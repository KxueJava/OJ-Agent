param([Parameter(Mandatory = $true)][string]$ApiKey)

# P4-b：重做冻结验收。
# 上一版的问题：比赛窗口只有 24 分钟（start=-20, end=+2），提交可能落在窗口之外，
# 于是"选手视角 0 条"可能是窗口排除而非冻结所致 —— 断言碰巧通过。
# 这一版把窗口拉到 60 分钟（start=-30, end=+30），freeze=45 → 冻结起点 = end-45min = -15min（已冻结），
# 因此提交必然在窗口内：若管理员视角能看到、选手视角看不到，才真正证明"冻结只影响公开榜单"。

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$out = 'D:\workspace\OJ-Agent\tools\contest-p4b-verify.txt'
Set-Content -Path $out -Value "contest P4-b verify $(Get-Date -Format s)" -Encoding UTF8
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

$suffix = Get-Random -Minimum 1000 -Maximum 9999
$admin = 'p4b' + $suffix
Call 'Post' "$base/api/auth/register" $null (@{ username = $admin; email = "$admin@example.com"; password = 'Smoke#123456'; displayName = $admin } | ConvertTo-Json) | Out-Null
& $java -cp $mysqlJar $helper admin $admin | ForEach-Object { Say "  $_" }
$adminLogin = Call 'Post' "$base/api/auth/login" $null (@{ identifier = $admin; password = 'Smoke#123456' } | ConvertTo-Json)
$adminHeaders = @{ Authorization = "Bearer $($adminLogin.data.accessToken)" }
$user = 'p4bu' + $suffix
Call 'Post' "$base/api/auth/register" $null (@{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = $user } | ConvertTo-Json) | Out-Null
$userLogin = Call 'Post' "$base/api/auth/login" $null (@{ identifier = $user; password = 'Smoke#123456' } | ConvertTo-Json)
$userHeaders = @{ Authorization = "Bearer $($userLogin.data.accessToken)" }

$slug = "p4b-freeze-$suffix"
$start = (Get-Date).AddMinutes(-30).ToString('yyyy-MM-ddTHH:mm:sszzz')
$end = (Get-Date).AddMinutes(30).ToString('yyyy-MM-ddTHH:mm:sszzz')
Say "=== 建立 60 分钟窗口的比赛（start=-30min, end=+30min, freeze=45min → 冻结起点 = end-45min = -15min，即当前已冻结）==="
$created = Call 'Post' "$base/api/admin/contests" $adminHeaders (@{ slug = $slug; title = "P4b $slug"; startAt = $start; endAt = $end; freezeMinutes = 45; penaltyMinutes = 20 } | ConvertTo-Json)
$contestId = $created.data.contest.id
Call 'Post' "$base/api/admin/contests/$contestId/problems" $adminHeaders (@{ problemSlug = 'palindrome-number'; score = 100 } | ConvertTo-Json) | Out-Null
$published = Call 'Post' "$base/api/admin/contests/$contestId/publish" $adminHeaders $null
Say ("  published={0} status={1}" -f $published.data.published, $published.data.contest.status)
Start-Sleep -Seconds 8
Say ("  运行状态={0}（定时器应已推进）" -f (Call 'Get' "$base/api/contests/$slug" $null).data.contest.status)

Call 'Post' "$base/api/contests/$slug/register" $userHeaders | Out-Null
$good = 'public class Main { public static void main(String[] args) throws Exception { java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(System.in)); String s=br.readLine().trim(); int n=s.length(); for(int i=0;i<n/2;i++){ if(s.charAt(i)!=s.charAt(n-1-i)){ System.out.println(false); return; } } System.out.println(true); } }'
$problem = Call 'Get' "$base/api/workspace/problems/palindrome-number" $userHeaders
$submit = Call 'Post' "$base/api/submissions" $userHeaders (@{ problemVersion = $problem.data.problemVersionId; language = 'JAVA_21'; sourceCode = $good } | ConvertTo-Json)
$submissionId = $submit.data.id
$status = $submit.data.status
for ($i = 0; $i -lt 40; $i++) {
    if ($status -in @('AC', 'WA', 'CE', 'RE', 'TLE', 'MLE')) { break }
    Start-Sleep -Seconds 1
    $status = (Call 'Get' "$base/api/submissions/$submissionId" $userHeaders).data.submission.status
}
$submission = (Call 'Get' "$base/api/submissions/$submissionId" $userHeaders).data.submission
Say ("  提交 #{0} 判题={1} 提交时间={2}（比赛窗口 {3} → {4}）" -f $submissionId, $status, $submission.createdAt, $start, $end)

$player = Call 'Get' "$base/api/contests/$slug/standings" $null
$adminView = Call 'Get' "$base/api/admin/contests/$contestId/standings" $adminHeaders
Say ("  选手视角条数={0}" -f @($player.data).Count)
Say ("  管理员视角条数={0}" -f @($adminView.data).Count)
if (@($adminView.data).Count -gt 0) {
    $row = $adminView.data[0]
    Say ("    {0} solved={1} penalty={2}s cell(A)={3} wrong={4}" -f $row.username, $row.solved, $row.penaltySeconds, $row.cells[0].state, $row.cells[0].wrongAttempts)
}
Expect '选手视角应为 0（冻结中）' @($player.data).Count 0
Expect '管理员视角应 >= 1（冻结不影响管理端）' ([bool](@($adminView.data).Count -ge 1)) $true
Say "=== DONE ==="
