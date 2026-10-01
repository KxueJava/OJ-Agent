$ErrorActionPreference = 'Continue'
$out = 'D:\workspace\OJ-Agent\tools\stream-diag.txt'
Set-Content -Path $out -Value "stream diag $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }

$base = 'http://127.0.0.1:8080'
$user = 'sd' + (Get-Random -Minimum 100000 -Maximum 999999)
$regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'SD' } | ConvertTo-Json
$reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
$token = $reg.data.accessToken
$prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/palindrome-number" -Method Get
$version = $prob.data.problemVersionId
Say "user=$user version=$version token_len=$($token.Length)"

$bodyFile = 'D:\workspace\OJ-Agent\tools\stream-diag-body.json'
$body = @{ problemVersion = $version; message = 'Give one short tip about edge cases.' } | ConvertTo-Json
Set-Content -Path $bodyFile -Value $body -Encoding ascii
Say "body=$body"

Say "--- A) /api/agent/stream raw (headers + first lines) ---"
& curl.exe -s -i -N --max-time 60 -X POST "$base/api/agent/stream" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" --data-binary "@$bodyFile" 2>&1 |
    Select-Object -First 25 | ForEach-Object { Say $_ }

Say "--- B) /api/agent/ask for comparison ---"
$askFile = 'D:\workspace\OJ-Agent\tools\stream-diag-ask.json'
Set-Content -Path $askFile -Value $body -Encoding ascii
$raw = & curl.exe -s -i --max-time 60 -X POST "$base/api/agent/ask" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" --data-binary "@$askFile" 2>&1
($raw | Select-Object -First 8) | ForEach-Object { Say $_ }
Say "--- C) server log tail (stream errors) ---"
$log = 'D:\workspace\OJ-Agent\backend-server.out.log'
if (Test-Path $log) {
    Get-Content $log -Tail 25 | Where-Object { $_ -match 'stream|ERROR|WARN|Exception' } | Select-Object -First 15 | ForEach-Object { Say $_ }
}
Say "=== DONE ==="
