param([Parameter(Mandatory = $true)][string]$ApiKey)

# P5 验收：定榜快照 + 竞赛内批量重判。
# 关键断言：① 定榜把名次/已解/罚时写进 contest_participants，选手端此后看到的是快照；
#          ② 竞赛内重判能重判该场归属的提交（rejudge_count 递增），且定榜后的名次数字**不再变化**。

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$out = 'D:\workspace\OJ-Agent\tools\contest-p5-verify.txt'
Set-Content -Path $out -Value "contest P5 verify $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$mysqlJar = (Get-ChildItem 'C:\Users\Administrator\.m2\repository\com\mysql\mysql-connector-j' -Recurse -Filter '*.jar' | Sort-Object FullName -Descending | Select-Object -First 1).FullName
$helper = 'D:\workspace\OJ-Agent\tools\JudgeAdmin.java'
$base = 'http://127.0.0.1:8080'

$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) { Stop-Process -Id $listener.OwningProcess -Force; Start-Sleep -Seconds 4 }
Push-Location $root
& 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp -DskipTests -pl codeagent-oj-server package 2>&1 | Select-Object -Last 1 | ForEach-Object { Say $_ }
Pop-Location
$jar = (Get-ChildItem (Join-Path $appDir 'target\codeagent-oj-server-*.jar') | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
$env:DEEPSEEK_API_KEY = $ApiKey
$env:SERVER_PORT = '8080'
$proc = Start-Process -FilePath $java -ArgumentList @('-Dapp.contest.sweep-ms=5000', '-jar', $jar) -WorkingDirectory $appDir `
    -RedirectStandardOutput 'D:\workspace\OJ-Agent\backend-server.out.log' -RedirectStandardError 'D:\workspace\OJ-Agent\backend-server.err.log' -PassThru -NoNewWindow
$started = $false
for ($i = 0; $i -lt 75; $i++) {
    Start-Sleep -Seconds 2
    $text = ''
    if (Test-Path 'D:\workspace\OJ-Agent\backend-server.out.log') { $text = Get-Content 'D:\workspace\OJ-Agent\backend-server.out.log' -Raw -ErrorAction SilentlyContinue }
    if ($text -match 'Started CodeAgentOjServerApplication') { $started = $true; break }
}
Say "started=$started pid=$($proc.Id)"
if (-not $started) { exit 6 }

function Call($method, $url, $headers, $body) {
    try {
        if ($body) { return Invoke-RestMethod -Uri $url -Method $method -ContentType 'application/json; charset=utf-8' -Headers $headers -Body $body }
        return Invoke-RestMethod -Uri $url -Method $method -Headers $headers
    } catch {
        $detail = ''
        if ($_.ErrorDetails) { $detail = $_.ErrorDetails.Message }
        $line = "  !! {0} {1} failed: {2} {3}" -f $method, $url, $_.Exception.Message, $detail
        Write-Host $line; Add-Content -Path $out -Value $line -Encoding UTF8
        return $null
    }
}
function Expect($label, $actual, $expected) {
    $ok = [string]$actual -eq [string]$expected
    Say ("  {0}: {1} (expect {2}){3}" -f $label, $actual, $expected, $(if ($ok) { " PASS" } else { " FAIL" }))
}
function NewUser($prefix) {
    $name = $prefix + (Get-Random -Minimum 100000 -Maximum 999999)
    Call 'Post' "$base/api/auth/register" $null (@{ username = $name; email = "$name@example.com"; password = 'Smoke#123456'; displayName = $name } | ConvertTo-Json) | Out-Null
    $login = Call 'Post' "$base/api/auth/login" $null (@{ identifier = $name; password = 'Smoke#123456' } | ConvertTo-Json)
    return @{ name = $name; token = $login.data.accessToken; headers = @{ Authorization = "Bearer $($login.data.accessToken)" } }
}
function Submit($headers, $code) {
    $problem = Call 'Get' "$base/api/workspace/problems/palindrome-number" $headers
    $submit = Call 'Post' "$base/api/submissions" $headers (@{ problemVersion = $problem.data.problemVersionId; language = 'JAVA_21'; sourceCode = $code } | ConvertTo-Json)
    $id = $submit.data.id
    for ($i = 0; $i -lt 40; $i++) {
        $detail = Call 'Get' "$base/api/submissions/$id" $headers
        if ($detail.data.submission.status -in @('AC', 'WA', 'CE', 'RE', 'TLE', 'MLE')) { return @{ id = $id; status = $detail.data.submission.status; rejudgeCount = $detail.data.submission.rejudgeCount } }
        Start-Sleep -Seconds 1
    }
    return @{ id = $id; status = 'TIMEOUT'; rejudgeCount = 0 }
}

$good = 'public class Main { public static void main(String[] args) throws Exception { java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(System.in)); String s=br.readLine().trim(); int n=s.length(); for(int i=0;i<n/2;i++){ if(s.charAt(i)!=s.charAt(n-1-i)){ System.out.println(false); return; } } System.out.println(true); } }'
$bad = 'public class Main { public static void main(String[] args) { System.out.println(false); } }'

$suffix = Get-Random -Minimum 1000 -Maximum 9999
$admin = NewUser 'p5a'
& $java -cp $mysqlJar $helper admin $admin.name | ForEach-Object { Say "  $_" }
$adminLogin = Call 'Post' "$base/api/auth/login" $null (@{ identifier = $admin.name; password = 'Smoke#123456' } | ConvertTo-Json)
$adminHeaders = @{ Authorization = "Bearer $($adminLogin.data.accessToken)" }
$u1 = NewUser 'p5u1'
$u2 = NewUser 'p5u2'

$slug = "p5-final-$suffix"
$start = (Get-Date).AddMinutes(-8).ToString('yyyy-MM-ddTHH:mm:sszzz')
$end = (Get-Date).AddMinutes(1).ToString('yyyy-MM-ddTHH:mm:sszzz')
Say "=== 建一场 9 分钟的比赛（end=+1min，便于等它自动结束）==="
$created = Call 'Post' "$base/api/admin/contests" $adminHeaders (@{ slug = $slug; title = "P5 $slug"; startAt = $start; endAt = $end; freezeMinutes = 0; penaltyMinutes = 20 } | ConvertTo-Json)
$contestId = $created.data.contest.id
Call 'Post' "$base/api/admin/contests/$contestId/problems" $adminHeaders (@{ problemSlug = 'palindrome-number'; score = 100 } | ConvertTo-Json) | Out-Null
Call 'Post' "$base/api/admin/contests/$contestId/publish" $adminHeaders $null | Out-Null
Start-Sleep -Seconds 8
Say ("  状态={0}" -f (Call 'Get' "$base/api/contests/$slug" $null).data.contest.status)

Call 'Post' "$base/api/contests/$slug/register" $u1.headers | Out-Null
Call 'Post' "$base/api/contests/$slug/register" $u2.headers | Out-Null
$s1 = Submit $u1.headers $bad
$s2 = Submit $u1.headers $good
$s3 = Submit $u2.headers $good
Say ("  u1: {0} → {1}（提交 #{2}）｜u2: {3}（提交 #{4}）" -f $s1.status, $s2.status, $s2.id, $s3.status, $s3.id)

$live = Call 'Get' "$base/api/contests/$slug/standings" $null
Say ("  实时榜单：{0} 行" -f @($live.data).Count)
foreach ($row in @($live.data)) { Say ("    #{0} {1} solved={2} penalty={3}s" -f $row.rank, $row.username, $row.solved, $row.penaltySeconds) }

Say "=== 等比赛自动结束（定时器 5s 扫描）==="
$status = ''
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 5
    $status = (Call 'Get' "$base/api/contests/$slug" $null).data.contest.status
    if ($status -eq 'ENDED') { break }
}
Say ("  状态={0}（期望 ENDED）" -f $status)

Say "=== 定榜（写快照）==="
$finalized = Call 'Post' "$base/api/admin/contests/$contestId/finalize" $adminHeaders $null
Say ("  定榜后状态={0} finalizedAt={1}" -f $finalized.data.contest.status, $finalized.data.contest.finalizedAt)
$snapshot = Call 'Get' "$base/api/contests/$slug/standings" $null
foreach ($row in @($snapshot.data)) { Say ("    快照 #{0} {1} solved={2} penalty={3}s" -f $row.rank, $row.username, $row.solved, $row.penaltySeconds) }
Expect '快照行数' @($snapshot.data).Count @($live.data).Count
$before = @($snapshot.data | ForEach-Object { "$($_.rank):$($_.userId):$($_.solved):$($_.penaltySeconds)" }) -join ','

Say "=== 竞赛内批量重判 ==="
$rejudge = Call 'Post' "$base/api/admin/contests/$contestId/rejudge" $adminHeaders $null
Say ("  重判条数={0}" -f $rejudge.data.rejudged)
Start-Sleep -Seconds 12
$after1 = (Call 'Get' "$base/api/submissions/$($s2.id)" $u1.headers).data.submission
$after3 = (Call 'Get' "$base/api/submissions/$($s3.id)" $u2.headers).data.submission
Say ("  重判后 提交#{0} verdict={1} rejudgeCount={2}｜提交#{3} verdict={4} rejudgeCount={5}" -f $s2.id, $after1.status, $after1.rejudgeCount, $s3.id, $after3.status, $after3.rejudgeCount)
Expect '重判确实重跑过（rejudgeCount 递增）' ([bool]([int]$after1.rejudgeCount -ge 1)) $true

$snapshot2 = Call 'Get' "$base/api/contests/$slug/standings" $null
$after = @($snapshot2.data | ForEach-Object { "$($_.rank):$($_.userId):$($_.solved):$($_.penaltySeconds)" }) -join ','
Say ("  重判前快照={0}" -f $before)
Say ("  重判后快照={0}" -f $after)
Expect '定榜后的名次数字保持不变' ([bool]($before -eq $after)) $true
Say "=== DONE ==="
