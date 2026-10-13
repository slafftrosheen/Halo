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
  Default is a one-model CPU smoke test, deliberately safe for the 4/6 GB lane.
  -InspectOnly reads the actual LiteRT ModelInfo task/modalities/backends without
  initializing a generation engine. It can still temporarily allocate native memory.
  Failed native probes are FAILED by default. -CaptureOnly records expected failures
  without treating them as accelerator qualification. Never downloads or selects models.
  The app:testDebugAndroidTest APK must be installed separately.
.EXAMPLE
  .\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Serial YOUR_ADB_ID
.EXAMPLE
  .\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Models qwen3-0.6b-dynamic-int4 -Backend CPU -ResidentTurns 5 -ContextTokens 2048
#>
[CmdletBinding()]
param(
    [string] $Serial = "",
    [string[]] $Models = @("qwen3-0.6b-dynamic-int4"),
    [ValidateSet("CPU", "GPU", "BOTH")]
    [string] $Backend = "CPU",
    [ValidateRange(1, 5)]
    [int] $Cycles = 1,
    [ValidateRange(0, 10)]
    [int] $ResidentTurns = 0,
    [switch] $InspectOnly,
    [ValidateSet(0, 1024, 2048, 4096, 8192)]
    [int] $ContextTokens = 0,
    [switch] $CaptureOnly,
    [string] $OutputDir = ("litert-phone-matrix-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
)
$ErrorActionPreference = "Stop"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb is not in PATH. Install Android platform-tools and connect the test phone."
}
$deviceArgs = @()
if ($Serial.Trim()) { $deviceArgs = @("-s", $Serial.Trim()) }
$packageName = "com.aistudio.braintool.xkqzps"
$testRunner = "$packageName.test/androidx.test.runner.AndroidJUnitRunner"
if ($InspectOnly -and ($ResidentTurns -gt 0 -or $Cycles -ne 1 -or $ContextTokens -gt 0)) {
    throw "-InspectOnly reads metadata; do not combine with context, cycles or resident generation."
}
if ($ResidentTurns -eq 1) { throw "-ResidentTurns must be 0 (fresh engines) or 2..10 (one resident engine)." }
if ($ResidentTurns -gt 0 -and $Cycles -ne 1) { throw "Choose either -ResidentTurns or -Cycles, not both." }
$testClass = if ($InspectOnly) {
    "com.halo.app.audio.LocalArtifactInspectionTest#inspectInstalledArtifact"
} elseif ($ResidentTurns -gt 0) {
    "com.halo.app.audio.DownloadedModelDiagnosticsTest#diagnoseResidentEndurance"
} else {
    "com.halo.app.audio.DownloadedModelDiagnosticsTest#diagnoseInstalledModel"
}
$backends = if ($InspectOnly) { @("INFO") } elseif ($Backend -eq "BOTH") { @("CPU", "GPU") } else { @($Backend) }
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$summary = [System.Collections.Generic.List[object]]::new()

foreach ($modelId in $Models) {
    if ($modelId -notmatch '^[a-z0-9][a-z0-9._-]{1,99}$') { throw "Unsafe model ID: $modelId" }
    foreach ($kind in $backends) {
        $label = if ($InspectOnly) { "$modelId-metadata" }
                 elseif ($ResidentTurns -gt 0) { "$modelId-$kind-ctx$ContextTokens-resident$ResidentTurns" }
                 else { "$modelId-$kind-ctx$ContextTokens-cycles$Cycles" }
        Write-Host "LiteRT physical probe: $label (captureOnly=$CaptureOnly)"
        $deviceState = & adb @deviceArgs get-state 2>&1
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
        $parameters = @(
            "shell", "am", "instrument", "-w", "-r",
            "-e", "class", $testClass,
            "-e", "modelId", $modelId
        )
        if (-not $InspectOnly) {
            $parameters += @("-e", "backend", $kind, "-e", "cycles", "$Cycles")
        }

        if ($ResidentTurns -gt 0) { $parameters += @("-e", "residentTurns", "$ResidentTurns") }
        if ($ContextTokens -gt 0) { $parameters += @("-e", "contextTokens", "$ContextTokens") }
        if ($CaptureOnly) { $parameters += @("-e", "captureOnly", "true") }
        $parameters += $testRunner
        $output = & adb @deviceArgs @parameters 2>&1
        $exit = $LASTEXITCODE
        $text = ($output | Out-String)
        $text | Set-Content -Path (Join-Path $OutputDir "$label.txt") -Encoding UTF8
        $instrumentOk = $exit -eq 0 -and $text -match 'INSTRUMENTATION_CODE:\s*-1' -and
            $text -match 'OK\s*\(1\s*test\)'
        $status = if ($exit -ne 0 -and $text -notmatch 'INSTRUMENTATION_CODE:') {
            "RUNNER_INTERRUPTED"
        } elseif (-not $instrumentOk) { "FAILED" } elseif ($CaptureOnly) { "CAPTURE_ONLY_NOT_QUALIFIED" } else { "PASSED" }
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
$summary | Export-Csv -Path (Join-Path $OutputDir "summary.csv") -NoTypeInformation -Encoding UTF8

# App is debuggable for androidTest. Reading the already-produced evidence does not
# mutate settings, download models or allocate another inference engine.
$jsonlFile = if ($InspectOnly) { "local-artifact-inspection.jsonl" }
             elseif ($ResidentTurns -gt 0) { "resident-model-endurance.jsonl" }
             else { "downloaded-model-diagnostics.jsonl" }
$evidence = & adb @deviceArgs exec-out run-as $packageName cat "files/$jsonlFile" 2>&1
if ($LASTEXITCODE -eq 0) {
    ($evidence | Out-String) | Set-Content -Path (Join-Path $OutputDir $jsonlFile) -Encoding UTF8
} else {
    Write-Warning "Could not read in-app JSONL through adb run-as. Runner logs and CSV remain available."
}
$summary | Format-Table -AutoSize
Write-Host "Saved evidence to: $OutputDir"
if (@($summary | Where-Object { $_.Result -notin @("PASSED", "CAPTURE_ONLY_NOT_QUALIFIED") }).Count -gt 0) {
    throw "One or more physical probes failed or were interrupted/not run. See saved logs and JSONL; incomplete runs do not establish model or backend qualification."
}
if ($CaptureOnly) {
    Write-Warning "Capture-only sweep completed: instrumentation green does NOT imply raw/provider generation passed. Read rawPassed/providerPassed in JSONL."
}
