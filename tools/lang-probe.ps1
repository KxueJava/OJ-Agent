$ErrorActionPreference = 'Continue'
$out = 'D:\workspace\OJ-Agent\tools\lang-probe.txt'
Set-Content -Path $out -Value "language probe $(Get-Date -Format s)" -Encoding UTF8
function Say($m) { $m | Tee-Object -FilePath $out -Append }

$base = 'http://127.0.0.1:8080'
$user = 'lang' + (Get-Random -Minimum 100000 -Maximum 999999)
$regBody = @{ username = $user; email = "$user@example.com"; password = 'Smoke#123456'; displayName = 'Lang' } | ConvertTo-Json
$reg = Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody
$token = $reg.data.accessToken
$headers = @{ Authorization = "Bearer $token" }
$prob = Invoke-RestMethod -Uri "$base/api/workspace/problems/palindrome-number" -Method Get
$version = $prob.data.problemVersionId
Say "user=$user version=$version"

$cpp = @'
#include <bits/stdc++.h>
int main(){long long x;if(!(std::cin>>x))return 0;if(x<0){std::cout<<"false";return 0;}long long t=x,r=0;while(t){r=r*10+t%10;t/=10;}std::cout<<(r==x?"true":"false");return 0;}
'@
$c = @'
#include <stdio.h>
int main(void){long long x;if(scanf("%lld",&x)!=1)return 0;if(x<0){printf("false");return 0;}long long t=x,r=0;while(t){r=r*10+t%10;t/=10;}printf(r==x?"true":"false");return 0;}
'@

function Probe($language, $source, $label) {
    $file = "D:\workspace\OJ-Agent\tools\lang-probe-$label.json"
    $body = @{ problemVersion = $version; language = $language; sourceCode = $source } | ConvertTo-Json
    Set-Content -Path $file -Value $body -Encoding utf8
    try {
        $created = Invoke-RestMethod -Uri "$base/api/submissions" -Method Post -ContentType 'application/json' -Headers $headers -Body $body
    } catch {
        Say ("{0}: 提交失败 {1}" -f $label, $_.Exception.Message); return
    }
    $id = $created.data.id
    $status = $created.data.status
    $msg = ''
    for ($i = 0; $i -lt 30; $i++) {
        if ($status -in @('AC', 'WA', 'CE', 'RE', 'TLE', 'MLE')) { break }
        Start-Sleep -Seconds 2
        $d = Invoke-RestMethod -Uri "$base/api/submissions/$id" -Method Get -Headers $headers
        $status = $d.data.submission.status
        $msg = $d.data.submission.verdictMessage
    }
    Say ("{0,-8} language={1,-8} submission={2} verdict={3} message={4}" -f $label, $language, $id, $status, $msg)
}

Probe 'CPP_17' $cpp 'cpp'
Probe 'C_17' $c 'c'
Say "=== DONE ==="
