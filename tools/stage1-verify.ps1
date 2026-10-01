param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [string]$Slug = 'palindrome-number'
)

$ErrorActionPreference = 'Continue'
$root = 'D:\workspace\OJ-Agent\backend'
$appDir = Join-Path $root 'codeagent-oj-server'
$out = 'D:\workspace\OJ-Agent\tools\stage1-verify.txt'
Set-Content -Path $out -Value "stage1 verify $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }
function Test-Port([int]$p) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $iar = $client.BeginConnect('127.0.0.1', $p, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(800)) { $client.EndConnect($iar); return $true } else { return $false }
    } catch { return $false } finally { $client.Close() }
}
function Register($base, $name) {
    $user = $name + (Get-Random -Minimum 100000 -Maximum 999999)
    $body = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = $name } | ConvertTo-Json
    $reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $body
    return @{ token = $reg.data.accessToken; headers = @{ Authorization = "Bearer $($reg.data.accessToken)" }; user = $user }
}

Say "=== 0) restart 8080 on the new build ==="
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

$base = 'http://127.0.0.1:8080'
try {
    $a = Register $base 'sa'
    $b = Register $base 'sb'
    Say "userA=$($a.user) userB=$($b.user)"

    $prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/$Slug" -Method Get
    $version = $prob.data.problemVersionId
    $code = 'public class Main { public static void main(String[] args) { System.out.println(true); } }'
    $submit = @{ problemVersion = $version; language = 'JAVA_21'; sourceCode = $code } | ConvertTo-Json
    $created = Invoke-RestMethod -Uri "$base/api/submissions" -Method Post -ContentType 'application/json' -Headers $a.headers -Body $submit
    $idA = $created.data.id
    $status = $created.data.status
    for ($i = 0; $i -lt 40; $i++) {
        if ($status -in @('AC', 'WA', 'CE', 'RE', 'TLE', 'MLE')) { break }
        Start-Sleep -Seconds 2
        $d = Invoke-RestMethod -Uri "$base/api/submissions/$idA" -Method Get -Headers $a.headers
        $status = $d.data.submission.status
    }
    Say "[1] A 提交 -> id=$idA status=$status"

    $listA = Invoke-RestMethod -Uri "$base/api/submissions?page=0&size=20" -Method Get -Headers $a.headers
    Say ("[2] A 的列表: total={0} items={1}" -f $listA.data.total, $listA.data.items.Count)
    $first = $listA.data.items[0]
    Say ("    首条: id={0} slug={1} language={2} status={3} runtime={4}ms diagnosed={5}" -f $first.id, $first.slug, $first.language, $first.status, $first.runtimeMs, $first.diagnosed)
    Say ("    列表字段自检: slug非空={0} title非空={1} createdAt非空={2}" -f [bool]$first.slug, [bool]$first.title, [bool]$first.createdAt)

    $filterWa = Invoke-RestMethod -Uri "$base/api/submissions?status=WA" -Method Get -Headers $a.headers
    $filterAc = Invoke-RestMethod -Uri "$base/api/submissions?status=AC" -Method Get -Headers $a.headers
    Say ("[3] 状态筛选: WA={0} AC={1}" -f $filterWa.data.total, $filterAc.data.total)

    $paged = Invoke-RestMethod -Uri "$base/api/submissions?page=0&size=1" -Method Get -Headers $a.headers
    Say ("[4] 分页 size=1: items={0} total={1} page={2}" -f $paged.data.items.Count, $paged.data.total, $paged.data.page)

    $listB = Invoke-RestMethod -Uri "$base/api/submissions?page=0&size=20" -Method Get -Headers $b.headers
    Say ("[5] B 的列表: total={0} items={1}  (期望 0)" -f $listB.data.total, $listB.data.items.Count)

    $codeB = & curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $($b.token)" "$base/api/submissions/$idA"
    Say ("[6] B 访问 A 的提交详情 -> HTTP {0}  (期望 404)" -f $codeB)

    $codeA = & curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $($a.token)" "$base/api/submissions/$idA"
    Say ("[7] A 访问自己的提交详情 -> HTTP {0}  (期望 200)" -f $codeA)

    $detail = Invoke-RestMethod -Uri "$base/api/submissions/$idA" -Method Get -Headers $a.headers
    Say ("[8] 详情字段: language={0} slug={1} sourceCode长度={2} 公开用例数={3}" -f $detail.data.language, $detail.data.slug, $detail.data.sourceCode.Length, $detail.data.cases.Count)
} catch {
    Say "API failed: $($_.Exception.Message)"
    if ($_.ErrorDetails) { Say $_.ErrorDetails.Message }
}
Say ("port 8080 listening at end = {0}" -f (Test-Port 8080))
Say "=== DONE ==="
