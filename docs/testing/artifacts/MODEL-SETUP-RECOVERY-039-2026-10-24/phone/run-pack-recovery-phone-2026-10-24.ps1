$ErrorActionPreference='Stop'
Set-Location -LiteralPath 'C:\Users\slavd\HaloEco\Halo-issues'
$adbPath='C:\Users\slavd\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$serial='f01fe673'
$appId='com.aistudio.braintool.xkqzps'
$proofDir='C:\Users\slavd\HaloEco\verification-2026-10-24-pack-recovery'
if(Test-Path -LiteralPath $proofDir){throw 'Use fresh evidence directory'}
$sources=Get-Content -LiteralPath C:\Users\slavd\HaloEco\pack-recovery-source-identities.json -Raw|ConvertFrom-Json
foreach($source in $sources){if((Get-FileHash -LiteralPath $source.path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $source.sha256){throw 'Source changed since gate'}}
New-Item -ItemType Directory -Path $proofDir|Out-Null
try {
    $identities=foreach($target in @(@{Id=$appId;File='app/build/outputs/apk/debug/app-debug.apk'},@{Id="$appId.test";File='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'})) {
        $installed=& $adbPath -s $serial install -r -t $target.File 2>&1
        $installExit=$LASTEXITCODE
        $installed|Set-Content -LiteralPath "$proofDir/$($target.Id)-install.txt" -Encoding utf8
        if($installExit -ne 0 -or ($installed|Out-String) -notmatch 'Success'){throw 'Package installation failed'}
        $paths=@(& $adbPath -s $serial shell pm path $target.Id)
        if($LASTEXITCODE -ne 0 -or $paths.Count -ne 1 -or $paths[0] -notmatch '^package:(/data/app/[A-Za-z0-9_./~+=-]+/base\.apk)\s*$'){throw 'Installed package path unavailable'}
        $remotePath=$Matches[1]
        $hashText=(& $adbPath -s $serial shell sha256sum $remotePath|Out-String)
        if($LASTEXITCODE -ne 0 -or $hashText -notmatch '^([a-fA-F0-9]{64})\s+'){throw 'Installed SHA unavailable'}
        $actual=$Matches[1].ToLowerInvariant()
        $expected=(Get-FileHash -LiteralPath $target.File -Algorithm SHA256).Hash.ToLowerInvariant()
        if($actual -ne $expected){throw 'Installed package SHA mismatch'}
        [pscustomobject]@{package=$target.Id;sha256=$actual;bytes=(Get-Item -LiteralPath $target.File).Length;installedHashMatches=$true}
    }
    $identities|ConvertTo-Json|Set-Content -LiteralPath "$proofDir/apk-identities.json" -Encoding utf8
    $sourceStamp=((& git rev-parse HEAD|Out-String).Trim())+'-dirty-'+(($identities|ForEach-Object sha256)-join '-')
    $runId=[Guid]::NewGuid().ToString('N')
    . ./tools/hil/LiteRtRoleEvidence.ps1
    $arguments=@('-s',$serial,'shell','am','instrument','-w','-r','-e','class','com.halo.app.ai.LocalRolePackRecoveryDeviceTest#damagedRecordCanBeRecoveredWithoutAcquisitionOrFileRemoval',
        '-e','runId',$runId,'-e','sourceStamp',$sourceStamp,"$appId.test/androidx.test.runner.AndroidJUnitRunner")
    $invocation=Invoke-LiteRtBoundedProcess -Executable $adbPath -Arguments $arguments -TimeoutSeconds 240
    $invocation.Text|Set-Content -LiteralPath "$proofDir/recovery-runner.txt" -Encoding utf8
    if($invocation.TimedOut){& $adbPath -s $serial shell am force-stop $appId|Out-Null;throw 'Host timeout; no native acceptance'}
    if($invocation.ExitCode -ne 0 -or $invocation.Text -notmatch 'INSTRUMENTATION_CODE:\s*-1' -or $invocation.Text -notmatch 'OK\s*\(1\s*test\)'){throw 'Native recovery fixture failed'}
    $raw=& $adbPath -s $serial exec-out run-as $appId cat files/model-setup-recovery-acceptance.jsonl
    if($LASTEXITCODE -ne 0){throw 'Native recovery evidence unavailable'}
    $rows=@($raw|Where-Object{$item=$_|ConvertFrom-Json; $item.runId -eq $runId -and $item.sourceStamp -eq $sourceStamp})
    if($rows.Count -ne 1){throw 'Exact-run evidence unavailable'}
    $rows[0]|Set-Content -LiteralPath "$proofDir/recovery-acceptance.jsonl" -Encoding utf8
    $row=$rows[0]|ConvertFrom-Json
    if($row.scope -ne 'MODEL_SETUP_SHARED_PREFS_OWNER_RECREATION' -or !$row.durableRecordCleared -or !$row.retainedFilesAndChoices -or $row.acquisitionCalls -ne 0 -or $row.fixtureUsesGlobalSetupOrSelection -ne $false -or $row.modelQualification -ne 'NOT_TESTED'){throw 'Native evidence scope mismatch'}
    [pscustomobject]@{status='ISOLATED_RECOVERY_PASSED';runId=$runId;durableRecordCleared=$row.durableRecordCleared;retainedFilesAndChoices=$row.retainedFilesAndChoices;acquisitionCalls=$row.acquisitionCalls;modelQualification=$row.modelQualification}|
        ConvertTo-Json|Tee-Object -FilePath "$proofDir/summary.json"
} finally {
    & $adbPath -s $serial shell am start -W -n "$appId/com.halo.app.MainActivity" 2>&1|Set-Content -LiteralPath "$proofDir/launch.txt" -Encoding utf8
    & $adbPath -s $serial shell dumpsys activity activities|Select-String 'mResumedActivity|topResumedActivity'|Set-Content -LiteralPath "$proofDir/resumed.txt" -Encoding utf8
}
