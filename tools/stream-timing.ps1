$ErrorActionPreference = 'Continue'
$out = 'D:\workspace\OJ-Agent\tools\stream-timing.txt'
Set-Content -Path $out -Value "stream timing $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }

$base = 'http://127.0.0.1:8080'
$user = 'st' + (Get-Random -Minimum 100000 -Maximum 999999)
$regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'ST' } | ConvertTo-Json
$reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
$token = $reg.data.accessToken
$prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/palindrome-number" -Method Get
$version = $prob.data.problemVersionId

$bodyFile = 'D:\workspace\OJ-Agent\tools\stream-timing-body.json'
$body = @{ problemVersion = $version; message = 'Explain the edge cases of this problem in two short sentences.' } | ConvertTo-Json
Set-Content -Path $bodyFile -Value $body -Encoding ascii

$watch = [System.Diagnostics.Stopwatch]::StartNew()
$first = -1
$tokens = 0
$done = $false
$chars = 0
& curl.exe -s -N --max-time 90 -X POST "$base/api/agent/stream" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" --data-binary "@$bodyFile" 2>&1 |
    ForEach-Object {
        $line = $_.ToString()
        if ($line -match '^event:\s*message') { $tokens++; if ($first -lt 0) { $first = $watch.ElapsedMilliseconds } }
        elseif ($line -match '^data:' -and $line -match '"content"') { $chars += $line.Length }
        elseif ($line -match '^event:\s*done') { $done = $true }
    }
Say ("first_token_ms={0}  message_events={1}  done={2}  total_ms={3}" -f $first, $tokens, $done, $watch.ElapsedMilliseconds)
Say ("P3_metric_first_token_under_2s={0}" -f ($first -ge 0 -and $first -lt 2000))
Say "=== DONE ==="
