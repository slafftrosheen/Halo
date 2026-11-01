$ErrorActionPreference = 'Stop'
# Final broad regression after shared dialog-density and generated-catalog changes.
$gradleArgs = @(':app:testDebugUnitTest', ':app:assembleDebug', ':app:compileDebugAndroidTestKotlin', '-Proborazzi.test.record=true', '--max-workers=1', '--no-daemon', '--no-parallel', '--console=plain', '--no-configuration-cache')
& .\gradlew.bat @gradleArgs *> (Join-Path $PSScriptRoot 'final-all-host.log')
$result = $LASTEXITCODE
Get-Content (Join-Path $PSScriptRoot 'final-all-host.log') -Tail 18
exit $result
