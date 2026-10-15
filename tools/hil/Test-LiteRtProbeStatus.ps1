$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "LiteRtProbeStatus.ps1")
$success = "OK (1 test)`nINSTRUMENTATION_CODE: -1"
$cases = @(
    @{ Exit=0; Text=$success; Expected="PASSED" },
    @{ Exit=0; Text="Failed to open LiteRT-LM file: model`nINSTRUMENTATION_CODE: -1"; Expected="FAILED_MODEL" },
    @{ Exit=0; Text="OutOfMemoryError at LocalArtifactInspectionTest`nINSTRUMENTATION_CODE: -1"; Expected="FAILED_RUNTIME" },
    @{ Exit=0; Text="failedStage=FIRST_TOKEN type=unknown`nINSTRUMENTATION_CODE: -1"; Expected="FAILED_MODEL" },
    @{ Exit=0; Text="failedStage=COMPLETION type=unknown`nINSTRUMENTATION_CODE: -1"; Expected="FAILED_MODEL" },
    @{ Exit=1; Text="INSTRUMENTATION_STATUS_CODE: 1"; Expected="RUNNER_INTERRUPTED" },
    @{ Exit=0; Text="INSTRUMENTATION_STATUS_CODE: 1"; Expected="RUNNER_INTERRUPTED" },
    @{ Exit=0; Text="INSTRUMENTATION_RESULT: shortMsg=Process crashed"; Expected="FAILED_RUNTIME" },
    @{ Exit=0; Text="Installed model task is non-generative`nINSTRUMENTATION_CODE: -1"; Expected="SKIPPED_TASK_NOT_GENERATION" },
    @{ Exit=0; Text="OK (0 tests)`nINSTRUMENTATION_CODE: -1"; Expected="FAILED_RUNTIME" }
)
foreach ($case in $cases) {
    $actual = Resolve-LiteRtProbeStatus -ProcessExit $case.Exit -RunnerText $case.Text
    if ($actual -ne $case.Expected) { throw "Expected $($case.Expected), got $actual" }
}
if ((Resolve-LiteRtProbeStatus -ProcessExit 0 -RunnerText $success -CaptureOnly $true) -ne "CAPTURE_ONLY_NOT_QUALIFIED") { throw "Capture-only run was promoted" }
Write-Host "PASS: 11 runner protocol cases"
