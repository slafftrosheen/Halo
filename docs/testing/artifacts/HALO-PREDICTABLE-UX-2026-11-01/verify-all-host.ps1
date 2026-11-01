$ErrorActionPreference = 'Stop'
$gradleArgs = @(':app:testDebugUnitTest', ':app:assembleDebug', ':app:compileDebugAndroidTestKotlin', '-Proborazzi.test.record=true', '--max-workers=2', '--no-parallel', '--console=plain', '--no-configuration-cache')
& .\gradlew.bat @gradleArgs *> (Join-Path $PSScriptRoot 'all-host.log')
$result = $LASTEXITCODE
Get-Content (Join-Path $PSScriptRoot 'all-host.log') -Tail 35
exit $result
