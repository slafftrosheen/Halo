#requires -Version 7.0
<#
.SYNOPSIS
  Explicit physical Android LiteRT diagnostic sweep for installed Halo artifacts.
.DESCRIPTION
  Runs one model/backend/context per instrumentation process, preserving runner exit truth.
  Records platform available RAM, process PSS and native heap at each probe stage.
  -Cycles N recreates the engine between probes. -ResidentTurns N keeps ONE engine
  and recreates its Conversation N times, alternating strict sentinel prompts.
  Only the latter probes conversation isolation and memory reclamation.
  For recurrent architectures, a FAILED strict sentinel is evidence requiring investigation.
  There is no default model. Explicit -Models or -InstalledOnly uses a freshly verified device inventory.
  -InspectOnly reads the actual LiteRT ModelInfo task/modalities/backends without
  initializing a generation engine. It can still temporarily allocate native memory.
  Failed native probes are FAILED by default. -CaptureOnly records expected failures
  without treating them as accelerator qualification. Never downloads or selects models.
  -VerifyProvider additionally exercises Halo's provider on the explicit context/backend;
  it does not write model tuning or role bindings. Run after the resident baseline passes.
  The app:testDebugAndroidTest APK must be installed separately.
.EXAMPLE
  .\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Serial YOUR_ADB_ID -InstalledOnly -InspectOnly
.EXAMPLE
  .\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Models qwen3-0.6b-dynamic-int4 -Backend CPU -ResidentTurns 5 -ContextTokens 2048
.EXAMPLE
  .\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Serial YOUR_ADB_ID -InstalledOnly -RoleSuite -Languages EN,LV,RU,PL -Repetitions 3 -Backend CPU
#>
[CmdletBinding()]
param(
    [string] $Serial = "",
    [string[]] $Models = @(),
    [switch] $InstalledOnly,
    [switch] $AllowGpu,
    [string] $CpuCycleEvidenceDir = "",
    [string] $CpuResidentEvidenceDir = "",
    [string] $Adb = "adb",
    [ValidateSet("CPU", "GPU", "BOTH")]
    [string] $Backend = "CPU",
    [ValidateRange(1, 5)]
    [int] $Cycles = 1,
    [ValidateRange(0, 10)]
    [int] $ResidentTurns = 0,
    [switch] $InspectOnly,
    [switch] $VerifyProvider,
    [switch] $RoleSuite,
    [ValidateSet('EN', 'LV', 'RU', 'PL')]
    [string[]] $Languages = @('EN', 'LV', 'RU', 'PL'),
    [ValidateRange(1, 5)]
    [int] $Repetitions = 3,
    [switch] $Resume,
    [ValidateRange(30, 7200)]
    [int] $TimeoutSeconds = 1800,
    [ValidateSet(0, 1024, 2048, 4096, 8192)]
    [int] $ContextTokens = 0,
    [switch] $CaptureOnly,
    [string] $OutputDir = ("litert-phone-matrix-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
)
$ErrorActionPreference = "Stop"
$Backend = $Backend.ToUpperInvariant()
$Languages = @($Languages | ForEach-Object { $_.ToUpperInvariant() } | Select-Object -Unique)
$Models = @($Models | Select-Object -Unique)
. (Join-Path $PSScriptRoot "LiteRtProbeStatus.ps1")
. (Join-Path $PSScriptRoot "LiteRtRoleEvidence.ps1")
if (-not (Get-Command $Adb -ErrorAction SilentlyContinue)) {
    throw "adb is not in PATH. Install Android platform-tools and connect the test phone."
}
if (-not $Serial.Trim()) { throw "Select an explicit physical device serial with -Serial." }
if ($Serial -like 'emulator-*') { throw "Physical model qualification cannot use an emulator." }
if (($Models.Count -gt 0) -eq [bool]$InstalledOnly) { throw "Choose explicit -Models or -InstalledOnly; no default model is permitted." }
if (-not $InspectOnly -and $Backend -ne "CPU" -and -not $AllowGpu) { throw "GPU requires explicit operator approval through -AllowGpu after a CPU pass." }
$deviceArgs = @("-s", $Serial.Trim())
$packageName = "com.aistudio.braintool.xkqzps"
$testRunner = "$packageName.test/androidx.test.runner.AndroidJUnitRunner"
if ($InspectOnly -and ($ResidentTurns -gt 0 -or $Cycles -ne 1 -or $ContextTokens -gt 0)) {
    throw "-InspectOnly reads metadata; do not combine with context, cycles or resident generation."
}
if ($ResidentTurns -eq 1) { throw "-ResidentTurns must be 0 (fresh engines) or 2..10 (one resident engine)." }
if ($VerifyProvider -and ($InspectOnly -or $ResidentTurns -gt 0)) { throw "-VerifyProvider requires fresh-engine generation probes." }
if ($ResidentTurns -gt 0 -and $Cycles -ne 1) { throw "Choose either -ResidentTurns or -Cycles, not both." }
if ($RoleSuite -and ($InspectOnly -or $VerifyProvider -or $ResidentTurns -gt 0 -or $Cycles -ne 1 -or $CaptureOnly)) {
    throw '-RoleSuite uses real provider role probes; do not combine it with metadata/control/resident/capture modes.'
}
if ($Resume -and -not $RoleSuite) { throw '-Resume is supported only for -RoleSuite.' }
if ($Resume -and -not (Test-Path -LiteralPath (Join-Path $OutputDir 'selected-installed-models.json'))) {
    Write-Host 'No immutable selection exists yet; starting the first verified role batch.'
    $Resume = $false
}
$testClass = if ($RoleSuite) {
    'com.halo.app.audio.LocalModelRolePhoneTest#qualifyInstalledRoles'
} elseif ($InspectOnly) {
    "com.halo.app.audio.LocalArtifactInspectionTest#inspectInstalledArtifact"
} elseif ($ResidentTurns -gt 0) {
    "com.halo.app.audio.DownloadedModelDiagnosticsTest#diagnoseResidentEndurance"
} else {
    "com.halo.app.audio.DownloadedModelDiagnosticsTest#diagnoseInstalledModel"
}
$backends = if ($InspectOnly) { @("INFO") } elseif ($Backend -eq "BOTH") { @("CPU", "GPU") } else { @($Backend) }
if ((Test-Path -LiteralPath (Join-Path $OutputDir "selected-installed-models.json")) -and -not $Resume) { throw "Use a fresh matrix OutputDir or -RoleSuite -Resume; the selection is immutable." }
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$summary = [System.Collections.Generic.List[object]]::new()
function Save-MatrixSummary {
    $summary | Select-Object ModelId,Backend,Language,Cycles,ResidentTurns,ContextTokens,Result,ProcessExit,OutputFile,Reason |
        Export-Csv -LiteralPath (Join-Path $OutputDir 'summary.csv') -NoTypeInformation -Encoding utf8
}
$existingInventory = Test-Path -LiteralPath (Join-Path $OutputDir 'inventory/installed-models.json')
$inventoryDir = if ($Resume -or ($RoleSuite -and $existingInventory)) { 'inventory-resume-' + [guid]::NewGuid().ToString('N') } else { 'inventory' }
$inventory = & (Join-Path $PSScriptRoot "Get-InstalledLiteRtModels.ps1") -Serial $Serial -Adb $Adb -OutputDir (Join-Path $OutputDir $inventoryDir)
if ($inventory.status -ne "INVENTORY_VERIFIED") {
    $statusName = if ($Resume) { 'resume-status.csv' } else { 'summary.csv' }
    [pscustomobject]@{ Result=$inventory.status; Serial=$Serial; Reason=$inventory.reason } | Export-Csv (Join-Path $OutputDir $statusName) -NoTypeInformation
    if ($RoleSuite) { throw "Role campaign not run: $($inventory.status). Reconnect the phone and resume with the same configuration." }
    if ($inventory.status -ne "NOT_RUN_DEVICE_UNAVAILABLE") { throw "Installed inventory was interrupted; no artifact is selected." }
    return
}
$eligible = @($inventory.models | Where-Object { $_.verified -eq $true -and $_.runnable -eq $true -and $_.sha256 -match '^[0-9a-f]{64}$' })
if ($InstalledOnly) { $Models = @($eligible | Sort-Object bytes | ForEach-Object { $_.id }) }
$selected = @($eligible | Where-Object { $_.id -in $Models })
$runId = [guid]::NewGuid().ToString("N")
$selectionFile = Join-Path $OutputDir "selected-installed-models.json"
if ($Resume) {
    $previous = Get-Content -LiteralPath $selectionFile -Raw | ConvertFrom-Json
    $oldKeys = @($previous.models | ForEach-Object { "$($_.id):$($_.sha256):$($_.bytes)" } | Sort-Object)
    $newKeys = @($selected | ForEach-Object { "$($_.id):$($_.sha256):$($_.bytes)" } | Sort-Object)
    if ($previous.serial -cne $Serial -or $previous.sourceStamp -cne $inventory.sourceStamp -or
        -not $previous.roleSuite -or $previous.contextTokens -ne $ContextTokens -or $previous.backend -ne $Backend -or
        $previous.repetitions -ne $Repetitions -or ($previous.languages -join ',') -cne ($Languages -join ',') -or
        (Compare-Object $oldKeys $newKeys -CaseSensitive)) { throw 'Resume identity/configuration changed. Use a fresh OutputDir.' }
    $runId = $previous.runId
} else {
[pscustomobject]@{ schema=1; serial=$Serial; runId=$runId; sourceStamp=$inventory.sourceStamp; capturedAtMs=$inventory.capturedAtMs; models=$selected;
    roleSuite=[bool]$RoleSuite; contextTokens=$ContextTokens; backend=$Backend; languages=$Languages; repetitions=$Repetitions } |
    ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $selectionFile -Encoding utf8
(Get-Item -LiteralPath $selectionFile).IsReadOnly = $true
}
if ($Models.Count -eq 0) {
    $emptyStatus = if (@($inventory.models).Count -eq 0) { "SKIPPED_NOT_INSTALLED" } else { "FAILED_MODEL" }
    [pscustomobject]@{ ModelId=""; Backend=$Backend; Result=$emptyStatus; Reason="NO_VERIFIED_RUNNABLE_INSTALLED_ARTIFACTS" } |
        Export-Csv -LiteralPath (Join-Path $OutputDir "summary.csv") -NoTypeInformation -Encoding utf8
    if ($emptyStatus -eq "FAILED_MODEL") { throw "Installed artifacts did not pass admission; no engine was started." }
    Write-Host "SKIPPED_NOT_INSTALLED: no installed artifact is available for this run."
    return
}
$initialFilenames = @(Get-Content (Join-Path $OutputDir "$inventoryDir/filenames.txt") | Where-Object { $_.Length -gt 0 })
$previousSummary = if ($Resume -and (Test-Path (Join-Path $OutputDir 'summary.csv'))) { @(Import-Csv (Join-Path $OutputDir 'summary.csv')) } else { @() }
try {

foreach ($modelId in $Models) {
    if ($modelId -notmatch '^[a-z0-9][a-z0-9._-]{1,99}$') { throw "Unsafe model ID: $modelId" }
    $artifact = $selected | Where-Object { $_.id -eq $modelId } | Select-Object -First 1
    if ($null -eq $artifact -or (-not $InspectOnly -and $artifact.declaredTask -ne "GENERATION")) {
        $summary.Add([pscustomobject]@{ ModelId=$modelId; Backend=$Backend; Result=if($null -eq $artifact){if(@($inventory.models | Where-Object { $_.id -eq $modelId }).Count -gt 0){"FAILED_MODEL"}else{"SKIPPED_NOT_INSTALLED"}}else{"SKIPPED_TASK_NOT_GENERATION"}; ProcessExit=$null })
        continue
    }
    foreach ($kind in $backends) {
        if ($kind -eq "GPU") {
            foreach ($proofDir in @($CpuCycleEvidenceDir, $CpuResidentEvidenceDir)) {
                if (-not $proofDir) { throw "GPU requires saved CPU cycles and resident evidence for this exact artifact." }
                $proofSelection = Get-Content -LiteralPath (Join-Path $proofDir "selected-installed-models.json") -Raw | ConvertFrom-Json
                $matching = @($proofSelection.models | Where-Object { $_.id -eq $modelId -and $_.sha256 -eq $artifact.sha256 -and $_.bytes -eq $artifact.bytes })
                $proofSummary = @(Import-Csv -LiteralPath (Join-Path $proofDir "summary.csv") | Where-Object { $_.ModelId -eq $modelId -and $_.Backend -eq "CPU" -and $_.Result -eq "PASSED" -and $_.ContextTokens -eq "1024" })
                $required = if ($proofDir -eq $CpuCycleEvidenceDir) { @($proofSummary | Where-Object { [int]$_.Cycles -ge 3 }) } else { @($proofSummary | Where-Object { [int]$_.ResidentTurns -ge 5 }) }
                if ($proofSelection.serial -ne $Serial -or $proofSelection.sourceStamp -ne $inventory.sourceStamp -or $matching.Count -ne 1 -or $required.Count -eq 0) {
                    throw "GPU CPU evidence is missing, stale or belongs to a different device/artifact/source."
                }
            }
            $memory = (& $Adb @deviceArgs shell cat /proc/meminfo | Out-String)
            if ($LASTEXITCODE -ne 0 -or $memory -notmatch 'MemAvailable:\s+(\d+)\s+kB') { throw "GPU available RAM could not be checked." }
            if ([long]$Matches[1] * 1024 -lt [long]$artifact.bytes + 1GB) { throw "GPU conservative RAM headroom check failed." }
        }
        $label = if ($InspectOnly) { "$modelId-metadata" }
                 elseif ($ResidentTurns -gt 0) { "$modelId-$kind-ctx$ContextTokens-resident$ResidentTurns" }
                 else { "$modelId-$kind-ctx$ContextTokens-cycles$Cycles" }
        if ($VerifyProvider) { $label += "-provider" }
        $probeLanguages = if ($RoleSuite) { @($Languages | Select-Object -Unique) } else { @('NONE') }
        foreach ($language in $probeLanguages) {
        $probeLabel = if ($RoleSuite) { "$label-roles-$language-r$Repetitions" } else { $label }
        $labelBase = $label
        $label = $probeLabel
        $identity = [pscustomobject]@{ runId=$runId; sourceStamp=$inventory.sourceStamp; modelId=$modelId; sha256=$artifact.sha256;
            backend=$kind; language=$language; repetitions=$Repetitions; requestedContext=$ContextTokens }
        $old = @($previousSummary | Where-Object { $_.ModelId -ceq $modelId -and $_.Backend -ceq $kind -and $_.Language -ceq $language -and $_.Result -eq 'PASSED' })
        $savedEvidence = Join-Path $OutputDir 'local-model-role-evidence.jsonl'
        if ($RoleSuite -and $old.Count -eq 1 -and (Test-Path -LiteralPath $savedEvidence)) {
            $complete = @(Get-Content -LiteralPath $savedEvidence | ForEach-Object {
                try { $r = $_ | ConvertFrom-Json; if (Test-LiteRtRoleEvidence $r $identity) { $r.role } } catch { }
            } | Sort-Object -Unique)
            if ($complete.Count -eq 5) { $summary.Add($old[0]); $label=$labelBase; continue }
        }
        Write-Host "LiteRT physical probe: $label (captureOnly=$CaptureOnly)"
        $deviceState = & $Adb @deviceArgs get-state 2>&1
        $deviceReady = $LASTEXITCODE -eq 0 -and (($deviceState | Out-String).Trim() -eq "device")
        if (-not $deviceReady) {
            ($deviceState | Out-String) | Set-Content -Path (Join-Path $OutputDir "$label.txt") -Encoding UTF8
            $summary.Add([pscustomobject]@{
                ModelId = $modelId; Backend = $kind
                Language = $language
                Cycles = if ($ResidentTurns -gt 0) { 0 } else { $Cycles }
                ResidentTurns = $ResidentTurns
                ContextTokens = if ($ContextTokens -gt 0) { $ContextTokens } else { "PROFILE" }
                Result = "NOT_RUN_DEVICE_UNAVAILABLE"; ProcessExit = $LASTEXITCODE
                OutputFile = "$label.txt"
            })
            Write-Host "  NOT_RUN_DEVICE_UNAVAILABLE; reconnect the phone before retrying."
            $label=$labelBase
            continue
        }
        $currentFilenames = & $Adb @deviceArgs shell run-as $packageName ls -1 no_backup/models 2>&1
        if ($LASTEXITCODE -ne 0 -or (Compare-Object $initialFilenames @($currentFilenames | Where-Object { $_.Length -gt 0 }) -CaseSensitive)) {
            $summary.Add([pscustomobject]@{ ModelId=$modelId; Backend=$kind; Result="RUNNER_INTERRUPTED"; ProcessExit=$LASTEXITCODE; Reason="INSTALLED_INVENTORY_CHANGED_OR_UNAVAILABLE" })
            throw "Installed inventory changed or became unavailable. Stop this run; do not reselect or download."
        }
        $parameters = @(
            "shell", "am", "instrument", "-w", "-r",
            "-e", "class", $testClass,
            "-e", "modelId", $modelId,
            "-e", "expectedSha256", $artifact.sha256,
            "-e", "expectedBytes", "$($artifact.bytes)",
            "-e", "sourceStamp", $inventory.sourceStamp,
            "-e", "runId", $runId
        )
        if (-not $InspectOnly) {
            $parameters += @("-e", "backend", $kind, "-e", "cycles", "$Cycles")
        }

        if ($ResidentTurns -gt 0) { $parameters += @("-e", "residentTurns", "$ResidentTurns") }
        if ($ContextTokens -gt 0) { $parameters += @("-e", "contextTokens", "$ContextTokens") }
        if ($VerifyProvider) { $parameters += @("-e", "verifyProvider", "true") }
        if ($CaptureOnly) { $parameters += @("-e", "captureOnly", "true") }
        if ($RoleSuite) { $parameters += @('-e','language',$language,'-e','repetitions',"$Repetitions") }
        $parameters += $testRunner
        $invocation = Invoke-LiteRtBoundedProcess -Executable (Get-Command $Adb).Source -Arguments (@($deviceArgs) + $parameters) -TimeoutSeconds $TimeoutSeconds
        $exit = $invocation.ExitCode
        $text = $invocation.Text
        if ($invocation.TimedOut) {
            & $Adb @deviceArgs shell am force-stop $packageName | Out-Null
            $text += "`nHOST_TIMEOUT: $TimeoutSeconds seconds; target app stopped."
        }
        $text | Set-Content -Path (Join-Path $OutputDir "$label.txt") -Encoding UTF8
        $instrumentOk = $exit -eq 0 -and $text -match 'INSTRUMENTATION_CODE:\s*-1' -and
            $text -match 'OK\s*\(1\s*test\)'
        $status = Resolve-LiteRtProbeStatus -ProcessExit $exit -RunnerText $text -CaptureOnly ([bool]$CaptureOnly)
        $summary.Add([pscustomobject]@{
            ModelId = $modelId
            Backend = $kind
            Language = $language
            Cycles = if ($ResidentTurns -gt 0) { 0 } else { $Cycles }
            ResidentTurns = $ResidentTurns
            ContextTokens = if ($ContextTokens -gt 0) { $ContextTokens } else { "PROFILE" }
            Result = $status
            ProcessExit = $exit
            OutputFile = "$label.txt"
        })
        Save-MatrixSummary
        Write-Host "  $status; inspect $label.txt"
        $label=$labelBase
        }
    }
}
} finally {
Save-MatrixSummary

# App is debuggable for androidTest. Reading the already-produced evidence does not
# mutate settings, download models or allocate another inference engine.
$jsonlFile = if ($RoleSuite) { 'local-model-role-evidence.jsonl' }
             elseif ($InspectOnly) { "local-artifact-inspection.jsonl" }
             elseif ($ResidentTurns -gt 0) { "resident-model-endurance.jsonl" }
             else { "downloaded-model-diagnostics.jsonl" }
function Save-RunEvidence([string] $Filename) {
    $lines = & $Adb @deviceArgs shell run-as $packageName cat "files/$Filename" 2>&1
    if ($LASTEXITCODE -ne 0) { Write-Warning "Could not read $Filename; runner logs and CSV remain available."; return }
    $lines | Set-Content -LiteralPath (Join-Path $OutputDir ($Filename + ".raw")) -Encoding utf8
    $matching = @($lines | ForEach-Object {
        try {
            $row = $_ | ConvertFrom-Json -ErrorAction Stop
            if ($row.runId -eq $runId -and $row.sourceStamp -eq $inventory.sourceStamp -and
                @($selected | Where-Object { $_.id -eq $row.modelId -and $_.sha256 -eq $row.sha256 }).Count -eq 1) { $_ }
        } catch { }
    })
    [System.IO.File]::WriteAllLines((Join-Path (Resolve-Path $OutputDir) $Filename), [string[]]$matching)
}
Save-RunEvidence $jsonlFile
if ($RoleSuite) {
    $reportPath = Join-Path $OutputDir 'suitability.csv'
    $evidencePath = Join-Path $OutputDir $jsonlFile
    $evidence = if (Test-Path -LiteralPath $evidencePath) { @(Get-Content -LiteralPath $evidencePath | ForEach-Object { try { $_ | ConvertFrom-Json } catch { } }) } else { @() }
    Get-LiteRtSuitabilityRows -Evidence $evidence -Selection $selected -Summary @($summary.ToArray()) -RunId $runId -SourceStamp $inventory.sourceStamp `
        -Backends $backends -Languages $Languages -Repetitions $Repetitions -ContextTokens $ContextTokens |
        Select-Object modelId,sha256,backend,effectiveBackend,language,role,grade,scope,passed,attempted,expected,medianAnswerMs,effectiveContext,stable,baselinePass,memoryPressure,sampledPeakPssKb,suiteFailure,selectionEvidenceSaved |
        Export-Csv -LiteralPath $reportPath -NoTypeInformation -Encoding utf8
}
if (-not $InspectOnly) { Save-RunEvidence "installed-model-footprints.jsonl" }

}
$summary | Format-Table -AutoSize
Write-Host "Saved evidence to: $OutputDir"
$completedStatuses = @('PASSED', 'CAPTURE_ONLY_NOT_QUALIFIED')
if ($RoleSuite) { $completedStatuses += 'SKIPPED_TASK_NOT_GENERATION' }
if (@($summary | Where-Object { $_.Result -notin $completedStatuses }).Count -gt 0) {
    throw "One or more physical probes failed or were interrupted/not run. See saved logs and JSONL; incomplete runs do not establish model or backend qualification."
}
if ($CaptureOnly) {
    Write-Warning "Capture-only sweep completed: instrumentation green does NOT imply raw/provider generation passed. Read rawPassed/providerPassed in JSONL."
}
