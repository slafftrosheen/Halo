# Pure protocol classification; no device access or model operations.
function Resolve-LiteRtProbeStatus {
    param([int] $ProcessExit, [string] $RunnerText, [bool] $CaptureOnly = $false)
    if ($ProcessExit -ne 0) { return "RUNNER_INTERRUPTED" }
    if ($RunnerText -match 'shortMsg=Process crashed|INSTRUMENTATION_FAILED:.*Process crashed') { return "FAILED_RUNTIME" }
    if ($RunnerText -notmatch 'INSTRUMENTATION_CODE:\s*-?\d+') { return "RUNNER_INTERRUPTED" }
    $passed = $RunnerText -match 'INSTRUMENTATION_CODE:\s*-1' -and $RunnerText -match 'OK\s*\(1\s*test\)'
    if (-not $passed) {
        if ($RunnerText -match 'Installed model task is non-generative|This artifact is not a language generation model') { return "SKIPPED_TASK_NOT_GENERATION" }
        if ($RunnerText -match 'Artifact fingerprint changed|Artifact is known non-runnable|verified installed snapshot|Installed artifact missing or invalid|Failed to open LiteRT-LM file|does not support (CPU|GPU) in this artifact|Requested context .* exceeds the artifact|failedStage=(COMPLETION|FIRST_TOKEN) type=unknown') { return "FAILED_MODEL" }
        return "FAILED_RUNTIME"
    }
    if ($CaptureOnly) { return "CAPTURE_ONLY_NOT_QUALIFIED" }
    return "PASSED"
}
