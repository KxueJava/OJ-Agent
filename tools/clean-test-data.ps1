param([switch]$Apply)

# Clean smoke/test data created during development.
# Default is DRY-RUN (prints the plan). Pass -Apply to execute.
# Scope is explicit: only these username prefixes and contest slug prefixes.
# Kept: contest '333' (the real one), plus user-made drafts 'trt' and 'hhh'.

$ErrorActionPreference = 'Continue'
$mysql = 'D:\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysql.exe'
$db = 'codeagent_oj_local'

$userPrefixes = @('p1','p2','p3','p4','p5','c1','c2','c3','c4','c5','s1','s2','s3','s4',
                  'a2','attr','lock','scope','g1','g2','g3','reg','mine','solved','fmt',
                  'p4b','detail','smoke','e2e','lang')
$contestPrefixes = @('p2-','p3-','p4-','p4b-','p5-','c3-','c3b-','contest-smoke-')

$userMatch = ($userPrefixes | ForEach-Object { "u.username LIKE '$_%'" }) -join ' OR '
$contestMatch = ($contestPrefixes | ForEach-Object { "c.slug LIKE '$_%'" }) -join ' OR '

function Q([string]$sql) { & $mysql -uroot -proot $db --default-character-set=utf8 -e $sql 2>$null }
function P([string]$sql) { & $mysql -uroot -proot $db --default-character-set=utf8 -e $sql 2>&1 | Where-Object { $_ -notmatch 'Using a password' } }

Write-Host '=== current inventory ==='
P "SELECT (SELECT COUNT(*) FROM users u WHERE $userMatch) AS test_accounts, (SELECT COUNT(*) FROM users) AS all_accounts;"
P "SELECT (SELECT COUNT(*) FROM submissions s JOIN users u ON u.id=s.user_id WHERE $userMatch) AS test_subs, (SELECT COUNT(*) FROM contests c WHERE $contestMatch) AS test_contests;"

# Tables that reference users(id): build a generic cleanup for each one so no orphan rows remain.
$tables = @()
$rows = P "SELECT TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='$db' AND REFERENCED_TABLE_NAME='users' AND COLUMN_NAME='user_id';"
foreach ($line in $rows) {
  $name = ($line -split "`t")[0]
  if ($name -and $name -ne 'TABLE_NAME') { $tables += $name }
}
Write-Host ('tables referencing users(user_id): ' + ($tables -join ', '))

$steps = New-Object System.Collections.ArrayList
# 1) agent_findings points at BOTH submissions and agent_sessions -> delete first
[void]$steps.Add("DELETE f FROM agent_findings f JOIN submissions s ON s.id=f.submission_id JOIN users u ON u.id=s.user_id WHERE $userMatch")
[void]$steps.Add("DELETE f FROM agent_findings f JOIN submissions s ON s.id=f.submission_id JOIN contests c ON c.id=s.contest_id WHERE $contestMatch")
[void]$steps.Add("DELETE f FROM agent_findings f JOIN agent_sessions a ON a.id=f.session_id JOIN users u ON u.id=a.user_id WHERE $userMatch")
# 2) children of submissions: submission_cases / learning_events / mistake_books (all reference submission_id)
foreach ($child in @('submission_cases','learning_events','mistake_books')) {
  [void]$steps.Add("DELETE x FROM $child x JOIN submissions s ON s.id=x.submission_id JOIN users u ON u.id=s.user_id WHERE $userMatch")
  [void]$steps.Add("DELETE x FROM $child x JOIN submissions s ON s.id=x.submission_id JOIN contests c ON c.id=s.contest_id WHERE $contestMatch")
}
# 3) submissions themselves
[void]$steps.Add("DELETE s FROM submissions s JOIN users u ON u.id=s.user_id WHERE $userMatch")
[void]$steps.Add("DELETE s FROM submissions s JOIN contests c ON c.id=s.contest_id WHERE $contestMatch")
# 4) children of agent sessions: agent_messages / agent_tool_calls / agent_audits (reference session_id only)
foreach ($child in @('agent_messages','agent_tool_calls','agent_audits')) {
  [void]$steps.Add("DELETE x FROM $child x JOIN agent_sessions a ON a.id=x.session_id JOIN users u ON u.id=a.user_id WHERE $userMatch")
}
[void]$steps.Add("DELETE cp FROM contest_participants cp JOIN contests c ON c.id=cp.contest_id WHERE $contestMatch")
[void]$steps.Add("DELETE a FROM contest_announcements a JOIN contests c ON c.id=a.contest_id WHERE $contestMatch")
[void]$steps.Add("DELETE p FROM contest_problems p JOIN contests c ON c.id=p.contest_id WHERE $contestMatch")
[void]$steps.Add("DELETE c FROM contests c WHERE $contestMatch OR c.slug = 'test'")
foreach ($t in $tables) { [void]$steps.Add("DELETE t FROM $t t JOIN users u ON u.id=t.user_id WHERE $userMatch") }
# audit log rows of the deleted admins, then the accounts themselves
[void]$steps.Add("DELETE FROM admin_audit_log WHERE admin_user_id IN (SELECT id FROM users u WHERE $userMatch)")
[void]$steps.Add("DELETE u FROM users u WHERE $userMatch")

if (-not $Apply) {
  Write-Host '=== DRY RUN: the following statements would run (pass -Apply to execute) ==='
  $i = 0
  foreach ($step in $steps) { $i++; Write-Host ("  {0,2}. {1}" -f $i, (($step -replace '\s+', ' ').Substring(0, [Math]::Min(150, ($step -replace '\s+', ' ').Length)))) }
  exit 0
}

Write-Host '=== APPLYING ==='
$i = 0
foreach ($step in $steps) {
  $i++
  $short = ($step -replace '\s+', ' ')
  if ($short.Length -gt 90) { $short = $short.Substring(0, 90) + '...' }
  $result = P $step
  if ($result) { Write-Host ("  {0,2}. {1} -> {2}" -f $i, $short, ($result -join ' ')) } else { Write-Host ("  {0,2}. {1} -> ok" -f $i, $short) }
}

Write-Host '=== after ==='
P "SELECT (SELECT COUNT(*) FROM users) AS all_accounts, (SELECT COUNT(*) FROM contests) AS contests, (SELECT COUNT(*) FROM submissions) AS subs;"
P "SELECT c.slug, c.title, c.status FROM contests c ORDER BY c.id;"
