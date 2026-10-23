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

# Only classify a completed assertion from this exact fixture invocation. Crashes,
# incomplete instrumentation and mismatched/stale markers retain generic failure truth.
function Resolve-LiteRtEmbeddingProbeStatus {
    param([int] $ProcessExit, [string] $RunnerText, [string] $RunId, [string] $Sha256)
    $generic = Resolve-LiteRtProbeStatus -ProcessExit $ProcessExit -RunnerText $RunnerText
    if ($generic -eq 'PASSED') { return $generic }
    if ($ProcessExit -ne 0 -or $RunnerText -match 'shortMsg=Process crashed|INSTRUMENTATION_FAILED|HOST_TIMEOUT' -or
        $RunnerText -notmatch 'INSTRUMENTATION_CODE:\s*-1' -or $RunnerText -notmatch 'Tests run:\s*1,\s*Failures:\s*1') { return $generic }
    $marker = 'EMBEDDING_OUTCOME run=' + [regex]::Escape($RunId) + ' sha=' + [regex]::Escape($Sha256) +
        ' text=(PASSED|QUALITY_FAILED|UNAVAILABLE) image=(PASSED|QUALITY_FAILED|UNAVAILABLE)(?=\s|$)'
    if ($RunnerText -notmatch $marker) { return $generic }
    $statuses = @($Matches[1], $Matches[2])
    if ('QUALITY_FAILED' -in $statuses -and 'UNAVAILABLE' -in $statuses) { return 'QUALITY_FAILED_AND_UNAVAILABLE' }
    if ('QUALITY_FAILED' -in $statuses) { return 'QUALITY_FAILED' }
    if ('UNAVAILABLE' -in $statuses) { return 'NATIVE_UNAVAILABLE' }
    return $generic
}
