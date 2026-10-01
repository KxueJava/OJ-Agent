param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [string]$Slug = 'palindrome-number'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$out = 'D:\workspace\OJ-Agent\tools\stage4-verify.txt'
Set-Content -Path $out -Value "stage4 verify $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$mysqlJar = (Get-ChildItem 'C:\Users\Administrator\.m2\repository\com\mysql\mysql-connector-j' -Recurse -Filter '*.jar' | Sort-Object FullName -Descending | Select-Object -First 1).FullName
$helper = 'D:\workspace\OJ-Agent\tools\JudgeAdmin.java'

Say "=== 0) 用低阈值启动后端（便于观察告警） ==="
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) { Stop-Process -Id $listener.OwningProcess -Force; Say "killed pid=$($listener.OwningProcess)"; Start-Sleep -Seconds 4 }

Push-Location $root
& 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp -DskipTests -pl codeagent-oj-server package 2>&1 | Select-Object -Last 2 | ForEach-Object { Say $_ }
Pop-Location
$jar = (Get-ChildItem (Join-Path $appDir 'target\codeagent-oj-server-*.jar') | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
$env:DEEPSEEK_API_KEY = $ApiKey
$env:SERVER_PORT = '8080'
$proc = Start-Process -FilePath $java -ArgumentList @('-Dapp.judge.outbox.age-threshold-seconds=0', '-Dapp.judge.outbox.monitor-ms=3000', '-jar', $jar) -WorkingDirectory $appDir `
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

Say "=== 0b) 迁移完成后再造合成死信（dead_lettered_at 由 V25 创建） ==="
& $java -cp $mysqlJar $helper dead | ForEach-Object { Say "  $_" }

$base = 'http://127.0.0.1:8080'
$user = 's4' + (Get-Random -Minimum 100000 -Maximum 999999)
$regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'S4' } | ConvertTo-Json
$reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
$token = $reg.data.accessToken
$headers = @{ Authorization = "Bearer $token" }
$problem = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
$body = @{ problemVersion = $problem.data.problemVersionId; language = 'JAVA_21'; sourceCode = 'public class Main { public static void main(String[] args) { System.out.println(true); } }' } | ConvertTo-Json
$file = 'D:\workspace\OJ-Agent\tools\stage4-submit.json'
[System.IO.File]::WriteAllText($file, $body, [System.Text.UTF8Encoding]::new($false))

Say "=== 1) 提交限流：连续 9 次提交（默认每分钟 6 次） ==="
for ($i = 1; $i -le 9; $i++) {
    $raw = & curl.exe -s -w "`nHTTP:%{http_code}" -X POST "$base/api/submissions" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' --data-binary "@$file"
    $code = ($raw | Select-String -Pattern 'HTTP:(\d+)').Matches.Groups[1].Value
    $detail = ''
    if ($code -ne '200') { $detail = [regex]::Match(($raw -join "`n"), '"detail":"([^"]*)"').Groups[1].Value }
    Say ("  #{0} -> HTTP {1} {2}" -f $i, $code, $detail)
}

Say "=== 2) 管理端队列可见性（先把测试用户提为 ADMIN） ==="
& $java -cp $mysqlJar $helper admin $user | ForEach-Object { Say "  $_" }
$login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body (@{ identifier = $user; password = 'Smoke#123456' } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
Say ("  重新登录拿带 ADMIN 角色的 token：role={0}" -f $login.data.user.role)
$queue = Invoke-RestMethod -Uri "$base/api/admin/judge/queue" -Method Get -Headers $headers
Say ("  pending={0} published={1} dead={2} oldestPendingSeconds={3} deadEvents={4}" -f $queue.data.pending, $queue.data.published, $queue.data.dead, $queue.data.oldestPendingSeconds, $queue.data.deadEvents.Count)
if ($queue.data.deadEvents.Count -gt 0) { Say ("  首条死信: id={0} attempts={1} error={2}" -f $queue.data.deadEvents[0].id, $queue.data.deadEvents[0].attempts, $queue.data.deadEvents[0].lastError) }

Say "=== 3) 重投死信 + 非管理员访问控制 ==="
$requeue = Invoke-RestMethod -Uri "$base/api/admin/judge/outbox/999000001/requeue" -Method Post -Headers $headers
Say ("  重投结果 requeued={0}" -f $requeue.data.requeued)
Start-Sleep -Seconds 3
$after = Invoke-RestMethod -Uri "$base/api/admin/judge/queue" -Method Get -Headers $headers
Say ("  重投后 dead={0} pending={1}" -f $after.data.dead, $after.data.pending)

$userB = 's4b' + (Get-Random -Minimum 100000 -Maximum 999999)
$regB = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body (@{ username = $userB; email = "$userB@example.com"; password = 'Smoke#123456'; displayName = 'S4B' } | ConvertTo-Json)
$codeB = & curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $($regB.data.accessToken)" "$base/api/admin/judge/queue"
Say ("  普通用户访问 /api/admin/judge/queue -> HTTP {0}  (期望 403)" -f $codeB)

Say "=== 4) 告警日志 ==="
Get-Content 'D:\workspace\OJ-Agent\backend-server.out.log' | Where-Object { $_ -match 'OUTBOX_BACKLOG|OUTBOX_DEAD_TOTAL' } | Select-Object -Last 3 | ForEach-Object { Say ("  " + $_.Trim()) }

Say "=== 5) 清理合成死信 ==="
& $java -cp $mysqlJar $helper cleanup | ForEach-Object { Say "  $_" }
Say "=== DONE ==="
