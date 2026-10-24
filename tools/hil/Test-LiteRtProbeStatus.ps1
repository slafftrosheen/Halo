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

$runId = 'fixture-run-035'
$sha = 'a' * 64
$failureEnd = "`nFAILURES!!!`nTests run: 1, Failures: 1`nINSTRUMENTATION_CODE: -1"
$quality = "EMBEDDING_OUTCOME run=$runId sha=$sha text=QUALITY_FAILED image=PASSED"
$unavailable = "EMBEDDING_OUTCOME run=$runId sha=$sha text=PASSED image=UNAVAILABLE"
$mixed = "EMBEDDING_OUTCOME run=$runId sha=$sha text=QUALITY_FAILED image=UNAVAILABLE"
$embeddingCases = @(
    @{ Exit=0; Text=$success; Expected='PASSED' },
    @{ Exit=0; Text=($quality + $failureEnd); Expected='QUALITY_FAILED' },
    @{ Exit=0; Text=($unavailable + $failureEnd); Expected='NATIVE_UNAVAILABLE' },
    @{ Exit=0; Text=($mixed + $failureEnd); Expected='QUALITY_FAILED_AND_UNAVAILABLE' },
    @{ Exit=1; Text=($quality + $failureEnd); Expected='RUNNER_INTERRUPTED' },
    @{ Exit=0; Text=$quality; Expected='RUNNER_INTERRUPTED' },
    @{ Exit=0; Text=($quality + $failureEnd + "`nshortMsg=Process crashed"); Expected='FAILED_RUNTIME' },
    @{ Exit=0; Text=($quality + $failureEnd + "`nHOST_TIMEOUT"); Expected='FAILED_RUNTIME' },
    @{ Exit=0; Text=($quality.Replace($runId, 'stale') + $failureEnd); Expected='FAILED_RUNTIME' },
    @{ Exit=0; Text=($quality.Replace($sha, ('b' * 64)) + $failureEnd); Expected='FAILED_RUNTIME' },
    @{ Exit=0; Text=($quality.Replace('QUALITY_FAILED', 'UNKNOWN') + $failureEnd); Expected='FAILED_RUNTIME' }
)
foreach ($case in $embeddingCases) {
    $actual = Resolve-LiteRtEmbeddingProbeStatus -ProcessExit $case.Exit -RunnerText $case.Text -RunId $runId -Sha256 $sha
    if ($actual -ne $case.Expected) { throw "Expected embedding $($case.Expected), got $actual" }
}
Write-Host 'PASS: 11 exact-run embedding protocol cases'

$intent = "INTENT_OUTCOME run=$runId sha=$sha result=FIXTURE_OR_LATENCY_MISSES"
$intentNotTested = "INTENT_OUTCOME run=$runId sha=$sha result=NOT_TESTED"
$intentPrerequisite = "INTENT_PREREQUISITE run=$runId sha=$sha reason=CPU_CONFIGURATION"
$intentCases = @(
    @{Exit=0;Text=$success;Expected='PASSED'},
    @{Exit=0;Text=($intent+$failureEnd);Expected='INTENT_FIXTURE_OR_LATENCY_MISSES'},
    @{Exit=0;Text=($intentNotTested+$failureEnd);Expected='INTENT_NOT_QUALIFIED'},
    @{Exit=1;Text=($intent+$failureEnd);Expected='RUNNER_INTERRUPTED'},
    @{Exit=0;Text=$intent;Expected='RUNNER_INTERRUPTED'},
    @{Exit=0;Text=($intent+$failureEnd+"`nshortMsg=Process crashed");Expected='FAILED_RUNTIME'},
    @{Exit=0;Text=($intent+$failureEnd+"`nHOST_TIMEOUT");Expected='FAILED_RUNTIME'},
    @{Exit=0;Text=($intent+$failureEnd+"`nNATIVE_TEARDOWN_PENDING");Expected='FAILED_RUNTIME'},
    @{Exit=0;Text=($intent.Replace($runId,'stale')+$failureEnd);Expected='FAILED_RUNTIME'},
    @{Exit=0;Text=($intent.Replace($sha,('b'*64))+$failureEnd);Expected='FAILED_RUNTIME'},
    @{Exit=0;Text=($intent.Replace('FIXTURE_OR_LATENCY_MISSES','UNKNOWN')+$failureEnd);Expected='FAILED_RUNTIME'}
    @{Exit=0;Text=($intentPrerequisite+$failureEnd);Expected='INTENT_PREREQUISITE_UNMET'},
    @{Exit=0;Text=($intentPrerequisite.Replace('CPU_CONFIGURATION','CONTEXT_CONFIGURATION')+$failureEnd);Expected='INTENT_PREREQUISITE_UNMET'},
    @{Exit=0;Text=($intentPrerequisite.Replace($runId,'stale')+$failureEnd);Expected='FAILED_RUNTIME'},
    @{Exit=0;Text=($intentPrerequisite+$failureEnd+"`nHOST_TIMEOUT");Expected='FAILED_RUNTIME'}
)
foreach($case in $intentCases){
    $actual=Resolve-LiteRtIntentProbeStatus -ProcessExit $case.Exit -RunnerText $case.Text -RunId $runId -Sha256 $sha
    if($actual -ne $case.Expected){throw "Expected intent $($case.Expected), got $actual"}
}
Write-Host 'PASS: 15 exact-run warm intent protocol cases'
