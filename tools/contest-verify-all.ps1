# contest-verify-all.ps1 -- one-shot verification for the contest feature (P1/P2/P3)
#
# ASCII-only on purpose: Windows PowerShell 5.1 parses BOM-less UTF-8 scripts as GBK,
# and non-ASCII text corrupts quotes and breaks parsing. Keep this file ASCII.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\contest-verify-all.ps1 -ApiKey <DEEPSEEK_API_KEY>
#
# It runs, in order:
#   1) backend unit tests                     (mvn -B -ntp test)
#   2) frontend type check                    (npx tsc --noEmit)
#   3) P1 acceptance: admin CRUD + validation (tools/stage... contest-p1-verify.ps1)
#   4) P2 acceptance: publish/announce/sweep/finalize/cancel + guards
#   5) P3 acceptance: public visibility rules (404 / hidden problems / list filtering)
# and prints a PASS/FAIL summary at the end.

param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [switch]$SkipBackendTests,
    [switch]$SkipFrontendTypes
)

$ErrorActionPreference = 'Continue'
$repo = Split-Path -Parent $PSScriptRoot
$summary = New-Object System.Collections.Generic.List[string]

function Step($name, [scriptblock]$action) {
    Write-Host ""
    Write-Host ("=== " + $name + " ===") -ForegroundColor Cyan
    & $action
    $code = $LASTEXITCODE
    if ($null -eq $code) { $code = 0 }
    $script:summary.Add(("{0,-42} exit={1}" -f $name, $code))
    return $code
}

if (-not $SkipBackendTests) {
    Push-Location (Join-Path $repo 'backend')
    Step 'backend unit tests (mvn test)' {
        & 'D:\apache-maven-3.9.14\bin\mvn.cmd' -B -ntp test 2>&1 | Select-String -Pattern 'Tests run:.*Failures|BUILD' | Select-Object -Last 4 | ForEach-Object { Write-Host $_ }
    } | Out-Null
    Pop-Location
}

if (-not $SkipFrontendTypes) {
    Push-Location (Join-Path $repo 'frontend')
    Step 'frontend type check (tsc --noEmit)' {
        $node = (Get-Command node -ErrorAction SilentlyContinue).Source
        & $node 'node_modules\typescript\bin\tsc' --noEmit 2>&1 | Select-Object -Last 20 | ForEach-Object { Write-Host $_ }
    } | Out-Null
    Pop-Location
}

foreach ($script in 'contest-p1-verify.ps1', 'contest-p2-verify.ps1', 'contest-p3-verify.ps1', 'contest-p4-verify.ps1') {
    $path = Join-Path $PSScriptRoot $script
    if (-not (Test-Path $path)) { $summary.Add(("{0,-42} MISSING" -f $script)); continue }
    Step ("acceptance: " + $script) { & $path -ApiKey $ApiKey }
}

Write-Host ""
Write-Host "=== SUMMARY ===" -ForegroundColor Cyan
$summary | ForEach-Object { Write-Host $_ }
Write-Host ""
Write-Host "Notes:"
Write-Host "  - Each acceptance script restarts the backend on port 8080 and may stop the old instance."
Write-Host "  - P2/P3 use -Dapp.contest.sweep-ms=5000 so scheduler transitions are observable in seconds."
Write-Host "  - Restart 8080 WITHOUT that flag afterwards if you want production timing (30s)."
