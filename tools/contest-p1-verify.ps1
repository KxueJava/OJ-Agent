param(
    [Parameter(Mandatory = $true)][string]$ApiKey
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$out = 'D:\workspace\OJ-Agent\tools\contest-p1-verify.txt'
Set-Content -Path $out -Value "contest P1 verify $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$mysqlJar = (Get-ChildItem 'C:\Users\Administrator\.m2\repository\com\mysql\mysql-connector-j' -Recurse -Filter '*.jar' | Sort-Object FullName -Descending | Select-Object -First 1).FullName
$helper = 'D:\workspace\OJ-Agent\tools\JudgeAdmin.java'
$slug = 'contest-smoke-' + (Get-Random -Minimum 1000 -Maximum 9999)

Say "=== 0) restart 8080 (applies V26) ==="
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) { Stop-Process -Id $listener.OwningProcess -Force; Say "killed pid=$($listener.OwningProcess)"; Start-Sleep -Seconds 4 }
Push-Location $root
& 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp -DskipTests -pl codeagent-oj-server package 2>&1 | Select-Object -Last 2 | ForEach-Object { Say $_ }
Pop-Location
$jar = (Get-ChildItem (Join-Path $appDir 'target\codeagent-oj-server-*.jar') | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
$env:DEEPSEEK_API_KEY = $ApiKey
$env:SERVER_PORT = '8080'
$proc = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $appDir `
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
Get-Content 'D:\workspace\OJ-Agent\backend-server.out.log' | Where-Object { $_ -match 'Migrating schema|Successfully applied|APPLICATION FAILED|ERROR' } | Select-Object -Last 4 | ForEach-Object { Say "  $_" }
if (-not $started) { exit 6 }

$base = 'http://127.0.0.1:8080'
function Register($name) {
    $user = $name + (Get-Random -Minimum 100000 -Maximum 999999)
    $body = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = $name } | ConvertTo-Json
    $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $body
    return @{ user = $user; token = $reg.data.accessToken }
}
function Login($user) {
    $body = @{ identifier = $user; password = 'Smoke#123456' } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body $body
    return $res.data.accessToken
}
function StatusOf($scriptBlock) {
    try { & $scriptBlock | Out-Null; return 200 } catch { return [int]$_.Exception.Response.StatusCode }
}

function Call($method, $url, $headers, $body) {
    # 任何失败都要打印 HTTP 状态与后端 detail，否则脚本会"安静地失败"、只打印空值
    try {
        if ($body) { return Invoke-RestMethod -Uri $url -Method $method -ContentType 'application/json; charset=utf-8' -Headers $headers -Body $body }
        return Invoke-RestMethod -Uri $url -Method $method -Headers $headers
    } catch {
        $detail = ''
        if ($_.ErrorDetails) { $detail = $_.ErrorDetails.Message }
        Say ("  !! {0} {1} 失败: {2} {3}" -f $method, $url, $_.Exception.Message, $detail)
        return $null
    }
}

Say "=== 1) 普通用户访问管理端竞赛接口（期望 403） ==="
$a = Register 'c1'
$code = & curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $($a.token)" "$base/api/admin/contests"
Say "  GET /api/admin/contests -> HTTP $code"

Say "=== 2) 提为 ADMIN 后重新登录，创建草稿 ==="
& $java -cp $mysqlJar $helper admin $a.user | ForEach-Object { Say "  $_" }
$adminToken = Login $a.user
$headers = @{ Authorization = "Bearer $adminToken" }
# 后端字段是 Instant，时间必须带时区偏移，否则 Jackson 反序列化 400
$start = (Get-Date).AddDays(2).ToString('yyyy-MM-ddTHH:mm:sszzz')
$end = (Get-Date).AddDays(2).AddHours(2).ToString('yyyy-MM-ddTHH:mm:sszzz')
$create = @{ slug = $slug; title = 'P1 冒烟竞赛'; descriptionMd = '用于 P1 验收'; startAt = $start; endAt = $end; freezeMinutes = 20; penaltyMinutes = 20 } | ConvertTo-Json
$created = Call 'Post' "$base/api/admin/contests" $headers $create
$id = $created.data.contest.id
Say ("  创建成功 id={0} status={1} editable={2}" -f $id, $created.data.contest.status, $created.data.editable)
Say ("  空题集校验：" + (($created.data.issues | ForEach-Object { $_.code }) -join ', '))

Say "=== 3) 加两道题（期望题号 A、B） ==="
foreach ($problemSlug in 'two-sum', 'palindrome-number') {
    $body = @{ problemSlug = $problemSlug; score = 100 } | ConvertTo-Json
    $res = Call 'Post' "$base/api/admin/contests/$id/problems" $headers $body
    if ($res) { Say ("  加题 {0} -> 现在 {1} 题" -f $problemSlug, $res.data.problems.Count) }
}
$detail = Call 'Get' "$base/api/admin/contests/$id" $headers
Say ("  题号：" + (($detail.data.problems | ForEach-Object { $_.label + '=' + $_.slug }) -join ', '))
Say ("  校验结论：" + (($detail.data.issues | ForEach-Object { $_.code + '(' + $_.message + ')' }) -join ' | '))

Say "=== 4) 重复加同一题（期望 409） ==="
$dupCode = & curl.exe -s -o NUL -w "%{http_code}" -X POST -H "Authorization: Bearer $adminToken" -H 'Content-Type: application/json' -d '{\"problemSlug\":\"two-sum\"}' "$base/api/admin/contests/$id/problems"
Say "  重复加题 -> HTTP $dupCode"

Say "=== 5) 冻结时长 ≥ 比赛时长（期望校验指出 freeze.tooLong） ==="
$bad = @{ freezeMinutes = 999 } | ConvertTo-Json
$updated = Invoke-RestMethod -Uri "$base/api/admin/contests/$id" -Method Put -ContentType 'application/json' -Headers $headers -Body $bad
Say ("  校验结论：" + (($updated.data.issues | ForEach-Object { $_.code + '(' + $_.message + ')' }) -join ' | '))

Say "=== 6) 删掉 A 题后题号应重排（B -> A） ==="
$firstProblemId = $detail.data.problems[0].problemId
$removed = Invoke-RestMethod -Uri "$base/api/admin/contests/$id/problems/$firstProblemId" -Method Delete -Headers $headers
Say ("  剩余题号：" + (($removed.data.problems | ForEach-Object { $_.label + '=' + $_.slug }) -join ', '))

Say "=== 7) 数据库侧核对（竞赛 / 题目 / 审计） ==="
& $java -cp $mysqlJar $helper contest | ForEach-Object { Say "  $_" }
& $java -cp $mysqlJar $helper audit | ForEach-Object { Say "  $_" }
Say "=== DONE ==="
