$ErrorActionPreference = 'Stop'
# Full app regression on the final 158-file frozen source after P05/P06 and phone acceptance.
$gradleArgs = @(':app:testDebugUnitTest', ':app:assembleDebug', ':app:compileDebugAndroidTestKotlin', '-Proborazzi.test.record=true', '--max-workers=1', '--no-daemon', '--no-parallel', '--console=plain', '--no-configuration-cache')
& .\gradlew.bat @gradleArgs *> (Join-Path $PSScriptRoot 'frozen-all-host.log')
$result = $LASTEXITCODE
Get-Content (Join-Path $PSScriptRoot 'frozen-all-host.log') -Tail 18
exit $result
