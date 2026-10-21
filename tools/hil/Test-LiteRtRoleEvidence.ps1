$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'LiteRtRoleEvidence.ps1')
$identity = [pscustomobject]@{ runId='run'; sourceStamp='apk'; modelId='model'; sha256=('a'*64); backend='CPU'; language='LV'; repetitions=3; requestedContext=1024 }
$row = [pscustomobject]@{ runId='run'; sourceStamp='apk'; modelId='model'; sha256=('a'*64); backend='CPU'; language='LV'; repetitions=3; requestedContext=1024; stage='COMPLETED'; role='CHAT'; grade='SUITABLE'; fixtureSuite=1 }
if (-not (Test-LiteRtRoleEvidence $row $identity)) { throw 'Matching completed evidence rejected' }
foreach ($field in @('runId','sourceStamp','modelId','sha256','backend','language','repetitions','requestedContext')) {
    $changed = $row.PSObject.Copy()
    $changed.$field = 'changed'
    if (Test-LiteRtRoleEvidence $changed $identity) { throw "Mismatched $field accepted" }
}
$row.stage='CASE_FINISHED'
if (Test-LiteRtRoleEvidence $row $identity) { throw 'Partial evidence accepted as complete' }
$row.stage='COMPLETED'; $row.grade='NOT_SUITABLE'
if (-not (Test-LiteRtRoleEvidence $row $identity)) { throw 'Completed negative result discarded' }
$row.fixtureSuite=0
if (Test-LiteRtRoleEvidence $row $identity) { throw 'Old suite accepted' }
Write-Host 'PASS: 12 role evidence identity/completion cases'
$shell = (Get-Process -Id $PID).Path
$result = Invoke-LiteRtBoundedProcess -Executable $shell -Arguments @('-NoProfile','-Command','[Console]::WriteLine("fixture-out"); [Console]::Error.WriteLine("fixture-error"); exit 7') -TimeoutSeconds 10
if ($result.ExitCode -ne 7 -or $result.TimedOut -or $result.Text -notmatch 'fixture-out' -or $result.Text -notmatch 'fixture-error') { throw 'Subprocess output/exit lost' }
$result = Invoke-LiteRtBoundedProcess -Executable $shell -Arguments @('-NoProfile','-Command','Start-Sleep -Seconds 30') -TimeoutSeconds 1
if ($result.ExitCode -ne 124 -or -not $result.TimedOut) { throw 'Hung process was not bounded' }
Write-Host 'PASS: subprocess exit, stdout/stderr and timeout'
$selection = @([pscustomobject]@{ id='model'; sha256=('a'*64); declaredTask='GENERATION' })
$row.fixtureSuite=1; $row.grade='SUITABLE'; $row | Add-Member -NotePropertyName atMs -NotePropertyValue 1
$summary = @([pscustomobject]@{ ModelId='model'; Backend='CPU'; Language='LV'; Result='PASSED' })
$report = @(Get-LiteRtSuitabilityRows -Evidence @($row) -Selection $selection -Summary $summary -RunId run -SourceStamp apk -Backends CPU -Languages LV -Repetitions 3 -ContextTokens 1024)
if ($report.Count -ne 5 -or @($report | Where-Object grade -eq 'SUITABLE').Count -ne 1 -or @($report | Where-Object grade -eq 'NOT_TESTED').Count -ne 4) { throw 'Missing roles were not represented as untested' }
$summary[0].Result='RUNNER_INTERRUPTED'
$report = @(Get-LiteRtSuitabilityRows -Evidence @($row) -Selection $selection -Summary $summary -RunId run -SourceStamp apk -Backends CPU -Languages LV -Repetitions 3 -ContextTokens 1024)
if (@($report | Where-Object grade -ne 'NOT_TESTED').Count -gt 0) { throw 'Interrupted runner promoted evidence' }
$summary[0].Result='PASSED'
$newer=$row.PSObject.Copy(); $newer.grade='NOT_SUITABLE'; $newer.atMs=2
$report = @(Get-LiteRtSuitabilityRows -Evidence @($row,$newer) -Selection $selection -Summary $summary -RunId run -SourceStamp apk -Backends CPU -Languages LV -Repetitions 3 -ContextTokens 1024)
if (($report | Where-Object role -eq 'CHAT').grade -ne 'NOT_SUITABLE') { throw 'Older positive result hid newer failure' }
Write-Host 'PASS: complete role matrix, interruption and latest negative evidence'
