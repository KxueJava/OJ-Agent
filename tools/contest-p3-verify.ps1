param([Parameter(Mandatory = $true)][string]$ApiKey)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$out = 'D:\workspace\OJ-Agent\tools\contest-p3-verify.txt'
Set-Content -Path $out -Value "contest P3 verify $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$mysqlJar = (Get-ChildItem 'C:\Users\Administrator\.m2\repository\com\mysql\mysql-connector-j' -Recurse -Filter '*.jar' | Sort-Object FullName -Descending | Select-Object -First 1).FullName
$helper = 'D:\workspace\OJ-Agent\tools\JudgeAdmin.java'
$base = 'http://127.0.0.1:8080'

Say "=== 0) restart 8080 ==="
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
if (-not $started) { exit 6 }

function Call($method, $url, $headers, $body) {
    try {
        if ($body) { return Invoke-RestMethod -Uri $url -Method $method -ContentType 'application/json; charset=utf-8' -Headers $headers -Body $body }
        return Invoke-RestMethod -Uri $url -Method $method -Headers $headers
    } catch {
        $detail = ''
        if ($_.ErrorDetails) { $detail = $_.ErrorDetails.Message }
        $line = "  !! {0} {1} 失败: {2} {3}" -f $method, $url, $_.Exception.Message, $detail
        Write-Host $line; Add-Content -Path $out -Value $line -Encoding UTF8
        return $null
    }
}
function Expect($label, $actual, $expected) {
    $ok = [string]$actual -eq [string]$expected
    Say ("  {0}: {1}（期望 {2}）{3}" -f $label, $actual, $expected, $(if ($ok) { " 通过" } else { " 不通过" }))
}
function Public2($slug) { return & curl.exe -s -o NUL -w "%{http_code}" "$base/api/contests/$slug" }
function AdminContest($headers, $slug, $startOffset, $endOffset, $problems, $publish) {
    $start = (Get-Date).AddMinutes($startOffset).ToString('yyyy-MM-ddTHH:mm:sszzz')
    $end = (Get-Date).AddMinutes($endOffset).ToString('yyyy-MM-ddTHH:mm:sszzz')
    $created = Call 'Post' "$base/api/admin/contests" $headers (@{ slug = $slug; title = "P3 $slug"; startAt = $start; endAt = $end; freezeMinutes = 1; penaltyMinutes = 20 } | ConvertTo-Json)
    $id = $created.data.contest.id
    foreach ($problemSlug in $problems) { Call 'Post' "$base/api/admin/contests/$id/problems" $headers (@{ problemSlug = $problemSlug; score = 100 } | ConvertTo-Json) | Out-Null }
    if ($publish) { Call 'Post' "$base/api/admin/contests/$id/publish" $headers $null | Out-Null }
    return $id
}

$suffix = Get-Random -Minimum 1000 -Maximum 9999
$admin = 'p3' + $suffix
$reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body (@{ username = $admin; email = "$admin@example.com"; password = 'Smoke#123456'; displayName = 'P3' } | ConvertTo-Json)
& $java -cp $mysqlJar $helper admin $admin | ForEach-Object { Say "  $_" }
$login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body (@{ identifier = $admin; password = 'Smoke#123456' } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }

Say "=== 1) 已发布但未开赛（SCHEDULED）：应可见，但题目隐藏 ==="
$scheduled = "p3-scheduled-$suffix"
AdminContest $headers $scheduled 5 65 @('two-sum', 'palindrome-number') $true | Out-Null
$detail = Call 'Get' "$base/api/contests/$scheduled" $null
Say ("  status={0} problemsHidden={1}" -f $detail.data.contest.status, $detail.data.problemsHidden)
Say ("  题目：" + (($detail.data.problems | ForEach-Object { $_.label + '=' + $(if ($_.title) { $_.title } else { '(隐藏)' }) }) -join ', '))
Say ("  公告数={0}" -f $detail.data.announcements.Count)

Say "=== 2) 未发布（DRAFT）与已取消（CANCELLED）：公开接口应 404 ==="
$draft = "p3-draft-$suffix"
$draftId = AdminContest $headers $draft 300 400 @('two-sum') $false
Expect 'DRAFT 公开详情' (Public2 $draft) 404
$cancelled = "p3-cancelled-$suffix"
$cancelledId = AdminContest $headers $cancelled 400 500 @('two-sum') $false
Call 'Post' "$base/api/admin/contests/$cancelledId/cancel" $headers $null | Out-Null
Expect 'CANCELLED 公开详情' (Public2 $cancelled) 404

Say "=== 3) 已结束的比赛：题目应可见（标题不再是隐藏） ==="
$ended = "p3-ended-$suffix"
AdminContest $headers $ended (-20) (-10) @('two-sum', 'palindrome-number') $true | Out-Null
Start-Sleep -Seconds 12
$endedDetail = Call 'Get' "$base/api/contests/$ended" $null
Say ("  status={0} problemsHidden={1}" -f $endedDetail.data.contest.status, $endedDetail.data.problemsHidden)
Say ("  题目：" + (($endedDetail.data.problems | ForEach-Object { $_.label + '=' + $_.title + '/' + $_.slug }) -join ', '))

Say "=== 4) 公开列表：应只含已发布的三场（不含 DRAFT / CANCELLED） ==="
$list = Call 'Get' "$base/api/contests?size=50" $null
$slugs = @($list.data | ForEach-Object { $_.slug })
Say ("  列表条数={0}" -f $slugs.Count)
Say ("  含 scheduled={0} 含 ended={1} 含 draft={2} 含 cancelled={3}" -f ($slugs -contains $scheduled), ($slugs -contains $ended), ($slugs -contains $draft), ($slugs -contains $cancelled))
Say "=== DONE ==="
