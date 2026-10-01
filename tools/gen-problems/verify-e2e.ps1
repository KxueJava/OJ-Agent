param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [int]$Sample = 6,
    [string]$Batch = 'batch-1.json,batch-3.json,batch-6.json'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$genDir = 'D:\workspace\OJ-Agent\tools\gen-problems'
$out = Join-Path $genDir 'e2e-result.txt'
Set-Content -Path $out -Value "e2e verify $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
function Test-Port([int]$p) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $iar = $client.BeginConnect('127.0.0.1', $p, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(800)) { $client.EndConnect($iar); return $true } else { return $false }
    } catch { return $false } finally { $client.Close() }
}

Say "=== 0) restart 8080 on the new build (applies V22) ==="
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) { Stop-Process -Id $listener.OwningProcess -Force; Say "killed pid=$($listener.OwningProcess)"; Start-Sleep -Seconds 4 }
Push-Location $root
& 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp -DskipTests -pl codeagent-oj-server package 2>&1 | Select-Object -Last 3 | ForEach-Object { Say $_ }
Pop-Location
$jar = (Get-ChildItem (Join-Path $appDir 'target\codeagent-oj-server-*.jar') | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
$env:DEEPSEEK_API_KEY = $ApiKey
$env:SERVER_PORT = '8080'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$proc = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $appDir `
    -RedirectStandardOutput 'D:\workspace\OJ-Agent\backend-server.out.log' -RedirectStandardError 'D:\workspace\OJ-Agent\backend-server.err.log' -PassThru -NoNewWindow
Say "8080 pid=$($proc.Id)"
$started = $false
for ($i = 0; $i -lt 75; $i++) {
    Start-Sleep -Seconds 2
    $text = ''
    if (Test-Path 'D:\workspace\OJ-Agent\backend-server.out.log') { $text = Get-Content 'D:\workspace\OJ-Agent\backend-server.out.log' -Raw -ErrorAction SilentlyContinue }
    if ($text -match 'Started CodeAgentOjServerApplication') { $started = $true; break }
    if ($text -match 'APPLICATION FAILED TO START') { break }
}
Say "started=$started"
if (Test-Path 'D:\workspace\OJ-Agent\backend-server.out.log') {
    Get-Content 'D:\workspace\OJ-Agent\backend-server.out.log' | Where-Object { $_ -match 'Migrating schema|Successfully applied|ERROR' } | Select-Object -Last 6 | ForEach-Object { Say $_ }
}
if (-not $started) { exit 6 }

$base = 'http://127.0.0.1:8080'
try {
    $catalog = Invoke-RestMethod -Uri "$base/api/problems?page=0&size=1" -Method Get
    Say ("catalog_total={0}" -f $catalog.data.total)

    $user = 'e2e' + (Get-Random -Minimum 100000 -Maximum 999999)
    $regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'E2E' } | ConvertTo-Json
    $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
    $headers = @{ Authorization = "Bearer $($reg.data.accessToken)" }
    Say "user=$user"

    $picked = @()
    foreach ($file in $Batch.Split(',')) {
        $path = Join-Path $genDir $file.Trim()
        if (-not (Test-Path $path)) { continue }
        $json = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($problem in $json.problems) { $picked += $problem }
    }
    Say ("candidates={0}, sampling={1}" -f $picked.Count, $Sample)
    $step = [Math]::Max(1, [int]($picked.Count / $Sample))
    $chosen = @()
    for ($i = 0; $i -lt $picked.Count -and $chosen.Count -lt $Sample; $i += $step) { $chosen += $picked[$i] }

    $ac = 0
    foreach ($problem in $chosen) {
        try {
            $detail = Invoke-RestMethod -Uri "$base/api/workspace/problems/$($problem.slug)" -Method Get
            $version = $detail.data.problemVersionId
            $body = @{ problemVersion = $version; language = 'JAVA_21'; sourceCode = $problem.solution } | ConvertTo-Json
            $created = Invoke-RestMethod -Uri "$base/api/submissions" -Method Post -ContentType 'application/json' -Headers $headers -Body $body
            $id = $created.data.id
            $status = $created.data.status
            for ($i = 0; $i -lt 40; $i++) {
                if ($status -in @('AC', 'WA', 'CE', 'RE', 'TLE', 'MLE')) { break }
                Start-Sleep -Seconds 2
                $detail2 = Invoke-RestMethod -Uri "$base/api/submissions/$id" -Method Get -Headers $headers
                $status = $detail2.data.submission.status
            }
            if ($status -eq 'AC') { $ac++ }
            Say ("{0,-40} version={1} verdict={2}" -f $problem.slug, $version, $status)
        } catch {
            Say ("{0,-40} FAILED: {1}" -f $problem.slug, $_.Exception.Message)
        }
    }
    Say ("e2e_ac={0}/{1}" -f $ac, $chosen.Count)
} catch {
    Say "API failed: $($_.Exception.Message)"
    if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
}
Say ("port 8080 listening at end = {0}" -f (Test-Port 8080))
Say "=== DONE ==="
