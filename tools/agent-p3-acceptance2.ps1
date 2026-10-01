param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [int]$Port = 8081,
    [string]$Slug = 'palindrome-number'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$log = 'D:\workspace\OJ-Agent\tools\agent-p3b-server.log'
$out = 'D:\workspace\OJ-Agent\tools\agent-p3b-result.txt'
Set-Content -Path $out -Value "P3 acceptance v2 $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
function Test-Port([int]$p) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $iar = $client.BeginConnect('127.0.0.1', $p, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(800)) { $client.EndConnect($iar); return $true } else { return $false }
    } catch { return $false } finally { $client.Close() }
}

Say "=== 0) stop 8080 so only this instance diagnoses ==="
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) { Stop-Process -Id $listener.OwningProcess -Force; Say "killed pid=$($listener.OwningProcess)"; Start-Sleep -Seconds 4 }

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
if (-not $started) {
    if (Test-Path $log) { Get-Content $log -Tail 25 | ForEach-Object { Say $_ } }
    & taskkill /F /T /PID $proc.Id 2>&1 | Out-Null
    exit 6
}

$base = "http://127.0.0.1:$Port"
try {
    $user = 'p3b' + (Get-Random -Minimum 100000 -Maximum 999999)
    $regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'P3b' } | ConvertTo-Json
    $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
    $token = $reg.data.accessToken
    $headers = @{ Authorization = "Bearer $token" }
    $prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
    $version = $prob.data.problemVersionId
    Say "user=$user problemVersionId=$version"

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
    Say ("[1] diagnosis_available={0} after_seconds={1} safety={2} output_review={3}" -f $view.data.available, [int]((Get-Date) - $t0).TotalSeconds, $view.data.safetyStatus, $view.data.outputReview)
    Say "findings_json:"
    Say $view.data.findingsJson

    Say "[2] ask via curl (real trace)"
    $askFile = 'D:\workspace\OJ-Agent\tools\agent-p3b-ask.json'
    $askBody = @{ problemVersion = $version; message = 'Why did I fail last time? Check my submission records first.' } | ConvertTo-Json
    Set-Content -Path $askFile -Value $askBody -Encoding ascii
    $askRaw = & curl.exe -s -X POST "$base/api/agent/ask" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" --data-binary "@$askFile"
    $ask = $askRaw | ConvertFrom-Json
    Say ("ask_trace={0}" -f ($ask.data.trace -join ' -> '))
    Say ("ask_output_review_present={0}" -f ($ask.data.content.Length -gt 0))

    Say "[3] stream first-token timing"
    $streamFile = 'D:\workspace\OJ-Agent\tools\agent-p3b-stream.json'
    $streamBody = @{ problemVersion = $version; message = 'Give one short tip about edge cases.' } | ConvertTo-Json
    Set-Content -Path $streamFile -Value $streamBody -Encoding ascii
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $first = -1
    $messages = 0
    $doneSeen = $false
    & curl.exe -s -N -X POST "$base/api/agent/stream" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" --data-binary "@$streamFile" 2>&1 |
        ForEach-Object {
            if ($_ -match '^event: message') { $messages++; if ($first -lt 0) { $first = $watch.ElapsedMilliseconds } }
            if ($_ -match '^event: done') { $doneSeen = $true }
        }
    Say ("stream_first_token_ms={0} message_events={1} done_seen={2} total_ms={3}" -f $first, $messages, $doneSeen, $watch.ElapsedMilliseconds)
} catch {
    Say "API failed: $($_.Exception.Message)"
    if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
} finally {
    & taskkill /F /T /PID $proc.Id 2>&1 | Out-Null
    Start-Sleep -Seconds 3
    Say ("temp port {0} listening = {1}" -f $Port, (Test-Port $Port))
}

Say "=== 4) bring 8080 back on the P3 build ==="
Push-Location $root
& 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp -DskipTests -pl codeagent-oj-server package 2>&1 | Select-Object -Last 3 | ForEach-Object { Say $_ }
Pop-Location
$jar = (Get-ChildItem (Join-Path $appDir 'target\codeagent-oj-server-*.jar') | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
$env:SERVER_PORT = '8080'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$prod = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $appDir `
    -RedirectStandardOutput 'D:\workspace\OJ-Agent\backend-server.out.log' -RedirectStandardError 'D:\workspace\OJ-Agent\backend-server.err.log' -PassThru -NoNewWindow
Say "8080 pid=$($prod.Id)"
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    if (Test-Port 8080) { break }
}
Say ("port 8080 listening at end = {0}" -f (Test-Port 8080))
Say "=== DONE ==="
