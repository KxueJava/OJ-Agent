param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [string]$Slug = 'palindrome-number'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$out = 'D:\workspace\OJ-Agent\tools\agent-p2-result.txt'
Set-Content -Path $out -Value "P2 acceptance $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
function Test-Port([int]$p) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $iar = $client.BeginConnect('127.0.0.1', $p, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(800)) { $client.EndConnect($iar); return $true } else { return $false }
    } catch { return $false } finally { $client.Close() }
}
$alwaysTrue = 'public class Main { public static void main(String[] args) { System.out.println(true); } }'

function Run-Case([string]$name, [int]$port, [string]$key) {
    $kind = if ($key -eq $ApiKey) { 'real-key' } else { 'bogus-key' }
    Say "########## CASE $name ($kind) port=$port ##########"
    $log = "D:\workspace\OJ-Agent\tools\agent-p2-$name.log"
    $env:DEEPSEEK_API_KEY = $key
    $env:SERVER_PORT = "$port"
    $env:AGENT_AUTO_DIAGNOSE_LOOKBACK_MINUTES = '1'
    Remove-Item $log, "$log.err" -ErrorAction SilentlyContinue
    $proc = Start-Process -FilePath 'D:\apache-maven-3.9.14\bin\mvn.cmd' `
        -ArgumentList @('-B', '-ntp', '-pl', 'codeagent-oj-server', 'spring-boot:run') `
        -WorkingDirectory $root -RedirectStandardOutput $log -RedirectStandardError "$log.err" -PassThru -NoNewWindow

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
    if (-not $started) {
        if (Test-Path $log) { Get-Content $log -Tail 20 | ForEach-Object { Say $_ } }
        & taskkill /F /T /PID $proc.Id 2>&1 | Out-Null
        return
    }

    $base = "http://127.0.0.1:$port"
    try {
        $user = $name + (Get-Random -Minimum 100000 -Maximum 999999)
        $regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = $name } | ConvertTo-Json
        $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
        $headers = @{ Authorization = "Bearer $($reg.data.accessToken)" }
        $prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
        $version = $prob.data.problemVersionId

        $submitBody = @{ problemVersion = $version; language = 'JAVA_21'; sourceCode = $alwaysTrue } | ConvertTo-Json
        $created = Invoke-RestMethod -Uri "$base/api/submissions" -Method Post -ContentType 'application/json' -Headers $headers -Body $submitBody
        $submissionId = $created.data.id
        Say "user=$user submissionId=$submissionId"

        $status = $created.data.status
        for ($i = 0; $i -lt 40; $i++) {
            if ($status -in @('AC', 'WA', 'CE', 'RE', 'TLE', 'MLE')) { break }
            Start-Sleep -Seconds 3
            $detail = Invoke-RestMethod -Uri "$base/api/submissions/$submissionId" -Method Get -Headers $headers
            $status = $detail.data.submission.status
        }
        $t0 = Get-Date
        Say "final_status=$status"

        $view = $null
        $elapsed = -1
        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Seconds 2
            $view = Invoke-RestMethod -Uri "$base/api/agent/submissions/$submissionId/diagnosis" -Method Get -Headers $headers
            if ($view.data.available) { $elapsed = [int]((Get-Date) - $t0).TotalSeconds; break }
        }
        Say ("diagnosis_available={0} after_seconds={1} safety={2} verdict={3}" -f $view.data.available, $elapsed, $view.data.safetyStatus, $view.data.verdict)
        Say "diagnosis_content:"
        Say $view.data.content
    } catch {
        Say "API failed: $($_.Exception.Message)"
        if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
    } finally {
        & taskkill /F /T /PID $proc.Id 2>&1 | ForEach-Object { Say $_ }
        Start-Sleep -Seconds 3
        Say ("port {0} listening = {1}" -f $port, (Test-Port $port))
    }
}

Run-Case 'real' 8081 $ApiKey
Run-Case 'bogus' 8082 'sk-invalid-key-for-fallback-test'
Say ("port 8080 existing backend unchanged = {0}" -f (Test-Port 8080))
Say "=== DONE ==="
