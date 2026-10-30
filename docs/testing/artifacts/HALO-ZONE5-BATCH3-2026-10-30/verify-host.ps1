# Run the batch 3 host gate from this checkout. Does not contact devices or providers.
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$filters = @(
    'com.halo.app.ui.screens.home.HomeComposer*'
    'com.halo.app.ui.screens.home.HomeFeedFollowingTest'
    'com.halo.app.ui.screens.home.HomeThreadPresentationTest'
    'com.halo.app.ui.screens.home.HomeChat*'
    'com.halo.app.ui.screens.home.HomeAttachmentSheetTest'
    'com.halo.app.ui.screens.home.HomeRecoveryActionsTest'
    'com.halo.app.ui.screens.home.HomeNoteHandoffTest'
    'com.halo.app.ui.screens.notebook.*'
    'com.halo.app.screen.*'
    'com.halo.app.ui.shell.*'
    'com.halo.app.ui.presence.ShellPage*'
    'com.halo.app.ui.presence.HaloOrbThemeTest'
    'com.halo.app.ui.theme.*'
    'com.halo.app.ui.zone5.*'
    'com.halo.app.di.HostFirstSkillAuditTest'
    'com.halo.app.di.tools.AssistantToolsTest'
    'com.halo.app.di.tools.ScreenToolExecutorTest'
    'com.halo.app.context.CurrentScreenToolsTest'
    'com.halo.app.data.SavedChatToolsTest'
)
$gradleArgs = @(':app:testDebugUnitTest')
foreach ($filter in $filters) { $gradleArgs += @('--tests', $filter) }
$gradleArgs += @(':app:assembleDebug', '-Proborazzi.test.record=true', '--max-workers=2', '--console=plain')
Push-Location $repoRoot
try {
    & .\gradlew.bat @gradleArgs
    $gateCode = $LASTEXITCODE
} finally { Pop-Location }
exit $gateCode
