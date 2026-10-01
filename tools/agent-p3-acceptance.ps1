param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [int]$Port = 8081,
    [string]$Slug = 'palindrome-number'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$log = "D:\workspace\OJ-Agent\tools\agent-p3-server.log"
$out = 'D:\workspace\OJ-Agent\tools\agent-p3-result.txt'
Set-Content -Path $out -Value "P3 acceptance $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
function Test-Port([int]$p) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $iar = $client.BeginConnect('127.0.0.1', $p, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(800)) { $client.EndConnect($iar); return $true } else { return $false }
    } catch { return $false } finally { $client.Close() }
}

if (Test-Port $Port) { Say "port $Port busy, abort"; exit 3 }
$env:DEEPSEEK_API_KEY = $ApiKey
$env:SERVER_PORT = "$Port"
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
if (Test-Path $log) {
    Get-Content $log | Where-Object { $_ -match 'Migrating schema|Successfully applied|Started CodeAgent' } | Select-Object -First 5 | ForEach-Object { Say $_ }
}
if (-not $started) {
    if (Test-Path $log) { Get-Content $log -Tail 25 | ForEach-Object { Say $_ } }
    & taskkill /F /T /PID $proc.Id 2>&1 | Out-Null
    exit 6
}

$base = "http://127.0.0.1:$Port"
try {
    $user = 'p3' + (Get-Random -Minimum 100000 -Maximum 999999)
    $regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'P3' } | ConvertTo-Json
    $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
    $token = $reg.data.accessToken
    $headers = @{ Authorization = "Bearer $token" }
    $prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
    $version = $prob.data.problemVersionId
    Say "user=$user problemVersionId=$version"

    # 1) 结构化 finding：故意不读输入直接输出 true
    $code = 'public class Main { public static void main(String[] args) { System.out.println(true); } }'
    $submit = @{ problemVersion = $version; language = 'JAVA_21'; sourceCode = $code } | ConvertTo-Json
    $created = Invoke-RestMethod -Uri "$base/api/submissions" -Method Post -ContentType 'application/json' -Headers $headers -Body $submit
    $id = $created.data.id
    $status = $created.data.status
    for ($i = 0; $i -lt 40; $i++) {
        if ($status -in @('AC', 'WA', 'CE', 'RE', 'TLE', 'MLE')) { break }
        Start-Sleep -Seconds 3
        $detail = Invoke-RestMethod -Uri "$base/api/submissions/$id" -Method Get -Headers $headers
        $status = $detail.data.submission.status
    }
    $t0 = Get-Date
    $view = $null
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        $view = Invoke-RestMethod -Uri "$base/api/agent/submissions/$id/diagnosis" -Method Get -Headers $headers
        if ($view.data.available) { break }
    }
    Say ("diagnosis_available={0} after_seconds={1} safety={2} output_review={3}" -f $view.data.available, [int]((Get-Date) - $t0).TotalSeconds, $view.data.safetyStatus, $view.data.outputReview)
    Say "findings_json:"
    Say $view.data.findingsJson

    # 2) 真实 trace：问一个必须调工具的问题
    $askBody = @{ problemVersion = $version; message = 'Why did I fail last time? Check my submission records first.' } | ConvertTo-Json
    $ask = Invoke-RestMethod -Uri "$base/api/agent/ask" -Method Post -ContentType 'application/json' -Headers $headers -Body $askBody
    Say ("ask_trace={0}" -f ($ask.data.trace -join ' -> '))

    # 3) 真流式：量首个 token 到达时间
    $bodyFile = 'D:\workspace\OJ-Agent\tools\agent-p3-stream-body.json'
    $streamBody = @{ problemVersion = $version; message = 'Give one short tip about edge cases.' } | ConvertTo-Json
    Set-Content -Path $bodyFile -Value $streamBody -Encoding ascii
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $first = -1
    $messages = 0
    $doneSeen = $false
    & curl.exe -s -N -X POST "$base/api/agent/stream" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" --data-binary "@$bodyFile" 2>&1 |
        ForEach-Object {
            if ($_ -match '^event: message') { $messages++; if ($first -lt 0) { $first = $watch.ElapsedMilliseconds } }
            if ($_ -match '^event: done') { $doneSeen = $true }
        }
    Say ("stream_first_token_ms={0} message_events={1} done_seen={2} total_ms={3}" -f $first, $messages, $doneSeen, $watch.ElapsedMilliseconds)
} catch {
    Say "API failed: $($_.Exception.Message)"
    if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
} finally {
    & taskkill /F /T /PID $proc.Id 2>&1 | ForEach-Object { Say $_ }
    Start-Sleep -Seconds 3
    Say ("port {0} listening = {1}" -f $Port, (Test-Port $Port))
}
Say "=== DONE ==="
