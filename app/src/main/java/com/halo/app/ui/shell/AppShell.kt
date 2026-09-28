package com.halo.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.glass.components.HaloNavigation
import com.halo.app.ui.glass.components.NavigationItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.screens.activity.ActivityScreen
import com.halo.app.ui.screens.devices.BuddyBotControlScreen
import com.halo.app.ui.screens.devices.DevicesScreen
import com.halo.app.ui.screens.devices.HeyCyanControlScreen
import com.halo.app.ui.screens.discovery.PairingScreen
import com.halo.app.ui.screens.home.HomeScreen
import com.halo.app.ui.screens.notebook.ConversationDetailScreen
import com.halo.app.ui.screens.notebook.ConversationsListScreen
import com.halo.app.ui.screens.notebook.CanvasEditorScreen
import com.halo.app.ui.screens.notebook.CanvasEditorViewModel
import com.halo.app.ui.screens.notebook.NoteEditorScreen
import com.halo.app.ui.screens.notebook.NoteEditorViewModel
import com.halo.app.ui.screens.study.StudyScreen
import com.halo.app.ui.screens.study.StudyViewModel
import com.halo.app.ui.screens.notebook.NotebookNavigation
import com.halo.app.ui.screens.notebook.NotebookScreen
import com.halo.app.ui.screens.notebook.WorkspaceObjectDetailScreen
import com.halo.app.ui.screens.settings.AndroidAssistantScreen
import com.halo.app.ui.screens.settings.IntelligenceScreen
import com.halo.app.ui.screens.settings.RuntimeScreen
import com.halo.app.ui.screens.settings.SettingsScreen
import com.halo.app.ui.screens.settings.SkillsScreen
import com.halo.app.ui.screens.settings.VoiceSettingsScreen
import com.halo.app.ui.screens.settings.StorageBackupScreen
import com.halo.app.ui.screens.settings.SystemCheckScreen
import com.halo.app.ui.screens.settings.SystemCheckViewModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val glyph: HaloGlyph) {
    data object Home : Screen("home", "Home", HaloGlyph.HOME)
    data object Activity : Screen("activity", "Activity", HaloGlyph.ACTIVITY)
    data object Notebook : Screen("notebook", "Workspace", HaloGlyph.NOTE)
    data object Devices : Screen("devices", "Devices", HaloGlyph.DEVICE)
    data object Settings : Screen("settings", "Settings", HaloGlyph.SETTINGS)
}

val bottomNavItems = listOf(Screen.Home, Screen.Activity, Screen.Notebook, Screen.Devices, Screen.Settings)

private fun primaryNavigationRoute(route: String?): String? = when {
    route == null -> null
    route.startsWith("notebook/") -> Screen.Notebook.route
    route == "discovery" || route.startsWith("device/") -> Screen.Devices.route
    route in setOf("ai_settings", "voice_settings", "permissions") -> Screen.Settings.route
    else -> route
}

@Composable
fun AppShell(
    onRequestPermissions: () -> Unit,
    onRequestBluetoothPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit
) {
    val navController = rememberNavController()
    val homeViewModel = ManualDependencyGraph.homeViewModel
    val shellScope = rememberCoroutineScope()

    /*
     * RUNTIME-019: the global interaction renderer lives OUTSIDE screen
     * navigation, so a pending durable request stays resolvable from any tab
     * (spec #68). The ViewModel owns presentation state; composables never
     * read the dependency graph directly (spec #83). Dismiss only hides the
     * surface — the durable request remains pending (spec #65).
     */
    val interactionViewModel: com.halo.app.interaction.InteractionSurfaceViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = com.halo.app.interaction.InteractionSurfaceViewModelFactory(),
        )
    val interactionState by interactionViewModel.state.collectAsState()
    interactionState.primary?.let { surface ->
        com.halo.app.interaction.InteractionSurfaceRenderer(
            surface = surface,
            onRespond = interactionViewModel::respond,
        )
    }

    fun navigateSafely(route: String) {
        if (navController.currentDestination?.route != route) {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    LaunchedEffect(homeViewModel) {
        homeViewModel.navigationEvents.collectLatest(::navigateSafely)
    }

    BoxWithConstraints {
    val expandedNavigation = maxWidth >= 600.dp
    val backStackEntry by navController.currentBackStackEntryAsState()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!expandedNavigation) {
                HaloNavigation(
                    navController = navController,
                    items = bottomNavItems.map { NavigationItem(it.route, it.title, it.glyph) }
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Row(Modifier.fillMaxSize().padding(innerPadding)) {
            if (expandedNavigation) {
                NavigationRail {
                    bottomNavItems.forEach { item ->
                        NavigationRailItem(
                            selected = primaryNavigationRoute(backStackEntry?.destination?.route) == item.route,
                            onClick = { navigateSafely(item.route) },
                            icon = { com.halo.app.ui.icons.HaloIcon(item.glyph, contentDescription = item.title, modifier = Modifier.size(24.dp)) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        Box(Modifier.weight(1f).fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onRequestMicrophonePermission = onRequestPermissions
                    )
                }
                composable(Screen.Activity.route) {
                    ActivityScreen(ManualDependencyGraph.activityViewModel, onOpenObject = { id ->
                        shellScope.launch {
                            val item = ManualDependencyGraph.workspaceUseCases.getObject(id)
                            val route = when (item?.type) {
                                com.halo.app.data.notebook.WorkspaceObjectType.NOTE -> "notebook/note/$id"
                                com.halo.app.data.notebook.WorkspaceObjectType.CONVERSATION -> "notebook/conversation/$id"
                                com.halo.app.data.notebook.WorkspaceObjectType.MEMORY -> "notebook/memory/$id"
                                WorkspaceObjectType.CANVAS -> "notebook/canvas/$id"
                                com.halo.app.data.notebook.WorkspaceObjectType.FILE,
                                com.halo.app.data.notebook.WorkspaceObjectType.MEDIA,
                                com.halo.app.data.notebook.WorkspaceObjectType.ARTIFACT -> "notebook/file/$id"
                                null -> Screen.Notebook.route
                            }
                            navigateSafely(route)
                        }
                    })
                }

                composable(Screen.Notebook.route) {
                    val notebookViewModel = ManualDependencyGraph.notebookViewModel
                    LaunchedEffect(notebookViewModel) {
                        notebookViewModel.navigationEvents.collectLatest { event ->
                            when (event) {
                                is NotebookNavigation.OpenNote -> navigateSafely("notebook/note/${event.id}")
                                is NotebookNavigation.OpenConversation -> navigateSafely("notebook/conversation/${event.id}")
                                is NotebookNavigation.OpenFile -> navigateSafely("notebook/file/${event.id}")
                                is NotebookNavigation.OpenMemory -> navigateSafely("notebook/memory/${event.id}")
                                is NotebookNavigation.OpenCanvas -> navigateSafely("notebook/canvas/${event.id}")
                                is NotebookNavigation.OpenStudy -> navigateSafely("notebook/study/${event.sessionId}")
                            }
                        }
                    }
                    NotebookScreen(notebookViewModel)
                }

                composable(
                    "notebook/note/{noteId}",
                    arguments = listOf(navArgument("noteId") { type = NavType.StringType })
                ) { entry ->
                    val noteId = entry.arguments?.getString("noteId")
                    if (noteId == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        val editorViewModel: NoteEditorViewModel = viewModel(
                            key = "note-editor-$noteId",
                            factory = NoteEditorViewModel.Factory(
                                noteId, ManualDependencyGraph.notebookUseCases,
                                ManualDependencyGraph.notebookAssetRepository,
                                ManualDependencyGraph.notebookDocumentRepository,
                                ManualDependencyGraph.workspaceUseCases,
                                ManualDependencyGraph.aiProviderRouter,
                                { ManualDependencyGraph.workspaceContext.cloudSharing.value },
                                study = ManualDependencyGraph.studyUseCases,
                            )
                        )
                        NoteEditorScreen(
                            viewModel = editorViewModel,
                            onBack = { navController.popBackStack() },
                            onOpenStudy = { sessionId -> navigateSafely("notebook/study/$sessionId") },
                        )
                    }
                }

                composable(
                    "notebook/study/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
                ) { entry ->
                    val sessionId = entry.arguments?.getString("sessionId")
                    if (sessionId == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        val studyViewModel: StudyViewModel = viewModel(
                            key = "study-$sessionId",
                            factory = StudyViewModel.Factory(
                                sessionId,
                                ManualDependencyGraph.studyUseCases,
                                ManualDependencyGraph.studyTutorGateway,
                            ),
                        )
                        StudyScreen(studyViewModel) { navController.popBackStack() }
                    }
                }

                composable(
                    "notebook/canvas/{canvasId}",
                    arguments = listOf(navArgument("canvasId") { type = NavType.StringType })
                ) { entry ->
                    val canvasId = entry.arguments?.getString("canvasId")
                    if (canvasId == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        val canvasViewModel: CanvasEditorViewModel = viewModel(
                            key = "canvas-editor-$canvasId",
                            factory = CanvasEditorViewModel.Factory(
                                canvasId,
                                ManualDependencyGraph.notebookCanvasRepository,
                            ),
                        )
                        CanvasEditorScreen(canvasViewModel) { navController.popBackStack() }
                    }
                }

                composable("notebook/file/{objectId}",
                    arguments = listOf(navArgument("objectId") { type = NavType.StringType })) { entry ->
                    entry.arguments?.getString("objectId")?.let { id ->
                        WorkspaceObjectDetailScreen(id, "file", ManualDependencyGraph.workspaceUseCases) {
                            navController.popBackStack()
                        }
                    }
                }
                composable("notebook/memory/{objectId}",
                    arguments = listOf(navArgument("objectId") { type = NavType.StringType })) { entry ->
                    entry.arguments?.getString("objectId")?.let { id ->
                        WorkspaceObjectDetailScreen(id, "memory", ManualDependencyGraph.workspaceUseCases) {
                            navController.popBackStack()
                        }
                    }
                }

                composable(Screen.Devices.route) {
                    val viewModel = ManualDependencyGraph.devicesViewModel
                    LaunchedEffect(viewModel) {
                        viewModel.navigationEvents.collectLatest(::navigateSafely)
                    }
                    DevicesScreen(viewModel)
                }

                composable("device/{deviceId}", arguments = listOf(navArgument("deviceId") { type = NavType.StringType })) { entry ->
                    val viewModel = ManualDependencyGraph.devicesViewModel
                    val deviceId = entry.arguments?.getString("deviceId")
                    val state by viewModel.uiState.collectAsState()
                    val deviceKind = state.devices.firstOrNull { it.id == deviceId }?.kind
                    when {
                        deviceId == null -> navController.popBackStack()
                        deviceKind == com.halo.sdk.runtime.embodiment.DeviceKind.BUDDYBOT -> BuddyBotControlScreen(deviceId, viewModel) { navController.popBackStack() }
                        deviceKind == com.halo.sdk.runtime.embodiment.DeviceKind.HEYCYAN_GLASSES -> HeyCyanControlScreen(deviceId, viewModel) { navController.popBackStack() }
                        else -> navController.popBackStack()
                    }
                }

                composable("skills") {
                    SkillsScreen(
                        skillRegistry = ManualDependencyGraph.skillRegistry,
                        onBack = { navController.popBackStack() },
                        toolRegistry = ManualDependencyGraph.toolRegistry,
                        availabilityResolver = ManualDependencyGraph.toolAvailabilityResolver,
                        capabilityCatalogue = ManualDependencyGraph.capabilityCatalogue,
                        onInstallCustomSkill = ManualDependencyGraph::installCustomSkillPackage,
                    )
                }

                composable("runtime_status") {
                    RuntimeScreen(
                        projection = ManualDependencyGraph.runtimeUiProjection,
                        catalogueSource = ManualDependencyGraph.capabilityCatalogue,
                        onBack = { navController.popBackStack() },
                        onOpenSystemCheck = { navigateSafely("system_check") }
                    )
                }

                composable("system_check") {
                    val context = LocalContext.current
                    val viewModel = remember(context) {
                        SystemCheckViewModel(
                            appContext = context.applicationContext,
                            providerRouter = ManualDependencyGraph.aiProviderRouter
                        )
                    }
                    SystemCheckScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("notebook/conversations") {
                    ConversationsListScreen(
                        conversationStore = ManualDependencyGraph.conversationStore,
                        onOpenConversation = { navigateSafely("notebook/conversation/$it") },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    "notebook/conversation/{conversationId}",
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                ) { entry ->
                    val conversationId = entry.arguments?.getString("conversationId")
                    val scope = rememberCoroutineScope()
                    if (conversationId == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        val history by ManualDependencyGraph.conversationStore.history.collectAsState()
                        val conversations by ManualDependencyGraph.conversationStore.conversations.collectAsState()
                        val turns = history.filter { it.conversationId == conversationId }
                        ConversationDetailScreen(
                            conversationId = conversationId,
                            savedTitle = conversations.firstOrNull { it.id == conversationId }?.title,
                            turns = turns,
                            onContinue = {
                                scope.launch {
                                    ManualDependencyGraph.conversationStore.open(conversationId)
                                    navController.popBackStack()
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                composable(Screen.Settings.route) {
                    val viewModel = ManualDependencyGraph.settingsViewModel
                    LaunchedEffect(viewModel) { viewModel.navigationEvents.collectLatest(::navigateSafely) }
                    SettingsScreen(viewModel)
                }

                composable("automation") {
                    com.halo.app.ui.screens.settings.AutomationScreen(ManualDependencyGraph.routinesViewModel,
                        onBack = { navController.popBackStack() }, onNotifications = { navigateSafely("notification_settings") })
                }
                composable("notification_settings") {
                    com.halo.app.ui.screens.settings.NotificationSettingsScreen(ManualDependencyGraph.routinesViewModel,
                        onBack = { navController.popBackStack() })
                }
                composable("ai_settings") {
                    val viewModel = ManualDependencyGraph.intelligenceViewModel
                    IntelligenceScreen(viewModel, { navController.popBackStack() },
                        onBrowseModels = { navigateSafely("model_browser") })
                }
                composable("model_browser") {
                    com.halo.app.ui.screens.settings.ModelBrowserScreen(
                        ManualDependencyGraph.intelligenceViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("android_assistant") {
                    AndroidAssistantScreen(
                        ManualDependencyGraph.assistantRoleController,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("voice_settings") {
                    val viewModel = ManualDependencyGraph.voiceSettingsViewModel
                    LaunchedEffect(viewModel) {
                        viewModel.navigationEvents.collectLatest { if (it == "back") navController.popBackStack() }
                    }
                    VoiceSettingsScreen(viewModel) { navController.popBackStack() }
                }

                composable("permissions") {
                    PermissionsScreen(
                        onBack = { navController.popBackStack() },
                        onRequestPermissions = onRequestPermissions,
                        storageAccessRepository = ManualDependencyGraph.storageAccessRepository,
                        onOpenStorageBackup = { navigateSafely("storage_backup") },
                    )
                }
                composable("storage_backup") {
                    StorageBackupScreen(ManualDependencyGraph.storageBackupViewModel) { navController.popBackStack() }
                }
                composable("discovery") {
                    val viewModel = ManualDependencyGraph.pairingViewModel
                    LaunchedEffect(viewModel) {
                        viewModel.navigationEvents.collectLatest { if (it == "back") navController.popBackStack() }
                    }
                    PairingScreen(
                        viewModel = viewModel,
                        onRequestBluetoothPermissions = onRequestBluetoothPermissions,
                        onRequestBluetoothEnable = onRequestBluetoothEnable
                    )
                }
            }
        }
        }
    }
    }
}
