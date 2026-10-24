[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Seal','Install','Core','Inventory','Intent','Embedding','Roles')][string]$Phase,
    [string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$OutputDirectory,
    [string]$BuildLog,
    [string]$SourceWitness,
    [string[]]$Suites,
    [string]$ModelId,
    [string]$ExpectedSha256,
    [long]$ExpectedBytes,
    [ValidateSet('CPU','GPU')][string]$Backend = 'CPU',
    [ValidateSet('EN','RU','LV','PL')][string]$Language = 'EN'
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$packRoot = Join-Path $repoRoot 'docs\testing\open-issues-2026-10-24'
$manifestPath = Join-Path $packRoot 'build-manifest.json'
$packageId = 'com.aistudio.braintool.xkqzps'
$testPackageId = "$packageId.test"
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repoRoot ('.artifacts\open-issues-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)) }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if ((Test-Path -LiteralPath $OutputDirectory) -and @(Get-ChildItem -LiteralPath $OutputDirectory -Force).Count) {
    throw 'Choose a fresh empty evidence directory; never mix older runs with a new result'
}
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null

function Read-Json([string]$LiteralPath) { Get-Content -Raw -LiteralPath $LiteralPath | ConvertFrom-Json }
function Save-Json([string]$LiteralPath, $Value) { ConvertTo-Json -InputObject $Value -Depth 30 | Set-Content -Encoding utf8 -LiteralPath $LiteralPath }
function Hash([string]$LiteralPath) { (Get-FileHash -LiteralPath $LiteralPath -Algorithm SHA256).Hash.ToLowerInvariant() }
function Assert-Sources($Witnesses) {
    foreach ($witness in $Witnesses) {
        $path = [IO.Path]::GetFullPath((Join-Path $repoRoot $witness.path))
        if (-not $path.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Source path escapes repository' }
        if ((Hash $path) -ne $witness.sha256) { throw "Source changed since build: $($witness.path)" }
    }
}
function Invoke-Adb([string[]]$AdbArguments, [int]$TimeoutSeconds = 120) {
    $process = [Diagnostics.Process]::new()
    $process.StartInfo.FileName = $Adb
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.CreateNoWindow = $true
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    foreach ($argument in (@('-s', $Serial) + $AdbArguments)) { $process.StartInfo.ArgumentList.Add($argument) }
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $timedOut = -not $process.WaitForExit($TimeoutSeconds * 1000)
    if ($timedOut) { $process.Kill($true); $process.WaitForExit() }
    $result = [PSCustomObject]@{ exitCode=$process.ExitCode; timedOut=$timedOut; text=($stdout.Result + $stderr.Result) }
    $process.Dispose()
    return $result
}
function Require-Adb([string[]]$AdbArguments, [int]$TimeoutSeconds = 120) {
    $result = Invoke-Adb $AdbArguments $TimeoutSeconds
    if ($result.timedOut -or $result.exitCode -ne 0) { throw "ADB operation failed (exit=$($result.exitCode), timeout=$($result.timedOut))" }
    return $result.text
}

if ($Phase -eq 'Seal') {
    if (-not $BuildLog -or -not $SourceWitness) { throw 'Seal requires the successful build log and pre-build source witness' }
    $log = Get-Content -Raw -LiteralPath $BuildLog
    if ($log -notmatch 'BUILD SUCCESSFUL' -or $log -match 'BUILD FAILED') { throw 'Build success is unproven' }
    $source = Read-Json $SourceWitness
    Assert-Sources $source.files
    $appPath = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-debug.apk'
    $testPath = Join-Path $repoRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    foreach ($path in @($appPath, $testPath)) {
        if ((Get-Item -LiteralPath $path).LastWriteTimeUtc -lt ([datetime]$source.capturedAt).ToUniversalTime().AddSeconds(-2)) {
            throw 'An APK predates this source batch; do not seal stale packages'
        }
    }
    $sourceBytes = [Text.Encoding]::UTF8.GetBytes(($source | ConvertTo-Json -Depth 8 -Compress))
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { $stamp = [BitConverter]::ToString($hasher.ComputeHash($sourceBytes)).Replace('-','').ToLowerInvariant() }
    finally { $hasher.Dispose() }
    $manifest = [ordered]@{ schema=1; scope='DEBUG_BUILD_AND_NATIVE_FIXTURES_ONLY'; sourceBase=$source.baseline;
        sourceStamp=$stamp; sourceWitnesses=$source.files; sealedAt=(Get-Date -Format o); buildLogSha256=(Hash $BuildLog);
        app=@{path='app/build/outputs/apk/debug/app-debug.apk'; package=$packageId; sha256=(Hash $appPath)};
        test=@{path='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'; package=$testPackageId; sha256=(Hash $testPath)} }
    Save-Json $manifestPath $manifest
    Write-Output "Sealed current app/test APKs with source stamp $stamp"
    exit 0
}

if (-not $Serial -or $Serial -notmatch '^[A-Za-z0-9._:-]{1,100}$') { throw 'Choose an explicit ADB serial' }
if (-not (Test-Path -LiteralPath $Adb -PathType Leaf)) { throw 'ADB executable not found' }
$manifest = Read-Json $manifestPath
Assert-Sources $manifest.sourceWitnesses
foreach ($artifact in @($manifest.app, $manifest.test)) {
    if ((Hash (Join-Path $repoRoot $artifact.path)) -ne $artifact.sha256) { throw 'APK changed since sealing; rebuild/reseal deliberately' }
}
if ((Require-Adb @('get-state')).Trim() -ne 'device') { throw 'Selected phone is not online/authorized' }
$activeInstrumentation = Require-Adb @('shell','dumpsys','activity','instrumentation')
if ($activeInstrumentation -match [regex]::Escape($testPackageId)) {
    throw 'This test package already has active instrumentation. Confirm its actual teardown before another run or install.'
}
$environment = [ordered]@{serial=$Serial; model=(Require-Adb @('shell','getprop','ro.product.model')).Trim();
    api=(Require-Adb @('shell','getprop','ro.build.version.sdk')).Trim(); sourceStamp=$manifest.sourceStamp;
    appSha256=$manifest.app.sha256; testSha256=$manifest.test.sha256; at=(Get-Date -Format o)}
Save-Json (Join-Path $OutputDirectory 'environment.json') $environment

if ($Phase -eq 'Install') {
    foreach ($artifact in @($manifest.app, $manifest.test)) {
        $result = Require-Adb @('install','-r',(Join-Path $repoRoot $artifact.path)) 300
        if ($result -notmatch '(?m)^Success\s*$') { throw 'Package install was not confirmed; never uninstall user data to work around a signing error' }
    }
}

# Verify actual installed bytes before every independent execution phase, not only installer exit.
foreach ($artifact in @($manifest.app, $manifest.test)) {
    $packagePaths = Require-Adb @('shell','pm','path',$artifact.package)
    $baseLine = @($packagePaths -split "`r?`n" | Where-Object { $_ -match '^package:/data/app/.+/base\.apk$' })
    if ($baseLine.Count -ne 1) { throw 'Cannot identify one installed base APK' }
    $remotePath = $baseLine[0].Substring(8).Trim()
    $localPath = Join-Path $OutputDirectory ($artifact.package + '.apk')
    [void](Require-Adb @('pull',$remotePath,$localPath) 300)
    if ((Hash $localPath) -ne $artifact.sha256) { throw 'Installed package differs from the sealed source build' }
}
Save-Json (Join-Path $OutputDirectory 'installed-readback.json') $environment
if ($Phase -eq 'Install') {
    [void](Require-Adb @('shell','am','start','-W','-n',"$packageId/com.halo.app.MainActivity"))
    Write-Output "Installed and read back both exact packages on $Serial. App launched."
    exit 0
}

$runId = 'open-issues-' + [guid]::NewGuid().ToString('N')
$nativeSuites = @(Read-Json (Join-Path $packRoot 'native-suites.json'))
$jobs = @()
if ($Phase -eq 'Core') {
    $jobs = @($nativeSuites | Where-Object { -not $Suites -or $_.id -in $Suites })
    if (-not $jobs -or ($Suites -and @($Suites | Where-Object { $_ -notin $jobs.id }).Count)) { throw 'Unknown or empty native suite selection' }
    foreach ($suite in $jobs) { Assert-Sources $suite.sources }
} elseif ($Phase -eq 'Inventory') {
    $jobs = @([PSCustomObject]@{id='inventory';classes=@('com.halo.app.audio.InstalledLiteRtInventoryTest');expectedTests=1;timeoutSeconds=1800})
} else {
    if ($ModelId -notmatch '^[a-z0-9][a-z0-9._-]{1,99}$' -or $ExpectedSha256 -notmatch '^[a-f0-9]{64}$' -or $ExpectedBytes -le 0) {
        throw 'Model qualification requires one explicitly selected, verified installed model ID/SHA/byte length; no downloads or automatic retry'
    }
    $class = switch ($Phase) {
        'Intent' { 'com.halo.app.audio.LocalIntentClassifierPhoneTest#classifyPreparedFinalTextMatrix' }
        'Embedding' { if ($ModelId -ne 'embeddinggemma2-740m' -or $Backend -ne 'CPU') { throw 'Current embedding fixture is exact EmbeddingGemma2 CPU only' }; 'com.halo.app.audio.LocalEmbeddingPhoneTest#qualifyInstalledEmbedding' }
        'Roles' { 'com.halo.app.audio.LocalModelRolePhoneTest#qualifyInstalledRoles' }
    }
    $jobs = @([PSCustomObject]@{id=$Phase.ToLowerInvariant();classes=@($class);expectedTests=1;timeoutSeconds=3600})
}
$results = @()
$journalNames = @(switch ($Phase) {
    'Intent' { @('intent-model-acceptance.jsonl','installed-model-footprints.jsonl') }
    'Embedding' { @('local-embedding-evidence.jsonl','installed-model-footprints.jsonl') }
    'Roles' { @('local-model-role-evidence.jsonl','installed-model-footprints.jsonl') }
    'Inventory' { @('installed-litert-models.json') }
    default { @('note-purge-evidence.jsonl','note-attachment-controls-evidence.jsonl','note-editor-controls-evidence.jsonl','study-learner-controls-evidence.jsonl') }
})
foreach ($suite in $jobs) {
    $instrument = @('shell','am','instrument','-w','-r','-e','class',($suite.classes -join ','),'-e','runId',$runId,
        '-e','sourceStamp',$manifest.sourceStamp)
    if ($Phase -in @('Intent','Embedding','Roles')) {
        $instrument += @('-e','modelId',$ModelId,'-e','expectedSha256',$ExpectedSha256,'-e','expectedBytes',$ExpectedBytes.ToString(),
            '-e','backend',$Backend,'-e','language',$Language,'-e','repetitions','3')
    }
    $instrument += "$testPackageId/androidx.test.runner.AndroidJUnitRunner"
    Write-Output "Running $($suite.id) ($($suite.expectedTests) expected native fixture cases)…"
    $started = Get-Date
    $startedEpochMs = [DateTimeOffset]::new($started).ToUnixTimeMilliseconds()
    $result = Invoke-Adb $instrument $suite.timeoutSeconds
    $logPath = Join-Path $OutputDirectory ($suite.id + '.txt')
    [IO.File]::WriteAllText($logPath, $result.text)
    $match = [regex]::Match($result.text, 'OK \((\d+) tests?\)')
    $count = if ($match.Success) { [int]$match.Groups[1].Value } else { 0 }
    $failed = $result.timedOut -or $result.exitCode -ne 0 -or $count -ne $suite.expectedTests -or
        $result.text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|INSTRUMENTATION_STATUS_CODE: -[234]'
    $results += [PSCustomObject]@{id=$suite.id;status=$(if ($failed) {'FAIL'} else {'PASS'}); scope='NATIVE_FIXTURE_ONLY';
        expectedTests=$suite.expectedTests; observedTests=$count; runId=$runId; sourceStamp=$manifest.sourceStamp;
        elapsedSeconds=((Get-Date)-$started).TotalSeconds; logSha256=(Hash $logPath); log=[IO.Path]::GetFileName($logPath)}
    Save-Json (Join-Path $OutputDirectory 'automated-results.json') $results
    Write-Output "$($suite.id): $($results[-1].status), $count/$($suite.expectedTests) cases"
    if ($result.timedOut) {
        Save-Json (Join-Path $OutputDirectory 'execution-state.json') ([ordered]@{
            status='INCOMPLETE_REMOTE_STATE_UNVERIFIED'; suite=$suite.id; runId=$runId;
            sourceStamp=$manifest.sourceStamp; testPackage=$testPackageId; serial=$Serial;
            reason='Only the local ADB client timed out; Android instrumentation termination is unproven' })
        throw 'Instrumentation timed out. No further suite was started. Confirm actual Android teardown before resuming.'
    }
}
$journalIndex = @()
foreach ($journalName in $journalNames) {
    $journal = Invoke-Adb @('exec-out','run-as',$packageId,'cat',"files/$journalName") 120
    if ($journal.exitCode -ne 0 -or $journal.timedOut) { continue }
    $acceptedRows = @()
    if ($journalName.EndsWith('.jsonl')) {
        foreach ($line in ($journal.text -split "`r?`n")) {
            if (-not $line.Trim()) { continue }
            try { $row = $line | ConvertFrom-Json } catch { continue }
            if ($row.runId -eq $runId -and $row.sourceStamp -eq $manifest.sourceStamp) { $acceptedRows += $line }
        }
    } else {
        try {
            $row = $journal.text | ConvertFrom-Json
            if ($row.sourceStamp -eq $manifest.sourceStamp -and $row.capturedAtMs -ge $startedEpochMs) { $acceptedRows += $journal.text }
        } catch { }
    }
    if ($acceptedRows.Count) {
        $journalPath = Join-Path $OutputDirectory $journalName
        [IO.File]::WriteAllText($journalPath, ($acceptedRows -join "`n") + "`n")
        $journalIndex += [PSCustomObject]@{path=$journalName; sha256=(Hash $journalPath); rows=$acceptedRows.Count; runId=$runId; sourceStamp=$manifest.sourceStamp}
    }
}
Save-Json (Join-Path $OutputDirectory 'journal-index.json') $journalIndex
if ($Phase -in @('Intent','Embedding','Roles','Inventory') -and -not @($journalIndex | Where-Object path -eq $journalNames[0]).Count) {
    throw 'No exact current-run/source-bound native journal was exported; model/inventory acceptance is unproven'
}
Write-Output "Evidence saved to $OutputDirectory. Human, spoken, model-quality, second-host and Play acceptance remain separate."
if (@($results | Where-Object status -ne 'PASS').Count) { exit 1 }
