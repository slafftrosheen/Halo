$ErrorActionPreference = 'Stop'
$filters = @(
    'com.halo.app.ui.zone5.*'
    'com.halo.app.ui.shell.*'
    'com.halo.app.ui.theme.*'
    'com.halo.app.ui.presence.HaloOrbThemeTest'
    'com.halo.app.ui.presence.ShellPage*'
    'com.halo.app.ui.presence.Zone5OrbMotionTest'
    'com.halo.app.ui.presence.*'
    'com.halo.app.ui.screens.notebook.*'
    'com.halo.app.ui.screens.kids.*'
    'com.halo.app.ui.screens.settings.Settings*'
    'com.halo.app.ui.screens.settings.BrandSettings*'
    'com.halo.app.ui.screens.devices.*'
    'com.halo.app.ui.screens.activity.*'
    'com.halo.app.ui.screens.notifications.*'
    'com.halo.app.screen.*'
    'com.halo.app.ui.workspace.Studio*'
    'com.halo.app.ui.workspace.WorkspaceCsvLayoutTest'
    'com.halo.app.ui.workspace.WorkspaceFileTools*'
    'com.halo.app.ui.workspace.WorkspaceContinue*'
    'com.halo.app.di.HostFirstSkillAuditTest'
)
$gradleArgs = @(':app:testDebugUnitTest')
foreach ($filter in $filters) { $gradleArgs += @('--tests', $filter) }
$gradleArgs += @(':app:assembleDebug', ':app:assembleDebugAndroidTest', '-Proborazzi.test.record=true', '--max-workers=2', '--no-parallel', '--console=plain', '--no-configuration-cache')
& .\gradlew.bat @gradleArgs *> (Join-Path $PSScriptRoot 'kids-final-host.log')
$gateCode = $LASTEXITCODE
Get-Content (Join-Path $PSScriptRoot 'kids-final-host.log') -Tail 35
exit $gateCode
