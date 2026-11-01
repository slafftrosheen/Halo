$ErrorActionPreference = 'Stop'
$filters = @(
    'com.halo.app.ui.screens.notebook.NoteEditorViewModelTest',
    'com.halo.app.ui.screens.notebook.NoteStructuredRecoveryTest',
    'com.halo.app.data.workspace.WorkspaceExternalExportTest',
    'com.halo.app.ui.zone5.Zone5CreativeActionsTest',
    'com.halo.app.ui.zone5.Zone5GuideCompletionTest',
    'com.halo.app.data.workspace.WorkspaceContinuityTest',
    'com.halo.app.ui.zone5.Zone5WorkspaceMenusTest',
    'com.halo.app.ui.shell.HaloZoneLayoutTest',
    'com.halo.app.ui.shell.HaloZone5InteractionTest',
    'com.halo.app.ui.shell.Zone5WorkingAreaTest',
    'com.halo.app.data.studio.StudioLocalWorkflowTest',
    'com.halo.app.data.notebook.NotebookDocumentRepositoryTest',
    'com.halo.app.study.StudyLessonCountTest',
    'com.halo.app.ui.screens.notebook.NotebookLibrarySearchOrderTest',
    'com.halo.app.ui.workspace.WorkspaceFileToolsViewModelTest'
)
$gradleArgs = @(':app:testDebugUnitTest')
foreach ($filter in $filters) { $gradleArgs += @('--tests', $filter) }
$gradleArgs += @('-Proborazzi.test.record=true', '--max-workers=2', '--no-parallel', '--console=plain', '--no-configuration-cache')
& .\gradlew.bat @gradleArgs *> (Join-Path $PSScriptRoot 'recovery-shell-host.log')
$result = $LASTEXITCODE
Get-Content (Join-Path $PSScriptRoot 'recovery-shell-host.log') -Tail 45
exit $result
