param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [string]$Slug = 'palindrome-number'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$out = 'D:\workspace\OJ-Agent\tools\agent-smoke-both.txt'
Set-Content -Path $out -Value "both-case run $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
function Test-Port([int]$p) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $iar = $client.BeginConnect('127.0.0.1', $p, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(800)) { $client.EndConnect($iar); return $true } else { return $false }
    } catch { return $false } finally { $client.Close() }
}

function Run-Case([string]$name, [int]$port, [bool]$useKey) {
    Say "########## CASE $name  port=$port  useKey=$useKey ##########"
    $log = "D:\workspace\OJ-Agent\tools\agent-case-$name.log"
    $errLog = "$log.err"
    if ($useKey) { $env:DEEPSEEK_API_KEY = $ApiKey } else { Remove-Item Env:DEEPSEEK_API_KEY -ErrorAction SilentlyContinue }
    $env:SERVER_PORT = "$port"
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
    Say "started=$started"
    if (Test-Path $log) {
        Get-Content $log | Where-Object { $_ -match 'Started CodeAgent|APPLICATION FAILED|OpenAI API key|Tomcat started' } |
            Select-Object -First 10 | ForEach-Object { Say $_ }
    }
    if ($started) {
        $base = "http://127.0.0.1:$port"
        try {
            $user = 'smoke' + (Get-Random -Minimum 100000 -Maximum 999999)
            $regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'Smoke' } | ConvertTo-Json
            $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
            $headers = @{ Authorization = "Bearer $($reg.data.accessToken)" }
            $prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
            $ask = @{ problemVersion = $prob.data.problemVersionId; message = 'explain the problem briefly'; sourceCode = 'public class Main {}' } | ConvertTo-Json
            $r = Invoke-RestMethod -Uri "$base/api/agent/ask" -Method Post -ContentType 'application/json' -Headers $headers -Body $ask
            $len = $r.data.content.Length
            Say ("answer_length={0} route={1} safety={2} canned_by_length={3}" -f $len, $r.data.route, $r.data.safety, ($len -lt 300))
            Say "answer_head:"
            Say ($r.data.content.Substring(0, [Math]::Min(160, $len)))
        } catch {
            Say "API failed: $($_.Exception.Message)"
            if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
        }
    } else {
        Say "---- last 25 log lines ----"
        if (Test-Path $log) { Get-Content $log -Tail 25 | ForEach-Object { Say $_ } }
    }
    Say "--- stop case $name ---"
    & taskkill /F /T /PID $proc.Id 2>&1 | ForEach-Object { Say $_ }
    Start-Sleep -Seconds 3
    Say ("port {0} listening = {1}" -f $port, (Test-Port $port))
}

Run-Case 'withkey' 8081 $true
Run-Case 'nokey' 8082 $false
Say ("port 8080 existing backend unchanged = {0}" -f (Test-Port 8080))
Say "=== DONE ==="
