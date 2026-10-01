param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [string]$Slug = 'palindrome-number'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$out = 'D:\workspace\OJ-Agent\tools\restart-8080-result.txt'
Set-Content -Path $out -Value "restart 8080 $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
function Test-Port([int]$p) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $iar = $client.BeginConnect('127.0.0.1', $p, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(800)) { $client.EndConnect($iar); return $true } else { return $false }
    } catch { return $false } finally { $client.Close() }
}

Say "=== 1) stop the old backend on 8080 ==="
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    Say "killing pid=$($listener.OwningProcess)"
    Stop-Process -Id $listener.OwningProcess -Force
    Start-Sleep -Seconds 4
} else {
    Say "nothing listening on 8080"
}
Say ("port 8080 listening = {0}" -f (Test-Port 8080))

Say "=== 2) package (jar is free now) ==="
Push-Location $root
& 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp -DskipTests -pl codeagent-oj-server package 2>&1 | Select-Object -Last 5 | ForEach-Object { Say $_ }
$packExit = $LASTEXITCODE
Pop-Location
Say "package_exit=$packExit"
if ($packExit -ne 0) { Say "package failed, aborting (old backend already stopped)"; exit 5 }

$jar = (Get-ChildItem (Join-Path $appDir 'target\codeagent-oj-server-*.jar') |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
Say "jar=$jar"

Say "=== 3) start new backend on 8080 ==="
$env:DEEPSEEK_API_KEY = $ApiKey
$env:SERVER_PORT = '8080'
$log = 'D:\workspace\OJ-Agent\backend-server.out.log'
$errLog = 'D:\workspace\OJ-Agent\backend-server.err.log'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$proc = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $appDir `
    -RedirectStandardOutput $log -RedirectStandardError $errLog -PassThru -NoNewWindow
Say "pid=$($proc.Id)"

$started = $false
for ($i = 0; $i -lt 75; $i++) {
    Start-Sleep -Seconds 2
    $text = ''
    if (Test-Path $log) { $text = Get-Content $log -Raw -ErrorAction SilentlyContinue }
    if ($text -match 'Started CodeAgentOjServerApplication') { $started = $true; break }
    if ($text -match 'APPLICATION FAILED TO START') { break }
    if ($proc.HasExited) { break }
}
Say "started=$started"
if (Test-Path $log) {
    Get-Content $log | Where-Object { $_ -match 'Migrating schema|Successfully applied|Started CodeAgent|Tomcat started|ERROR' } |
        Select-Object -First 8 | ForEach-Object { Say $_ }
}

if ($started) {
    Say "=== 4) smoke the new endpoints on 8080 ==="
    $base = 'http://127.0.0.1:8080'
    try {
        $user = 'live' + (Get-Random -Minimum 100000 -Maximum 999999)
        $regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'Live' } | ConvertTo-Json
        $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
        $headers = @{ Authorization = "Bearer $($reg.data.accessToken)" }
        $prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
        $version = $prob.data.problemVersionId
        $code = 'public class Main { public static void main(String[] args) { System.out.println(true); } }'
        $body = @{ problemVersion = $version; language = 'JAVA_21'; sourceCode = $code } | ConvertTo-Json
        $created = Invoke-RestMethod -Uri "$base/api/submissions" -Method Post -ContentType 'application/json' -Headers $headers -Body $body
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
        Say ("live_check submission={0} status={1} diagnosis_available={2} after_seconds={3} safety={4}" -f $id, $status, $view.data.available, [int]((Get-Date) - $t0).TotalSeconds, $view.data.safetyStatus)
        Say "diagnosis_head:"
        Say ($view.data.content.Substring(0, [Math]::Min(120, $view.data.content.Length)))
    } catch {
        Say "live check failed: $($_.Exception.Message)"
        if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
    }
} else {
    Say "---- startup failed, last 25 lines ----"
    if (Test-Path $log) { Get-Content $log -Tail 25 | ForEach-Object { Say $_ } }
    if (Test-Path $errLog) { Get-Content $errLog -Tail 10 | ForEach-Object { Say $_ } }
}
Say ("port 8080 listening at end = {0}" -f (Test-Port 8080))
Say "=== DONE ==="
