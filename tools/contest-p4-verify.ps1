param([Parameter(Mandatory = $true)][string]$ApiKey)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$out = 'D:\workspace\OJ-Agent\tools\contest-p4-verify.txt'
Set-Content -Path $out -Value "contest P4 verify $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$mysqlJar = (Get-ChildItem 'C:\Users\Administrator\.m2\repository\com\mysql\mysql-connector-j' -Recurse -Filter '*.jar' | Sort-Object FullName -Descending | Select-Object -First 1).FullName
$helper = 'D:\workspace\OJ-Agent\tools\JudgeAdmin.java'
$base = 'http://127.0.0.1:8080'

Say "=== 0) restart 8080 (sweep 5s) ==="
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) { Stop-Process -Id $listener.OwningProcess -Force; Start-Sleep -Seconds 4 }
Push-Location $root
& 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp -DskipTests -pl codeagent-oj-server package 2>&1 | Select-Object -Last 2 | ForEach-Object { Say $_ }
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
    if ($text -match 'APPLICATION FAILED TO START') { break }
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
function MakeContest($adminHeaders, $slug, $startOffsetMin, $endOffsetMin, $freezeMinutes) {
    $start = (Get-Date).AddMinutes($startOffsetMin).ToString('yyyy-MM-ddTHH:mm:sszzz')
    $end = (Get-Date).AddMinutes($endOffsetMin).ToString('yyyy-MM-ddTHH:mm:sszzz')
    $created = Call 'Post' "$base/api/admin/contests" $adminHeaders (@{ slug = $slug; title = "P4 $slug"; startAt = $start; endAt = $end; freezeMinutes = $freezeMinutes; penaltyMinutes = 20 } | ConvertTo-Json)
    Call 'Post' "$base/api/admin/contests/$($created.data.contest.id)/problems" $adminHeaders (@{ problemSlug = 'palindrome-number'; score = 100 } | ConvertTo-Json) | Out-Null
    Call 'Post' "$base/api/admin/contests/$($created.data.contest.id)/publish" $adminHeaders $null | Out-Null
    Start-Sleep -Seconds 7
    return $created.data.contest.id
}
function Submit($headers, $slug, $code) {
    $problem = Call 'Get' "$base/api/workspace/problems/$slug" $headers
    $submit = Call 'Post' "$base/api/submissions" $headers (@{ problemVersion = $problem.data.problemVersionId; language = 'JAVA_21'; sourceCode = $code } | ConvertTo-Json)
    $id = $submit.data.id
    for ($i = 0; $i -lt 40; $i++) {
        $detail = Call 'Get' "$base/api/submissions/$id" $headers
        $status = $detail.data.submission.status
        if ($status -in @('AC', 'WA', 'CE', 'RE', 'TLE', 'MLE')) { return $status }
        Start-Sleep -Seconds 1
    }
    return $status
}

$good = 'public class Main { public static void main(String[] args) throws Exception { java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(System.in)); String s=br.readLine().trim(); int n=s.length(); for(int i=0;i<n/2;i++){ if(s.charAt(i)!=s.charAt(n-1-i)){ System.out.println(false); return; } } System.out.println(true); } }'
$bad = 'public class Main { public static void main(String[] args) { System.out.println(false); } }'

$suffix = Get-Random -Minimum 1000 -Maximum 9999
$admin = NewUser 'p4a'
& $java -cp $mysqlJar $helper admin $admin.name | ForEach-Object { Say "  $_" }
$adminLogin = Call 'Post' "$base/api/auth/login" $null (@{ identifier = $admin.name; password = 'Smoke#123456' } | ConvertTo-Json)
$adminHeaders = @{ Authorization = "Bearer $($adminLogin.data.accessToken)" }
$u1 = NewUser 'p4u1'
$u2 = NewUser 'p4u2'
$u3 = NewUser 'p4u3'

Say "=== 1) 罚时：u1 先错两次再 AC，u2 一次 AC → u2 应排第一，u1 罚时多 40 分钟 ==="
$slug1 = "p4-penalty-$suffix"
$contest1 = MakeContest $adminHeaders $slug1 (-1) 60 0
$r1 = Call 'Post' "$base/api/contests/$slug1/register" $u1.headers
$r2 = Call 'Post' "$base/api/contests/$slug1/register" $u2.headers
$again = Call 'Post' "$base/api/contests/$slug1/register" $u1.headers
Say ("  register: u1={0} u2={1} u1again_participants={2}（重复报名应幂等）" -f $r1.data.registered, $r2.data.registered, $again.data.participantCount)
Say ("  u1 judge: {0} {1} {2}" -f (Submit $u1.headers 'palindrome-number' $bad), (Submit $u1.headers 'palindrome-number' $bad), (Submit $u1.headers 'palindrome-number' $good))
Say ("  u2 judge: {0}" -f (Submit $u2.headers 'palindrome-number' $good))
Say ("  u3 (未报名) judge: {0}（应写入 contest_id=NULL，不进榜单）" -f (Submit $u3.headers 'palindrome-number' $good))
$standings = Call 'Get' "$base/api/contests/$slug1/standings" $null
Say ("  榜单条数={0}" -f @($standings.data).Count)
if (@($standings.data).Count -ge 1) {
    foreach ($row in @($standings.data)) {
        $cell = $row.cells[0]
        Say ("    #{0} {1} solved={2} penaltySeconds={3} cell(A)={4} wrong={5} acceptedAt={6}" -f $row.rank, $row.username, $row.solved, $row.penaltySeconds, $cell.state, $cell.wrongAttempts, $cell.acceptedAtSeconds)
    }
    Expect '榜首是 u2' $standings.data[0].username $u2.name
    Expect 'u2 错次数=0' $standings.data[0].cells[0].wrongAttempts 0
    if (@($standings.data).Count -ge 2) {
        Expect 'u1 错次数=2' $standings.data[1].cells[0].wrongAttempts 2
        $gap = [int]$standings.data[1].penaltySeconds - [int]$standings.data[0].penaltySeconds
        Say ("  u1 与 u2 罚时差 = {0} 秒（应约等于 1200 + 提交时间差）" -f $gap)
        Expect '罚时差 >= 1200 秒' ([bool]($gap -ge 1200)) $true
    }
    Expect '未报名的 u3 不在榜单' ([bool](@($standings.data | Where-Object { $_.username -eq $u3.name }).Count -eq 0)) $true
}

Say "=== 2) 冻结：end=+2min 且 freeze=5min → 冻结已开始，选手视角应为空、管理员视角应有人 ==="
$slug2 = "p4-freeze-$suffix"
$contest2 = MakeContest $adminHeaders $slug2 (-20) 2 5
Call 'Post' "$base/api/contests/$slug2/register" $u1.headers | Out-Null
Say ("  u1 judge: {0}" -f (Submit $u1.headers 'palindrome-number' $good))
$player = Call 'Get' "$base/api/contests/$slug2/standings" $null
$adminView = Call 'Get' "$base/api/admin/contests/$contest2/standings" $adminHeaders
Expect '选手视角条数' @($player.data).Count 0
Say ("  管理员视角条数={0}（应 >=1，冻结只影响公开榜单）" -f @($adminView.data).Count)
$adminCode = & curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $($u1.token)" "$base/api/admin/contests/$contest2/standings"
Expect '普通用户访问管理员榜单' $adminCode 403
Say "=== DONE ==="
