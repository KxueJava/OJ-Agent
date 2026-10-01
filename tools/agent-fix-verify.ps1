param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [int]$Port = 8081,
    [string]$Slug = 'number-of-islands'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$log = 'D:\workspace\OJ-Agent\tools\agent-fix-server.log'
$out = 'D:\workspace\OJ-Agent\tools\agent-fix-result.txt'
Set-Content -Path $out -Value "throttle fix verify $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
function Test-Port([int]$p) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $iar = $client.BeginConnect('127.0.0.1', $p, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(800)) { $client.EndConnect($iar); return $true } else { return $false }
    } catch { return $false } finally { $client.Close() }
}
function Wait-Terminal($base, $headers, $id) {
    $status = 'PENDING'
    for ($i = 0; $i -lt 40; $i++) {
        $detail = Invoke-RestMethod -Uri "$base/api/submissions/$id" -Method Get -Headers $headers
        $status = $detail.data.submission.status
        if ($status -in @('AC', 'WA', 'CE', 'RE', 'TLE', 'MLE')) { break }
        Start-Sleep -Seconds 2
    }
    return $status
}
function Wait-Diagnosis($base, $headers, $id, $seconds) {
    for ($i = 0; $i -lt $seconds; $i++) {
        Start-Sleep -Seconds 2
        $view = Invoke-RestMethod -Uri "$base/api/agent/submissions/$id/diagnosis" -Method Get -Headers $headers
        if ($view.data.available) { return $view }
    }
    return $view
}

Say "=== 0) stop 8080 so only this instance diagnoses ==="
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) { Stop-Process -Id $listener.OwningProcess -Force; Say "killed pid=$($listener.OwningProcess)"; Start-Sleep -Seconds 4 }

$env:DEEPSEEK_API_KEY = $ApiKey
$env:SERVER_PORT = "$Port"
$env:AGENT_AUTO_DIAGNOSE_LOOKBACK_MINUTES = '5'
$env:AGENT_AUTO_DIAGNOSE_COOLDOWN_SECONDS = '20'
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
    if (Test-Path $log) { Get-Content $log -Tail 30 | ForEach-Object { Say $_ } }
    & taskkill /F /T /PID $proc.Id 2>&1 | Out-Null
    exit 6
}

$base = "http://127.0.0.1:$Port"
try {
    $user = 'fix' + (Get-Random -Minimum 100000 -Maximum 999999)
    $regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'Fix' } | ConvertTo-Json
    $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
    $headers = @{ Authorization = "Bearer $($reg.data.accessToken)" }
    $prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
    $version = $prob.data.problemVersionId
    Say "user=$user slug=$Slug problemVersionId=$version"

    $bad = 'public class Main { public static void main(String[] args) { System.out.println(0); } }'
    $submitBody = @{ problemVersion = $version; language = 'JAVA_21'; sourceCode = $bad } | ConvertTo-Json

    Say "=== 1) first WA submission -> expect diagnosis ==="
    $first = Invoke-RestMethod -Uri "$base/api/submissions" -Method Post -ContentType 'application/json' -Headers $headers -Body $submitBody
    $firstId = $first.data.id
    $t0 = Get-Date
    $status = Wait-Terminal $base $headers $firstId
    $view1 = Wait-Diagnosis $base $headers $firstId 30
    Say ("submission={0} status={1} diagnosis_available={2} after_seconds={3}" -f $firstId, $status, $view1.data.available, [int]((Get-Date) - $t0).TotalSeconds)

    Say "=== 2) immediate resubmit within cooldown -> expect explicit SKIPPED reason (not silence) ==="
    $second = Invoke-RestMethod -Uri "$base/api/submissions" -Method Post -ContentType 'application/json' -Headers $headers -Body $submitBody
    $secondId = $second.data.id
    $status2 = Wait-Terminal $base $headers $secondId
    Start-Sleep -Seconds 4
    $view2 = Invoke-RestMethod -Uri "$base/api/agent/submissions/$secondId/diagnosis" -Method Get -Headers $headers
    Say ("submission={0} status={1} available={2} status_field={3} reason={4}" -f $secondId, $status2, $view2.data.available, $view2.data.status, $view2.data.reason)

    Say "=== 3) wait past cooldown (20s) -> second submission should now be diagnosed ==="
    $view2b = Wait-Diagnosis $base $headers $secondId 30
    Say ("submission={0} diagnosis_available_after_cooldown={1} safety={2}" -f $secondId, $view2b.data.available, $view2b.data.safetyStatus)
    Say ("trace={0}" -f $view2b.data.traceJson)
} catch {
    Say "API failed: $($_.Exception.Message)"
    if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
} finally {
    & taskkill /F /T /PID $proc.Id 2>&1 | Out-Null
    Start-Sleep -Seconds 3
    Say ("temp port {0} listening = {1}" -f $Port, (Test-Port $Port))
}

Say "=== 4) bring 8080 back on the fixed build ==="
Push-Location $root
& 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp -DskipTests -pl codeagent-oj-server package 2>&1 | Select-Object -Last 3 | ForEach-Object { Say $_ }
Pop-Location
$jar = (Get-ChildItem (Join-Path $appDir 'target\codeagent-oj-server-*.jar') | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
$env:SERVER_PORT = '8080'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$prod = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $appDir `
    -RedirectStandardOutput 'D:\workspace\OJ-Agent\backend-server.out.log' -RedirectStandardError 'D:\workspace\OJ-Agent\backend-server.err.log' -PassThru -NoNewWindow
Say "8080 pid=$($prod.Id)"
for ($i = 0; $i -lt 60; $i++) { Start-Sleep -Seconds 2; if (Test-Port 8080) { break } }
Say ("port 8080 listening at end = {0}" -f (Test-Port 8080))
Say "=== DONE ==="
