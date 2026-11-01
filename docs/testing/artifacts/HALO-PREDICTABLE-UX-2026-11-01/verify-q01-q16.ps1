$ErrorActionPreference = 'Stop'
$filters = @(
    'com.halo.app.ui.theme.ActionPinPreferencesTest',
    'com.halo.app.ui.zone5.Zone5PinnedActionsTest',
    'com.halo.app.ui.screens.notebook.CanvasEditorPersistenceTest',
    'com.halo.app.ui.screens.notebook.CanvasEditorSelectionInteractionTest',
    'com.halo.app.ui.screens.study.StudySetupRecoveryTest',
    'com.halo.app.data.workspace.SelectedPassageSetupRegressionTest',
    'com.halo.app.ui.workspace.WorkspaceContinueLayoutTest',
    'com.halo.app.ui.presence.ShellUtilityBandTest',
    'com.halo.app.ui.presence.ShellPageActionsTest',

    'com.halo.app.ui.screens.notebook.NoteEditorViewModelTest',
    'com.halo.app.ui.screens.notebook.NoteStructuredRecoveryTest',
    'com.halo.app.data.workspace.WorkspaceExternalExportTest',
    'com.halo.app.ui.workspace.WorkspaceExternalExportHostTest',
    'com.halo.app.ui.workspace.WorkspaceExternalExportRenderingTest',
    'com.halo.app.ui.zone5.Zone5PredictableHelpTest',
    'com.halo.app.ui.shell.ShellOrbCatalogTest',
    'com.halo.app.ui.theme.HaloAmbientMotionTest',
    'com.halo.app.ui.theme.PalettePreferencesTest',
    'com.halo.app.ui.theme.VisualComfortPreferencesTest',
    'com.halo.app.ui.zone5.Zone5CreativeActionsTest',
    'com.halo.app.ui.zone5.Zone5DocumentActionsTest',
    'com.halo.app.ui.zone5.Zone5KidsControlsTest',
    'com.halo.app.ui.zone5.Zone5MainSurfacesVisualTest',
    'com.halo.app.ui.screens.settings.SettingsAppearanceVisualTest',
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
$gradleArgs = @(':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:testDebugUnitTest')
foreach ($filter in $filters) { $gradleArgs += @('--tests', $filter) }
$gradleArgs += @(':app:assembleDebug', ':app:compileDebugAndroidTestKotlin', '-Proborazzi.test.record=true', '--max-workers=1', '--no-daemon', '--no-parallel', '--console=plain', '--no-configuration-cache')
& .\gradlew.bat @gradleArgs *> (Join-Path $PSScriptRoot 'q01-q16-host4.log')
$result = $LASTEXITCODE
Get-Content (Join-Path $PSScriptRoot 'q01-q16-host4.log') -Tail 45
exit $result


