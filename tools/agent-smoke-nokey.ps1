param(
    [int]$Port = 8082,
    [string]$Slug = 'palindrome-number'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$log = 'D:\workspace\OJ-Agent\tools\agent-nokey-server.log'
$errLog = "$log.err"
$out = 'D:\workspace\OJ-Agent\tools\agent-nokey-result.txt'
Set-Content -Path $out -Value "no-key run $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
function Test-Port([int]$p) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $iar = $client.BeginConnect('127.0.0.1', $p, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(800)) { $client.EndConnect($iar); return $true } else { return $false }
    } catch { return $false } finally { $client.Close() }
}

if (Test-Port $Port) { Say "port $Port busy, abort"; exit 3 }

Say "=== start WITHOUT DEEPSEEK_API_KEY on port $Port ==="
$env:DEEPSEEK_API_KEY = ''
$env:SERVER_PORT = "$Port"
Remove-Item $log, $errLog -ErrorAction SilentlyContinue
$proc = Start-Process -FilePath 'D:\apache-maven-3.9.14\bin\mvn.cmd' `
    -ArgumentList @('-B', '-ntp', '-pl', 'codeagent-oj-server', 'spring-boot:run') `
    -WorkingDirectory $root -RedirectStandardOutput $log -RedirectStandardError $errLog -PassThru -NoNewWindow

$started = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    $text = ''
    if (Test-Path $log) { $text = Get-Content $log -Raw -ErrorAction SilentlyContinue }
    if ($text -match 'Started CodeAgentOjServerApplication') { $started = $true; break }
    if ($text -match 'APPLICATION FAILED TO START|BUILD FAILURE') { break }
    if ($proc.HasExited) { break }
}
Say "started_without_key=$started"

Say "=== startup lines ==="
if (Test-Path $log) {
    Get-Content $log | Where-Object { $_ -match 'Started CodeAgent|APPLICATION FAILED|ERROR|api-key|OpenAI|Tomcat started' } |
        Select-Object -First 20 | ForEach-Object { Say $_ }
}

if ($started) {
    $base = "http://127.0.0.1:$Port"
    try {
        $user = 'nokey' + (Get-Random -Minimum 100000 -Maximum 999999)
        $regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'NoKey' } | ConvertTo-Json
        $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
        $headers = @{ Authorization = "Bearer $($reg.data.accessToken)" }
        $prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
        $ask = @{ problemVersion = $prob.data.problemVersionId; message = 'explain the problem briefly' } | ConvertTo-Json
        $r = Invoke-RestMethod -Uri "$base/api/agent/ask" -Method Post -ContentType 'application/json' -Headers $headers -Body $ask
        Say ("http_ok=True answer_length={0} route={1} safety={2}" -f $r.data.content.Length, $r.data.route, $r.data.safety)
        Say "fallback_answer_raw:"
        Say $r.data.content
        Say ("looks_like_canned_fallback={0}" -f ($r.data.content.Length -lt 300))
    } catch {
        Say "API call failed: $($_.Exception.Message)"
        if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
    }
} else {
    Say "---- last 40 lines ----"
    if (Test-Path $log) { Get-Content $log -Tail 40 | ForEach-Object { Say $_ } }
}

Say "=== stop ==="
& taskkill /F /T /PID $proc.Id 2>&1 | ForEach-Object { Say $_ }
Start-Sleep -Seconds 3
Say ("port {0} still listening = {1}" -f $Port, (Test-Port $Port))
Say ("port 8080 (existing backend) still listening = {0}" -f (Test-Port 8080))
Say "=== DONE ==="
