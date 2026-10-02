param(
    [Parameter(Mandatory = $true)][string]$ApiKey
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$out = 'D:\workspace\OJ-Agent\tools\contest-p2-verify.txt'
Set-Content -Path $out -Value "contest P2 verify $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$mysqlJar = (Get-ChildItem 'C:\Users\Administrator\.m2\repository\com\mysql\mysql-connector-j' -Recurse -Filter '*.jar' | Sort-Object FullName -Descending | Select-Object -First 1).FullName
$helper = 'D:\workspace\OJ-Agent\tools\JudgeAdmin.java'
$base = 'http://127.0.0.1:8080'

Say "=== 0) restart 8080（应用 V27，并把状态扫描缩到 5 秒便于验证定时器） ==="
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) { Stop-Process -Id $listener.OwningProcess -Force; Say "killed pid=$($listener.OwningProcess)"; Start-Sleep -Seconds 4 }
Push-Location $root
& 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp -DskipTests -pl codeagent-oj-server package 2>&1 | Select-Object -Last 2 | ForEach-Object { Say $_ }
Pop-Location
$jar = (Get-ChildItem (Join-Path $appDir 'target\codeagent-oj-server-*.jar') | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
$env:DEEPSEEK_API_KEY = $ApiKey
$env:SERVER_PORT = '8080'
$proc = Start-Process -FilePath $java -ArgumentList @('-Dapp.contest.sweep-ms=5000', '-jar', $jar) -WorkingDirectory $appDir `
    -RedirectStandardOutput 'D:\workspace\OJ-Agent\backend-server.out.log' -RedirectStandardError 'D:\workspace\OJ-Agent\backend-server.err.log' -PassThru -NoNewWindow
$started = $false
for ($i = 0; $i -lt 75; $i++) {
    Start-Sleep -Seconds 2
    $text = ''
    if (Test-Path 'D:\workspace\OJ-Agent\backend-server.out.log') { $text = Get-Content 'D:\workspace\OJ-Agent\backend-server.out.log' -Raw -ErrorAction SilentlyContinue }
    if ($text -match 'Started CodeAgentOjServerApplication') { $started = $true; break }
    if ($text -match 'APPLICATION FAILED TO START') { break }
}
Say "started=$started pid=$($proc.Id)"
Get-Content 'D:\workspace\OJ-Agent\backend-server.out.log' | Where-Object { $_ -match 'Migrating schema|Successfully applied|APPLICATION FAILED' } | Select-Object -Last 3 | ForEach-Object { Say "  $_" }
if (-not $started) { exit 6 }

function Call($method, $url, $headers, $body) {
    try {
        if ($body) { return Invoke-RestMethod -Uri $url -Method $method -ContentType 'application/json; charset=utf-8' -Headers $headers -Body $body }
        return Invoke-RestMethod -Uri $url -Method $method -Headers $headers
    } catch {
        $detail = ''
        if ($_.ErrorDetails) { $detail = $_.ErrorDetails.Message }
        # 关键：这里不能用 Say（Tee-Object 会把输出作为函数返回值），否则失败会被当成成功
        $line = "  !! {0} {1} 失败: {2} {3}" -f $method, $url, $_.Exception.Message, $detail
        Write-Host $line
        Add-Content -Path $out -Value $line -Encoding UTF8
        return $null
    }
}
function Expect($label, $actual, $expected) {
    $ok = [string]$actual -eq [string]$expected
    Say ("  {0}: HTTP {1}（期望 {2}）{3}" -f $label, $actual, $expected, $(if ($ok) { " 通过" } else { " 不通过" }))
}
function HttpCode($method, $url, $token, $json) {
    # 第 4 个参数是 JSON 文本（不是文件路径）：先落盘再交给 curl，避免引号转义问题
    if ($json) {
        $file = 'D:\workspace\OJ-Agent\tools\contest-p2-body.json'
        [System.IO.File]::WriteAllText($file, $json, [System.Text.UTF8Encoding]::new($false))
        return & curl.exe -s -o NUL -w "%{http_code}" -X $method -H "Authorization: Bearer $token" -H 'Content-Type: application/json; charset=utf-8' --data-binary "@$file" $url
    }
    return & curl.exe -s -o NUL -w "%{http_code}" -X $method -H "Authorization: Bearer $token" $url
}
function NewContest($headers, $slug, $startOffsetMin, $endOffsetMin, $problems) {
    $start = (Get-Date).AddMinutes($startOffsetMin).ToString('yyyy-MM-ddTHH:mm:sszzz')
    $end = (Get-Date).AddMinutes($endOffsetMin).ToString('yyyy-MM-ddTHH:mm:sszzz')
    $created = Call 'Post' "$base/api/admin/contests" $headers (@{ slug = $slug; title = "P2 $slug"; startAt = $start; endAt = $end; freezeMinutes = 1; penaltyMinutes = 20 } | ConvertTo-Json)
    $id = $created.data.contest.id
    foreach ($problemSlug in $problems) {
        Call 'Post' "$base/api/admin/contests/$id/problems" $headers (@{ problemSlug = $problemSlug; score = 100 } | ConvertTo-Json) | Out-Null
    }
    return $id
}
function StatusOf($headers, $id) { return (Call 'Get' "$base/api/admin/contests/$id" $headers).data.contest.status }

$suffix = Get-Random -Minimum 1000 -Maximum 9999
$admin = 'p2' + $suffix
$regBody = @{ username = $admin; email = "$admin@example.com"; password = 'Smoke#123456'; displayName = 'P2' } | ConvertTo-Json
$reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
& $java -cp $mysqlJar $helper admin $admin | ForEach-Object { Say "  $_" }
$login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body (@{ identifier = $admin; password = 'Smoke#123456' } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
$token = $login.data.accessToken

Say "=== 1) 空题集直接发布（期望 HTTP 200 + published=false + problems.empty） ==="
$empty = NewContest $headers "p2-empty-$suffix" 120 240 @()
$published = Call 'Post' "$base/api/admin/contests/$empty/publish" $headers $null
Say ("  published={0} issues={1}" -f $published.data.published, (($published.data.issues | ForEach-Object { $_.code }) -join ', '))

Say "=== 2) 选题后发布（期望 published=true + SCHEDULED + published_at 有值） ==="
$live = NewContest $headers "p2-live-$suffix" (-2) 6 @('two-sum', 'palindrome-number')
$ok = Call 'Post' "$base/api/admin/contests/$live/publish" $headers $null
Say ("  published={0} status={1} publishedAt={2}" -f $ok.data.published, $ok.data.contest.status, $ok.data.contest.publishedAt)

Say "=== 3) 公告（SCHEDULED 可发；DRAFT 不可发） ==="
$ann = Call 'Post' "$base/api/admin/contests/$live/announcements" $headers (@{ body = '比赛已发布，请提前熟悉赛制。' } | ConvertTo-Json)
Say ("  公告数量={0} 首条={1}" -f $ann.data.Count, $ann.data[0].body)
$draftOnly = NewContest $headers "p2-draft-$suffix" 300 420 @('two-sum')
$draftAnnCode = HttpCode 'POST' "$base/api/admin/contests/$draftOnly/announcements" $token (@{ body = 'x' } | ConvertTo-Json)
Expect 'DRAFT 发公告' $draftAnnCode 409

Say "=== 4) 定时器推进：已开赛的比赛应自动变 RUNNING ==="
Start-Sleep -Seconds 12
Say ("  p2-live 状态={0}（期望 RUNNING）" -f (StatusOf $headers $live))

Say "=== 5) 已结束的比赛应自动变 ENDED，且未结束不可定榜 ==="
$ended = NewContest $headers "p2-ended-$suffix" (-20) (-10) @('two-sum')
Call 'Post' "$base/api/admin/contests/$ended/publish" $headers $null | Out-Null
$earlyCode = HttpCode 'POST' "$base/api/admin/contests/$live/finalize" $token $null
Expect '对 RUNNING 定榜' $earlyCode 409
Start-Sleep -Seconds 12
Say ("  p2-ended 状态={0}（期望 ENDED）" -f (StatusOf $headers $ended))
$finalized = Call 'Post' "$base/api/admin/contests/$ended/finalize" $headers $null
Say ("  定榜后状态={0} finalizedAt={1}" -f $finalized.data.contest.status, $finalized.data.contest.finalizedAt)
$againCode = HttpCode 'POST' "$base/api/admin/contests/$ended/finalize" $token $null
Expect '重复定榜' $againCode 409

Say "=== 6) 取消：DRAFT 可取消，取消后不可再取消 ==="
$cancelled = Call 'Post' "$base/api/admin/contests/$draftOnly/cancel" $headers $null
Say ("  取消后状态={0}" -f $cancelled.data.contest.status)
$cancelAgainCode = HttpCode 'POST' "$base/api/admin/contests/$draftOnly/cancel" $token $null
Expect '重复取消' $cancelAgainCode 409

Say "=== 7) 状态与审计核对 ==="
& $java -cp $mysqlJar $helper contest | ForEach-Object { Say "  $_" }
& $java -cp $mysqlJar $helper audit | ForEach-Object { Say "  $_" }
Get-Content 'D:\workspace\OJ-Agent\backend-server.out.log' | Where-Object { $_ -match 'CONTEST_STARTED|CONTEST_ENDED' } | Select-Object -Last 4 | ForEach-Object { Say ("  " + $_.Trim()) }
Say "=== DONE ==="
