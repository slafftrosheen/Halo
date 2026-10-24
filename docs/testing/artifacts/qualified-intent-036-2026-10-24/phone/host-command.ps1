$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath 'C:\Users\slavd\HaloEco\Halo-issues'
$adbPath='C:\Users\slavd\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$serial='f01fe673'
$appId='com.aistudio.braintool.xkqzps'
$proofDir='C:\Users\slavd\HaloEco\verification-2026-10-24-intent-qualification-install'
if(Test-Path -LiteralPath $proofDir){throw 'Use a fresh install directory'}
$appFile='app/build/outputs/apk/debug/app-debug.apk'
$testFile='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
$rows=Get-Content -LiteralPath C:\Users\slavd\HaloEco\intent-qualification-source-identities.json -Raw|ConvertFrom-Json
foreach($row in $rows){if((Get-FileHash -LiteralPath $row.path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $row.sha256){throw 'Source changed since gate'}}
New-Item -ItemType Directory -Path $proofDir|Out-Null
$appInstall=& $adbPath -s $serial install -r $appFile 2>&1
$appExit=$LASTEXITCODE
$appInstall|Set-Content -LiteralPath "$proofDir/app-install.txt" -Encoding utf8
if($appExit -ne 0 -or ($appInstall|Out-String) -notmatch 'Success'){throw 'Production package installation failed'}
$install=& $adbPath -s $serial install -r -t $testFile 2>&1
$installExit=$LASTEXITCODE
$install|Set-Content -LiteralPath "$proofDir/test-install.txt" -Encoding utf8
if($installExit -ne 0 -or ($install|Out-String) -notmatch 'Success'){throw 'Test package installation failed'}
$identities=foreach($target in @(@{Id=$appId;File=$appFile},@{Id="$appId.test";File=$testFile})){
    $paths=@(& $adbPath -s $serial shell pm path $target.Id)
    if($LASTEXITCODE -ne 0 -or $paths.Count -ne 1 -or $paths[0] -notmatch '^package:(/data/app/[A-Za-z0-9_./~+=-]+/base\.apk)\s*$'){throw 'Unexpected installed package path'}
    $remotePath=$Matches[1]
    $hashText=(& $adbPath -s $serial shell sha256sum $remotePath|Out-String)
    if($LASTEXITCODE -ne 0 -or $hashText -notmatch '^([a-fA-F0-9]{64})\s+'){throw 'Installed package hash unavailable'}
    $actual=$Matches[1].ToLowerInvariant()
    $expected=(Get-FileHash -LiteralPath $target.File -Algorithm SHA256).Hash.ToLowerInvariant()
    if($actual -ne $expected){throw 'Installed hash mismatch'}
    [pscustomobject]@{package=$target.Id;sha256=$actual;bytes=(Get-Item -LiteralPath $target.File).Length;installedHashMatches=$true}
}
$identities|ConvertTo-Json|Set-Content -LiteralPath "$proofDir/apk-identities.json" -Encoding utf8
try{
    $outDir='C:/Users/slavd/HaloEco/verification-2026-10-24-intent-qualification'
    if(Test-Path -LiteralPath $outDir){throw 'Use a fresh native output directory'}
    New-Item -ItemType Directory -Path $outDir|Out-Null
    . ./tools/hil/LiteRtRoleEvidence.ps1
    $modelId='qwen3-0.6b-dynamic-int4'
    $inventory=& ./tools/hil/Get-InstalledLiteRtModels.ps1 -Adb $adbPath -Serial $serial -ModelIds @($modelId) -OutputDir "$outDir/inventory"
    if($inventory.status -ne 'INVENTORY_VERIFIED'){throw 'Verified inventory unavailable'}
    $models=@($inventory.models|Where-Object id -eq $modelId)
    if($models.Count -ne 1){throw 'Selected model descriptor unavailable'}
    $artifact=$models[0]
    if($artifact.sha256 -cnotmatch '^[a-f0-9]{64}$' -or "$($artifact.bytes)" -notmatch '^[0-9]+$'){throw 'Invalid selected artifact identity'}
    $runId=[Guid]::NewGuid().ToString('N')
    $arguments=@('-s',$serial,'shell','am','instrument','-w','-r','-e','class',
        'com.halo.app.audio.LocalIntentClassifierPhoneTest#unqualifiedWarmAdmissionNeverEnqueuesNativeWork',
        '-e','modelId',$modelId,'-e','backend','CPU','-e','expectedSha256',$artifact.sha256,
        '-e','expectedBytes',"$($artifact.bytes)",'-e','runId',$runId,'-e','sourceStamp',$inventory.sourceStamp,
        "$appId.test/androidx.test.runner.AndroidJUnitRunner")
    $invocation=Invoke-LiteRtBoundedProcess -Executable $adbPath -Arguments $arguments -TimeoutSeconds 240
    $invocation.Text|Set-Content "$outDir/admission-runner.txt" -Encoding utf8
    if($invocation.TimedOut){& $adbPath -s $serial shell am force-stop $appId|Out-Null;throw 'Admission fixture host timeout; no acceptance'}
    if($invocation.ExitCode -ne 0 -or $invocation.Text -notmatch 'INSTRUMENTATION_CODE:\s*-1' -or $invocation.Text -notmatch 'OK\s*\(1\s*test\)'){throw 'Admission fixture failed'}
    $raw=& $adbPath -s $serial exec-out run-as $appId cat files/intent-admission-acceptance.jsonl
    if($LASTEXITCODE -ne 0){throw 'Native admission evidence unavailable'}
    $admissionRows=@($raw|Where-Object { $row=$_|ConvertFrom-Json; $row.runId -eq $runId -and $row.sha256 -eq $artifact.sha256 -and $row.sourceStamp -eq $inventory.sourceStamp })
    if($admissionRows.Count -ne 1){throw 'Exact-run admission row unavailable'}
    $admissionRows[0]|Set-Content "$outDir/intent-admission-acceptance.jsonl" -Encoding utf8
    $row=$admissionRows[0]|ConvertFrom-Json
    if($row.scope -ne 'UNQUALIFIED_WARM_ADMISSION_ONLY' -or $row.blockedCases -ne 48 -or $row.generationQueueDelta -ne 0 -or
       $row.maximumCallerMs -gt 2000 -or $row.modelQuality -ne 'NOT_TESTED'){throw 'Admission evidence does not meet scope'}
    $before=@(Get-Content "$outDir/inventory/filenames.txt"|Where-Object{$_})
    $after=@(& $adbPath -s $serial shell run-as $appId ls -1 no_backup/models|Where-Object{$_})
    if($LASTEXITCODE -ne 0 -or (Compare-Object $before $after -CaseSensitive)){throw 'Installed model listing changed'}
    [pscustomobject]@{status='UNQUALIFIED_ADMISSION_PASSED';runId=$runId;sha256=$artifact.sha256;blockedCases=$row.blockedCases;
        generationQueueDelta=$row.generationQueueDelta;maximumCallerMs=$row.maximumCallerMs;modelQuality='NOT_TESTED'}|
        ConvertTo-Json|Tee-Object -FilePath "$outDir/summary.json"
}finally{
    # Only bounded, content-free runtime events; never export prompts/native error bodies.
    & $adbPath -s $serial logcat -d -v epoch -s LiteRtRuntime:I 2>&1 |
        Select-String 'HALO_LITERT_(GENERATION_START|CONVERSATION_READY|FIRST_TOKEN) ' |
        ForEach-Object { $_.Line } | Set-Content -LiteralPath "$proofDir/native-events-retained.txt" -Encoding utf8
    & $adbPath -s $serial shell am start -W -n "$appId/com.halo.app.MainActivity" 2>&1|Set-Content -LiteralPath "$proofDir/launch.txt" -Encoding utf8
    & $adbPath -s $serial shell dumpsys activity activities|Select-String 'mResumedActivity|topResumedActivity'|Set-Content -LiteralPath "$proofDir/resumed.txt" -Encoding utf8
}
