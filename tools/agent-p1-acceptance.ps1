param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [int]$Port = 8081,
    [string]$Slug = 'palindrome-number'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$log = "D:\workspace\OJ-Agent\tools\agent-p1-server.log"
$errLog = "$log.err"
$out = 'D:\workspace\OJ-Agent\tools\agent-p1-result.txt'
Set-Content -Path $out -Value "P1 acceptance run $(Get-Date -Format s)" -Encoding UTF8
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
if (-not $started) {
    if (Test-Path $log) { Get-Content $log -Tail 30 | ForEach-Object { Say $_ } }
    & taskkill /F /T /PID $proc.Id 2>&1 | Out-Null
    exit 6
}

$base = "http://127.0.0.1:$Port"
try {
    $user = 'p1' + (Get-Random -Minimum 100000 -Maximum 999999)
    $regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'P1' } | ConvertTo-Json
    $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
    $headers = @{ Authorization = "Bearer $($reg.data.accessToken)" }
    Say "user=$user"

    $prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
    $version = $prob.data.problemVersionId
    Say "problemVersionId=$version"

    # 故意写一份"永远输出 true"的代码：公开用例 121 会过，隐藏用例 -121 会挂 => WA
    $code = 'public class Main { public static void main(String[] args) { System.out.println(true); } }'
    $submitBody = @{ problemVersion = $version; language = 'JAVA_21'; sourceCode = $code } | ConvertTo-Json
    $created = Invoke-RestMethod -Uri "$base/api/submissions" -Method Post -ContentType 'application/json' -Headers $headers -Body $submitBody
    $submissionId = $created.data.id
    Say "submissionId=$submissionId initial_status=$($created.data.status)"

    $status = $created.data.status
    for ($i = 0; $i -lt 40; $i++) {
        if ($status -in @('AC', 'WA', 'CE', 'RE', 'TLE', 'MLE')) { break }
        Start-Sleep -Seconds 3
        $detail = Invoke-RestMethod -Uri "$base/api/submissions/$submissionId" -Method Get -Headers $headers
        $status = $detail.data.submission.status
    }
    Say "final_status=$status verdict=$($detail.data.submission.verdictMessage)"

    Say "=== ask agent (should use tools, not ask the user to paste code) ==="
    $question = 'I failed this problem last time. Please look up my submission records first, then tell me whether the failure was on a public case or a hidden case.'
    $ask = @{ problemVersion = $version; message = $question } | ConvertTo-Json
    $reply = Invoke-RestMethod -Uri "$base/api/agent/ask" -Method Post -ContentType 'application/json' -Headers $headers -Body $ask
    $answer = $reply.data.content
    Say ("route={0} safety={1} answer_length={2}" -f $reply.data.route, $reply.data.safety, $answer.Length)
    Say "answer:"
    Say $answer
    Say ("mentions_submission_id={0}" -f ($answer -match "$submissionId"))
    Say ("mentions_verdict={0}" -f ($answer -match 'WA|隐藏|hidden'))
} catch {
    Say "API failed: $($_.Exception.Message)"
    if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
} finally {
    & taskkill /F /T /PID $proc.Id 2>&1 | ForEach-Object { Say $_ }
    Start-Sleep -Seconds 3
    Say ("port {0} listening = {1}" -f $Port, (Test-Port $Port))
    Say ("port 8080 existing backend unchanged = {0}" -f (Test-Port 8080))
}
Say "=== DONE ==="
