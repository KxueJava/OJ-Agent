param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [string]$Slug = 'number-of-islands'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$out = 'D:\workspace\OJ-Agent\tools\stage2-verify.txt'
Set-Content -Path $out -Value "stage2 verify $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }

Say "=== 0) restart 8080 (applies V23) ==="
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) { Stop-Process -Id $listener.OwningProcess -Force; Say "killed pid=$($listener.OwningProcess)"; Start-Sleep -Seconds 4 }
Push-Location $root
& 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp -DskipTests -pl codeagent-oj-server package 2>&1 | Select-Object -Last 2 | ForEach-Object { Say $_ }
Pop-Location
$jar = (Get-ChildItem (Join-Path $appDir 'target\codeagent-oj-server-*.jar') | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
$env:DEEPSEEK_API_KEY = $ApiKey
$env:SERVER_PORT = '8080'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
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
if (Test-Path 'D:\workspace\OJ-Agent\backend-server.out.log') {
    Get-Content 'D:\workspace\OJ-Agent\backend-server.out.log' | Where-Object { $_ -match 'Migrating schema|Successfully applied|ERROR' } | Select-Object -Last 5 | ForEach-Object { Say $_ }
}
if (-not $started) { exit 6 }

Say "=== 1) DB check (via tools/Stage2Check.java) ==="
$jarMysql = (Get-ChildItem 'C:\Users\Administrator\.m2\repository\com\mysql\mysql-connector-j' -Recurse -Filter '*.jar' | Sort-Object FullName -Descending | Select-Object -First 1).FullName
& $java -cp $jarMysql 'D:\workspace\OJ-Agent\tools\Stage2Check.java' 2>&1 | ForEach-Object { Say $_ }

$base = 'http://127.0.0.1:8080'
try {
    Say "=== 2) 题面示例 vs 判题公开用例（API 视角） ==="
    $problem = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
    $example = $problem.data.examples[0]
    Say ("  example#1 input  = {0}" -f $example.input)
    Say ("  example#1 output = {0}" -f $example.output)

    Say "=== 3) 首页卡片依赖的接口 ==="
    $user = 's2' + (Get-Random -Minimum 100000 -Maximum 999999)
    $regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'S2' } | ConvertTo-Json
    $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
    $headers = @{ Authorization = "Bearer $($reg.data.accessToken)" }
    $list = Invoke-RestMethod -Uri "$base/api/submissions?size=1" -Method Get -Headers $headers
    Say ("  GET /api/submissions?size=1 -> total={0} items={1}（新用户应为 0，卡片此时回落规则推荐）" -f $list.data.total, $list.data.items.Count)

    Say "=== 4) 首页提示按钮走的真实 Agent 问答 ==="
    $version = $problem.data.problemVersionId
    $askBody = @{ problemVersion = $version; message = '给我第 1 级提示，循序渐进，不要直接给完整可提交代码' } | ConvertTo-Json
    $askFile = 'D:\workspace\OJ-Agent\tools\stage2-ask.json'
    [System.IO.File]::WriteAllText($askFile, $askBody, [System.Text.UTF8Encoding]::new($false))
    $reply = & curl.exe -s -X POST "$base/api/agent/ask" -H "Authorization: Bearer $($reg.data.accessToken)" -H 'Content-Type: application/json; charset=utf-8' --data-binary "@$askFile" | ConvertFrom-Json
    Say ("  route={0} safety={1} content长度={2} trace={3}" -f $reply.data.route, $reply.data.safety, $reply.data.content.Length, ($reply.data.trace -join ' | '))
    Say ("  content 预览: {0}" -f ($reply.data.content.Substring(0, [Math]::Min(90, $reply.data.content.Length)) -replace "`n", ' '))
} catch {
    Say "API failed: $($_.Exception.Message)"
    if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
}
Say "=== DONE ==="
