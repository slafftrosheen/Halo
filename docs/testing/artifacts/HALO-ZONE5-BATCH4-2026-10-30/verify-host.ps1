# Host-only batch 4 gate; no ADB, speech or remote provider calls.
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$filters = @(
    'com.halo.app.ui.zone5.*'
    'com.halo.app.ui.screens.notebook.*'
    'com.halo.app.ui.workspace.Studio*'
    'com.halo.app.ui.workspace.WorkspaceCsvLayoutTest'
    'com.halo.app.data.studio.*'
    'com.halo.app.ui.screens.study.*'
    'com.halo.app.study.*'
    'com.halo.app.ui.screens.kids.*'
    'com.halo.app.data.memory.MemoryRepositoryTest'
    'com.halo.app.data.workspace.WorkspaceNoteActionsTest'
    'com.halo.app.data.workspace.WorkspaceFileEditingActionsTest'
    'com.halo.app.data.workspace.WorkspaceCanvas*'
    'com.halo.app.data.workspace.WorkspaceStudy*'
    'com.halo.app.data.workspace.WorkspaceOperabilityTest'
    'com.halo.app.ui.shell.*'
    'com.halo.app.ui.presence.ShellPage*'
    'com.halo.app.ui.presence.HaloOrbThemeTest'
    'com.halo.app.ui.theme.*'
    'com.halo.app.di.HostFirstSkillAuditTest'
    'com.halo.app.di.tools.AssistantToolsTest'
    'com.halo.app.di.tools.StudySessionToolsTest'
)
$gradleArgs = @(':app:testDebugUnitTest')
foreach ($filter in $filters) { $gradleArgs += @('--tests', $filter) }
$gradleArgs += @(':app:assembleDebug', '-Proborazzi.test.record=true', '--max-workers=2', '--console=plain', '--no-configuration-cache')
Push-Location $repoRoot
try {
    & .\gradlew.bat @gradleArgs
    $gateCode = $LASTEXITCODE
} finally { Pop-Location }
exit $gateCode
