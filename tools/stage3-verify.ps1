param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [string]$Slug = 'palindrome-number'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$out = 'D:\workspace\OJ-Agent\tools\stage3-verify.txt'
Set-Content -Path $out -Value "stage3 verify $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
function Wait-Terminal($base, $headers, $id) {
    $detail = Invoke-RestMethod -Uri "$base/api/submissions/$id" -Method Get -Headers $headers
    for ($i = 0; $i -lt 45; $i++) {
        $status = $detail.data.submission.status
        if ($status -in @('AC', 'WA', 'CE', 'RE', 'TLE', 'MLE')) { return $detail.data.submission }
        Start-Sleep -Seconds 2
        $detail = Invoke-RestMethod -Uri "$base/api/submissions/$id" -Method Get -Headers $headers
    }
    return $detail.data.submission
}

Say "=== 0) restart 8080 (applies V24) ==="
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) { Stop-Process -Id $listener.OwningProcess -Force; Say "killed pid=$($listener.OwningProcess)"; Start-Sleep -Seconds 4 }
Push-Location $root
& 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp -DskipTests -pl codeagent-oj-server package 2>&1 | Select-Object -Last 2 | ForEach-Object { Say $_ }
Pop-Location
$jar = (Get-ChildItem (Join-Path $appDir 'target\codeagent-oj-server-*.jar') | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1).FullName
$env:DEEPSEEK_API_KEY = $ApiKey
$env:SERVER_PORT = '8080'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$proc = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $appDir `
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
if (Test-Path 'D:\workspace\OJ-Agent\backend-server.out.log') {
    Get-Content 'D:\workspace\OJ-Agent\backend-server.out.log' | Where-Object { $_ -match 'Migrating schema|Successfully applied|ERROR' } | Select-Object -Last 4 | ForEach-Object { Say $_ }
}
if (-not $started) { exit 6 }

$base = 'http://127.0.0.1:8080'
try {
    $user = 's3' + (Get-Random -Minimum 100000 -Maximum 999999)
    $regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'S3' } | ConvertTo-Json
    $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
    $headers = @{ Authorization = "Bearer $($reg.data.accessToken)" }
    $problem = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
    $version = $problem.data.problemVersionId

    Say "=== 1) 提交一个错误答案，看重判与计时字段 ==="
    $bad = 'public class Main { public static void main(String[] args) { System.out.println(true); } }'
    $submit = @{ problemVersion = $version; language = 'JAVA_21'; sourceCode = $bad } | ConvertTo-Json
    $created = Invoke-RestMethod -Uri "$base/api/submissions" -Method Post -ContentType 'application/json' -Headers $headers -Body $submit
    $id = $created.data.id
    $first = Wait-Terminal $base $headers $id
    Say ("  id={0} status={1} compileMs={2} runtimeMs={3} failureKind={4} rejudgeCount={5} message={6}" -f $id, $first.status, $first.compileMs, $first.runtimeMs, $first.failureKind, $first.rejudgeCount, $first.verdictMessage)

    $before = Invoke-RestMethod -Uri "$base/api/submissions/$id" -Method Get -Headers $headers
    Say ("  重判前公开用例行数 = {0}" -f $before.data.cases.Count)

    Say "=== 2) 重判（POST /api/submissions/{id}/rejudge） ==="
    $rejudged = Invoke-RestMethod -Uri "$base/api/submissions/$id/rejudge" -Method Post -Headers $headers
    Say ("  重判响应 status={0} rejudgeCount={1}" -f $rejudged.data.status, $rejudged.data.rejudgeCount)
    $second = Wait-Terminal $base $headers $id
    Say ("  重判后 status={0} failureKind={1} rejudgeCount={2} compileMs={3}" -f $second.status, $second.failureKind, $second.rejudgeCount, $second.compileMs)
    $after = Invoke-RestMethod -Uri "$base/api/submissions/$id" -Method Get -Headers $headers
    Say ("  重判后公开用例行数 = {0}  (期望与重判前相同，不重复累加)" -f $after.data.cases.Count)

    Say "=== 3) 越权：B 重判 A 的提交应失败 ==="
    $userB = 's3b' + (Get-Random -Minimum 100000 -Maximum 999999)
    $regBodyB = @{ username = $userB; email = "$userB@example.com"; password = 'Smoke#123456'; displayName = 'S3B' } | ConvertTo-Json
    $regB = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBodyB
    $code = & curl.exe -s -o NUL -w "%{http_code}" -X POST -H "Authorization: Bearer $($regB.data.accessToken)" "$base/api/submissions/$id/rejudge"
    Say ("  B 重判 A 的提交 -> HTTP {0}  (期望 400)" -f $code)

    Say "=== 4) 管理员批量重判接口的权限 ==="
    $adminBody = @{ problemVersionId = $version; limit = 5 } | ConvertTo-Json
    $adminFile = 'D:\workspace\OJ-Agent\tools\stage3-admin.json'
    [System.IO.File]::WriteAllText($adminFile, $adminBody, [System.Text.UTF8Encoding]::new($false))
    $code2 = & curl.exe -s -o NUL -w "%{http_code}" -X POST -H "Authorization: Bearer $($reg.data.accessToken)" -H 'Content-Type: application/json' --data-binary "@$adminFile" "$base/api/admin/submissions/rejudge"
    Say ("  普通用户调用管理员重判 -> HTTP {0}  (期望 403)" -f $code2)
} catch {
    Say "API failed: $($_.Exception.Message)"
    if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
}
Say "=== DONE ==="
