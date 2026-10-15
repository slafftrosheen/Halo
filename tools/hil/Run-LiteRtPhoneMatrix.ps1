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
    [ValidateSet(0, 1024, 2048, 4096, 8192)]
    [int] $ContextTokens = 0,
    [switch] $CaptureOnly,
    [string] $OutputDir = ("litert-phone-matrix-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "LiteRtProbeStatus.ps1")
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
$testClass = if ($InspectOnly) {
    "com.halo.app.audio.LocalArtifactInspectionTest#inspectInstalledArtifact"
} elseif ($ResidentTurns -gt 0) {
    "com.halo.app.audio.DownloadedModelDiagnosticsTest#diagnoseResidentEndurance"
} else {
    "com.halo.app.audio.DownloadedModelDiagnosticsTest#diagnoseInstalledModel"
}
$backends = if ($InspectOnly) { @("INFO") } elseif ($Backend -eq "BOTH") { @("CPU", "GPU") } else { @($Backend) }
if (Test-Path -LiteralPath (Join-Path $OutputDir "selected-installed-models.json")) { throw "Use a fresh matrix OutputDir; the selection is immutable." }
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$summary = [System.Collections.Generic.List[object]]::new()
$inventory = & (Join-Path $PSScriptRoot "Get-InstalledLiteRtModels.ps1") -Serial $Serial -Adb $Adb -OutputDir (Join-Path $OutputDir "inventory")
if ($inventory.status -ne "INVENTORY_VERIFIED") {
    [pscustomobject]@{ Result=$inventory.status; Serial=$Serial; Reason=$inventory.reason } | Export-Csv (Join-Path $OutputDir "summary.csv") -NoTypeInformation
    if ($inventory.status -ne "NOT_RUN_DEVICE_UNAVAILABLE") { throw "Installed inventory was interrupted; no artifact is selected." }
    return
}
$eligible = @($inventory.models | Where-Object { $_.verified -eq $true -and $_.runnable -eq $true -and $_.sha256 -match '^[0-9a-f]{64}$' })
if ($InstalledOnly) { $Models = @($eligible | Sort-Object bytes | ForEach-Object { $_.id }) }
$selected = @($eligible | Where-Object { $_.id -in $Models })
$runId = [guid]::NewGuid().ToString("N")
$selectionFile = Join-Path $OutputDir "selected-installed-models.json"
if (Test-Path -LiteralPath $selectionFile) { throw "This run already has an immutable selection. Use a new OutputDir." }
[pscustomobject]@{ schema=1; serial=$Serial; runId=$runId; sourceStamp=$inventory.sourceStamp; capturedAtMs=$inventory.capturedAtMs; models=$selected } |
    ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $selectionFile -Encoding utf8
(Get-Item -LiteralPath $selectionFile).IsReadOnly = $true
if ($Models.Count -eq 0) {
    $emptyStatus = if (@($inventory.models).Count -eq 0) { "SKIPPED_NOT_INSTALLED" } else { "FAILED_MODEL" }
    [pscustomobject]@{ ModelId=""; Backend=$Backend; Result=$emptyStatus; Reason="NO_VERIFIED_RUNNABLE_INSTALLED_ARTIFACTS" } |
        Export-Csv -LiteralPath (Join-Path $OutputDir "summary.csv") -NoTypeInformation -Encoding utf8
    if ($emptyStatus -eq "FAILED_MODEL") { throw "Installed artifacts did not pass admission; no engine was started." }
    Write-Host "SKIPPED_NOT_INSTALLED: no installed artifact is available for this run."
    return
}
$initialFilenames = @(Get-Content (Join-Path $OutputDir "inventory/filenames.txt") | Where-Object { $_.Length -gt 0 })
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
        Write-Host "LiteRT physical probe: $label (captureOnly=$CaptureOnly)"
        $deviceState = & $Adb @deviceArgs get-state 2>&1
        $deviceReady = $LASTEXITCODE -eq 0 -and (($deviceState | Out-String).Trim() -eq "device")
        if (-not $deviceReady) {
            ($deviceState | Out-String) | Set-Content -Path (Join-Path $OutputDir "$label.txt") -Encoding UTF8
            $summary.Add([pscustomobject]@{
                ModelId = $modelId; Backend = $kind
                Cycles = if ($ResidentTurns -gt 0) { 0 } else { $Cycles }
                ResidentTurns = $ResidentTurns
                ContextTokens = if ($ContextTokens -gt 0) { $ContextTokens } else { "PROFILE" }
                Result = "NOT_RUN_DEVICE_UNAVAILABLE"; ProcessExit = $LASTEXITCODE
                OutputFile = "$label.txt"
            })
            Write-Host "  NOT_RUN_DEVICE_UNAVAILABLE; reconnect the phone before retrying."
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
        $parameters += $testRunner
        $output = & $Adb @deviceArgs @parameters 2>&1 | Tee-Object -FilePath (Join-Path $OutputDir "$label.txt")
        $exit = $LASTEXITCODE
        $text = ($output | Out-String)
        $text | Set-Content -Path (Join-Path $OutputDir "$label.txt") -Encoding UTF8
        $instrumentOk = $exit -eq 0 -and $text -match 'INSTRUMENTATION_CODE:\s*-1' -and
            $text -match 'OK\s*\(1\s*test\)'
        $status = Resolve-LiteRtProbeStatus -ProcessExit $exit -RunnerText $text -CaptureOnly ([bool]$CaptureOnly)
        $summary.Add([pscustomobject]@{
            ModelId = $modelId
            Backend = $kind
            Cycles = if ($ResidentTurns -gt 0) { 0 } else { $Cycles }
            ResidentTurns = $ResidentTurns
            ContextTokens = if ($ContextTokens -gt 0) { $ContextTokens } else { "PROFILE" }
            Result = $status
            ProcessExit = $exit
            OutputFile = "$label.txt"
        })
        Write-Host "  $status; inspect $label.txt"
    }
}
} finally {
$summary | Export-Csv -Path (Join-Path $OutputDir "summary.csv") -NoTypeInformation -Encoding UTF8

# App is debuggable for androidTest. Reading the already-produced evidence does not
# mutate settings, download models or allocate another inference engine.
$jsonlFile = if ($InspectOnly) { "local-artifact-inspection.jsonl" }
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
if (-not $InspectOnly) { Save-RunEvidence "installed-model-footprints.jsonl" }

}
$summary | Format-Table -AutoSize
Write-Host "Saved evidence to: $OutputDir"
if (@($summary | Where-Object { $_.Result -notin @("PASSED", "CAPTURE_ONLY_NOT_QUALIFIED") }).Count -gt 0) {
    throw "One or more physical probes failed or were interrupted/not run. See saved logs and JSONL; incomplete runs do not establish model or backend qualification."
}
if ($CaptureOnly) {
    Write-Warning "Capture-only sweep completed: instrumentation green does NOT imply raw/provider generation passed. Read rawPassed/providerPassed in JSONL."
}
