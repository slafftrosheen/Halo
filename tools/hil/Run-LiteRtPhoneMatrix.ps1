<#
.SYNOPSIS
  Explicit physical Android LiteRT diagnostic sweep for installed Halo artifacts.
.DESCRIPTION
  Runs one model/backend per instrumentation process, preserving the runner exit truth.
  Failed native probes are FAILED by default. -CaptureOnly records expected failures
  without treating them as accelerator qualification. Never downloads or selects models.
  The app:testDebugAndroidTest APK must be installed separately.
.EXAMPLE
  .\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Serial YOUR_ADB_ID
.EXAMPLE
  .\tools\hil\Run-LiteRtPhoneMatrix.ps1 -Models qwen3-0.6b-dynamic-int4,smollm2-135m-instruct -CaptureOnly
#>
[CmdletBinding()]
param(
    [string] $Serial = "",
    [string[]] $Models = @(
        "qwen3-0.6b-dynamic-int4",
        "smollm2-135m-instruct",
        "qwen3-1.7b-dynamic-int4",
        "deepseek-r1-distill-qwen-1.5b-q8"
    ),
    [ValidateSet("CPU", "GPU", "BOTH")]
    [string] $Backend = "BOTH",
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
$testClass = "com.halo.app.audio.DownloadedModelDiagnosticsTest#diagnoseInstalledModel"
$backends = if ($Backend -eq "BOTH") { @("CPU", "GPU") } else { @($Backend) }
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$summary = [System.Collections.Generic.List[object]]::new()

foreach ($modelId in $Models) {
    if ($modelId -notmatch '^[a-z0-9][a-z0-9._-]{1,99}$') { throw "Unsafe model ID: $modelId" }
    foreach ($kind in $backends) {
        $label = "$modelId-$kind"
        Write-Host "LiteRT physical probe: $label (captureOnly=$CaptureOnly)"
        $parameters = @(
            "shell", "am", "instrument", "-w", "-r",
            "-e", "class", $testClass,
            "-e", "modelId", $modelId,
            "-e", "backend", $kind
        )
        if ($CaptureOnly) { $parameters += @("-e", "captureOnly", "true") }
        $parameters += $testRunner
        $output = & adb @deviceArgs @parameters 2>&1
        $exit = $LASTEXITCODE
        $text = ($output | Out-String)
        $text | Set-Content -Path (Join-Path $OutputDir "$label.txt") -Encoding UTF8
        $instrumentOk = $exit -eq 0 -and $text -match 'INSTRUMENTATION_CODE:\s*-1' -and
            $text -match 'OK\s*\(1\s*test\)'
        $status = if (-not $instrumentOk) { "FAILED" } elseif ($CaptureOnly) { "CAPTURE_ONLY_NOT_QUALIFIED" } else { "PASSED" }
        $summary.Add([pscustomobject]@{
            ModelId = $modelId
            Backend = $kind
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
$evidence = & adb @deviceArgs exec-out run-as $packageName cat files/downloaded-model-diagnostics.jsonl 2>&1
if ($LASTEXITCODE -eq 0) {
    ($evidence | Out-String) | Set-Content -Path (Join-Path $OutputDir "model-diagnostics.jsonl") -Encoding UTF8
} else {
    Write-Warning "Could not read in-app JSONL through adb run-as. Runner logs and CSV remain available."
}
$summary | Format-Table -AutoSize
Write-Host "Saved evidence to: $OutputDir"
if (@($summary | Where-Object { $_.Result -eq "FAILED" }).Count -gt 0) {
    throw "One or more physical probes failed. See the saved logs and JSONL; do not call those GPU models qualified."
}
if ($CaptureOnly) {
    Write-Warning "Capture-only sweep completed: instrumentation green does NOT imply raw/provider generation passed. Read rawPassed/providerPassed in JSONL."
}
