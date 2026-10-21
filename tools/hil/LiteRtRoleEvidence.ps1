# Completed semantic failures are evidence too. Partial/stale rows never support a recommendation.
function Test-LiteRtRoleEvidence {
    param($Row, $Identity)
    if ($Row.stage -ne 'COMPLETED' -or $Row.fixtureSuite -ne 1) { return $false }
    foreach ($field in @('runId','sourceStamp','modelId','sha256','backend','language','repetitions','requestedContext')) {
        if ([string]$Row.$field -cne [string]$Identity.$field) { return $false }
    }
    return $Row.role -in @('FAST','CHAT','REASONING','AGENTIC','VISION') -and
        $Row.grade -in @('GOOD','SUITABLE','NOT_SUITABLE','NOT_TESTED','UNSUPPORTED')
}

function Invoke-LiteRtBoundedProcess {
    param([string] $Executable, [string[]] $Arguments, [int] $TimeoutSeconds)
    $info = [System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Executable
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        $null = $process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $deadline = [System.Diagnostics.Stopwatch]::StartNew()
        $timedOut = $false
        while (-not $process.WaitForExit(250)) {
            if ($deadline.Elapsed.TotalSeconds -ge $TimeoutSeconds) {
                $timedOut = $true
                $process.Kill($true)
                $process.WaitForExit()
                break
            }
        }
        [pscustomobject]@{ ExitCode=$(if ($timedOut) {124} else {$process.ExitCode});
            TimedOut=$timedOut; Text=($stdout.GetAwaiter().GetResult() + "`n" + $stderr.GetAwaiter().GetResult()) }
    } finally { $process.Dispose() }
}

function Get-LiteRtSuitabilityRows {
    param([object[]] $Evidence, [object[]] $Selection, [object[]] $Summary,
        [string] $RunId, [string] $SourceStamp, [string[]] $Backends, [string[]] $Languages,
        [int] $Repetitions, [int] $ContextTokens)
    foreach ($artifact in $Selection) {
        foreach ($backend in $Backends) {
            foreach ($language in $Languages) {
                $identity = [pscustomobject]@{ runId=$RunId; sourceStamp=$SourceStamp; modelId=$artifact.id; sha256=$artifact.sha256;
                    backend=$backend; language=$language; repetitions=$Repetitions; requestedContext=$ContextTokens }
                $completed = @($Evidence | Where-Object { Test-LiteRtRoleEvidence $_ $identity })
                $runnerPassed = @($Summary | Where-Object { $_.ModelId -ceq $artifact.id -and $_.Backend -ceq $backend -and $_.Language -ceq $language -and $_.Result -eq 'PASSED' }).Count -gt 0
                foreach ($role in @('FAST','CHAT','REASONING','AGENTIC','VISION')) {
                    $measured = $completed | Where-Object role -eq $role | Sort-Object { [long]$_.atMs } -Descending | Select-Object -First 1
                    if ($null -ne $measured -and $runnerPassed) { $measured; continue }
                    [pscustomobject]@{ modelId=$artifact.id; sha256=$artifact.sha256; backend=$backend; effectiveBackend=$null;
                        language=$language; role=$role; grade=$(if ($artifact.declaredTask -eq 'EMBEDDING') {'UNSUPPORTED'} else {'NOT_TESTED'});
                        scope='CONTROLLED_FIXTURES_ONLY'; passed=0; attempted=0; expected=$null; medianAnswerMs=$null;
                        effectiveContext=$null; suiteFailure='NO_COMPLETED_MATCHING_RUN'; selectionEvidenceSaved=$false }
                }
            }
        }
    }
}
