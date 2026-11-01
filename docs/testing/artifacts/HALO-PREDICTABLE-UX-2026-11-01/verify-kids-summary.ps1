$ErrorActionPreference = 'Stop'
$gradleArgs = @(':app:testDebugUnitTest', '--tests', 'com.halo.app.ui.screens.kids.KidsShellOrbBindingTest', '--max-workers=1', '--no-daemon', '--no-parallel', '--console=plain', '--no-configuration-cache')
& .\gradlew.bat @gradleArgs *> (Join-Path $PSScriptRoot 'kids-summary-host.log')
$result = $LASTEXITCODE
Get-Content (Join-Path $PSScriptRoot 'kids-summary-host.log') -Tail 12
exit $result
