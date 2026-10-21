@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.halo.app.ui.shell

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Scaffold
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.halo.app.ui.theme.rememberReducedMotion
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
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
import com.halo.app.ai.readRuntimeAiContextPolicy
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
    data object Study : Screen("study", "Study", HaloGlyph.QUESTION)
    data object Activity : Screen("activity", "Activity", HaloGlyph.ACTIVITY)
    data object Notebook : Screen("notebook", "Notebook", HaloGlyph.NOTE)
    data object Devices : Screen("devices", "Devices", HaloGlyph.DEVICE)
    data object Settings : Screen("settings", "Settings", HaloGlyph.SETTINGS)
}

val bottomNavItems = listOf(Screen.Home, Screen.Activity, Screen.Notebook, Screen.Devices, Screen.Settings)

@Composable
fun AppShell(
    onRequestPermissions: () -> Unit,
    onRequestBluetoothPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onRequestSkillPermissions: (Set<com.halo.sdk.runtime.skills.HostPermission>, Set<String>) -> Unit = { _, _ -> },
    onCompactChanged: (Boolean) -> Unit = {},
) {
    val navController = rememberNavController()
    var screenMode by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var screenFull by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val reducedMotion by rememberReducedMotion()
    val setupStore = ManualDependencyGraph.setupProgress
    // Only the first un-dismissed launch opens setup. "Save and leave" is durable
    // across Activity recreation/process death even when all checks remain incomplete.
    val initialSetupNeeded = remember { setupStore.shouldAutoLaunch() }
    var setupSession by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(initialSetupNeeded) }
    val homeViewModel = ManualDependencyGraph.homeViewModel
    val kidsMode by ManualDependencyGraph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val kidsReady by ManualDependencyGraph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    val kidsLoadError by ManualDependencyGraph.kidsPreferences.loadError.collectAsStateWithLifecycle()
    if (!kidsReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(uiText("Starting halo…")) }
        return
    }
    LaunchedEffect(kidsMode) { if (kidsMode) navController.navigateToPrimary("home") }
    val shellScope = rememberCoroutineScope()
    val studyContext = LocalContext.current

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
    val interactionState by interactionViewModel.state.collectAsStateWithLifecycle()
    interactionState.primary?.let { surface ->
        com.halo.app.interaction.InteractionSurfaceRenderer(
            surface = surface,
            onRespond = interactionViewModel::respond,
        )
    }

    var parentRoute by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var parentUnavailable by remember { androidx.compose.runtime.mutableStateOf(false) }
    fun performNavigation(requested: String) {
        val route = if (ManualDependencyGraph.kidsPreferences.enabled.value && requested == "mini_halo") "home" else requested
        if (com.halo.app.ui.screens.settings.isDeveloperDestination(route) &&
            !ManualDependencyGraph.storage.getBoolean("halo_developer_tools_enabled", false)) {
            if (navController.currentDestination?.route != "advanced_settings")
                navController.navigate("advanced_settings") { launchSingleTop = true }
            return
        }
        val activity = com.halo.app.ui.mini.miniHaloActivityOwner(studyContext)
        if (activity is com.halo.app.ui.mini.MiniHaloActivity && route != "mini_halo" && route in HaloScreenCatalog.screens) {
            activity.startActivity(com.halo.app.ui.mini.MiniHaloEntry.intent(activity, route)); activity.finish()
            return
        }
        if (navController.currentDestination?.route != route) {
            if (route == "guided_setup") { navController.navigateToGuidedSetup(); return }
            if (bottomNavItems.any { it.route == route }) navController.navigateToPrimary(route)
            else navController.navigate(route) { launchSingleTop = true }
        }
    }
    val parentGate = com.halo.app.ui.screens.kids.rememberKidsGrownUpGate(
        onVerified = { parentRoute?.let(::performNavigation); parentRoute = null },
        onUnavailable = { parentRoute = null; parentUnavailable = true })
    fun navigateSafely(route: String) {
        val childSurface = route in setOf("home", "mini_halo", "study") ||
            route.startsWith("notebook/note/") || route.startsWith("notebook/canvas/") || route.startsWith("notebook/study/")
        if (ManualDependencyGraph.kidsPreferences.enabled.value && !childSurface) { parentRoute = route; parentGate() }
        else performNavigation(route)
    }
    if (parentUnavailable) androidx.compose.material3.AlertDialog(onDismissRequest = { parentUnavailable = false },
        title = { Text(uiText("Grown-up space")) },
        text = { Text(uiText("Ask a grown-up to set a device PIN or password in Android settings first.")) },
        confirmButton = { TextButton(onClick = { parentUnavailable = false }) { Text(uiText("OK")) } })
    val ownerLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val ownerState by ownerLifecycle.currentStateFlow.collectAsState()
    val requestedScreen by ManualDependencyGraph.requestedHaloScreen.collectAsStateWithLifecycle(minActiveState = androidx.lifecycle.Lifecycle.State.RESUMED)
    LaunchedEffect(requestedScreen, ownerState) {
        HaloScreenRequests.claim(ManualDependencyGraph.requestedHaloScreen,
            ownerState == androidx.lifecycle.Lifecycle.State.RESUMED)?.let(::navigateSafely)
    }
    LaunchedEffect(Unit) {
        val screen = ManualDependencyGraph.assistantPreferences.get().startScreen
        val kids = ManualDependencyGraph.kidsPreferences.getEnabled()
        if (!kids && !ManualDependencyGraph.explicitHaloScreenRequested && ManualDependencyGraph.requestedHaloScreen.value == null && navController.currentDestination?.route == "home" && screen != "home") navigateSafely(screen)
    }

    LaunchedEffect(homeViewModel) {
        homeViewModel.navigationEvents.collectLatest(::navigateSafely)
    }
    LaunchedEffect(Unit) {
        val graph = ManualDependencyGraph
        graph.interactionContext.navigation.collectLatest { target ->
            try { when(target) {
                is com.halo.runtime.cognition.context.ContextualNextActionTarget.FollowUp -> {
                    if(graph.conversationStore.activeConversationId() == target.conversationId) {
                        check(target.turnId == null || graph.conversationStore.turnsForConversation(target.conversationId).any { it.id == target.turnId }) { "Reply unavailable" }
                        navigateSafely("home"); homeViewModel.composerFocus.value += 1
                    }
                }
                is com.halo.runtime.cognition.context.ContextualNextActionTarget.OpenStudy -> {
                    check(graph.workspaceContext.versionFor(target.sourceId) != null) { "Source unavailable" }
                    navigateSafely("study?captureSource=${android.net.Uri.encode(target.sourceId)}")
                }
                is com.halo.runtime.cognition.context.ContextualNextActionTarget.OpenSource -> {
                    check(graph.workspaceContext.versionFor(target.objectId) != null) { "Source unavailable" }
                    navigateSafely("notebook/note/${android.net.Uri.encode(target.objectId)}")
                }
                is com.halo.runtime.cognition.context.ContextualNextActionTarget.ContinueStudy -> {
                    check(graph.studyUseCases.getSession(target.sessionId)?.lifecycle == com.halo.sdk.runtime.study.StudySessionLifecycle.ACTIVE) { "Study session unavailable" }
                    navigateSafely("notebook/study/${android.net.Uri.encode(target.sessionId)}")
                }
                is com.halo.runtime.cognition.context.ContextualNextActionTarget.ReviewMemory -> {
                    check(graph.workspaceContext.versionFor(target.sourceId) != null) { "Source unavailable" }
                    navigateSafely("notebook/memory?section=Memories")
                }
                is com.halo.runtime.cognition.context.ContextualNextActionTarget.ReviewRun -> navigateSafely("activity")
                is com.halo.runtime.cognition.context.ContextualNextActionTarget.ReattachSource -> { navigateSafely("home"); homeViewModel.captureMessage.value = "Select the source again using Add context." }
                is com.halo.runtime.cognition.context.ContextualNextActionTarget.ReviewSaveResult -> Unit // Home owns the reviewed exact-result dialog.
            } } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch(error: Exception) { graph.interactionContext.message.value = error.message ?: "Source unavailable" }
        }
    }
    LaunchedEffect(Unit) {
        ManualDependencyGraph.scopedSpeechMediaControls
        ManualDependencyGraph.accessoryActionOwners.navigationEvents.collectLatest(::navigateSafely)
    }

    LaunchedEffect(Unit) {
        ManualDependencyGraph.contextualCapture.navigation.collectLatest { event ->
            when(event) {
                com.halo.app.capture.ContextualCaptureNavigation.Ask -> navigateSafely("home")
                is com.halo.app.capture.ContextualCaptureNavigation.Study -> navigateSafely("study?captureSource=${android.net.Uri.encode(event.objectId)}")
                is com.halo.app.capture.ContextualCaptureNavigation.OpenNote -> navigateSafely("notebook/note/${android.net.Uri.encode(event.objectId)}")
                com.halo.app.capture.ContextualCaptureNavigation.Memory -> navigateSafely("notebook/memory?section=Memories")
            }
        }
    }
    com.halo.app.capture.ContextualCaptureSheet(ManualDependencyGraph.contextualCapture,
        onVisionSetup = { navigateSafely("settings/intelligence/roles") })
    com.halo.app.study.ExtractionReviewSheet(ManualDependencyGraph.extractionReview)
    LaunchedEffect(Unit) {
        ManualDependencyGraph.extractionReview.navigation.collectLatest { destination ->
            try { when(destination.kind) {
                "study" -> navigateSafely("study?captureSource=${android.net.Uri.encode(destination.noteId)}")
                "ask" -> {
                    ManualDependencyGraph.visualAttachments.remove()
                    ManualDependencyGraph.interactionContext.selectWorkspace(destination.noteId)
                    ManualDependencyGraph.homeViewModel.selectedMode.value = com.halo.runtime.cognition.context.CognitiveOperatingMode.ASK
                    navigateSafely("home")
                }
                else -> navigateSafely("notebook/note/${android.net.Uri.encode(destination.noteId)}")
            } } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { ManualDependencyGraph.extractionReview.navigationFailed(error.message ?: "Could not open the reviewed note; it remains saved") }
        }
    }

    BoxWithConstraints {
    val expandedNavigation = maxWidth >= 600.dp
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val developerRouteBlocked = currentRoute?.let {
        com.halo.app.ui.screens.settings.isDeveloperDestination(it)
    } == true && !ManualDependencyGraph.storage.getBoolean("halo_developer_tools_enabled", false)
    LaunchedEffect(currentRoute, developerRouteBlocked) {
        if (developerRouteBlocked) {
            navController.popBackStack()
            performNavigation("advanced_settings")
        }
    }
    val immersiveScreen = screenFull && currentRoute == "home" && screenMode && !kidsMode
    val mini = backStackEntry?.destination?.route == "mini_halo"
    LaunchedEffect(mini) { onCompactChanged(mini) }
    val editingWorkspace = backStackEntry?.destination?.route?.let {
        it.startsWith("notebook/note/") || it.startsWith("notebook/canvas/") || it.startsWith("notebook/study/")
    } == true
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!immersiveScreen) Column {
            val liveHome by homeViewModel.uiState.collectAsStateWithLifecycle()
            if (!kidsMode && !editingWorkspace) LiveTaskPill(liveHome,
                onReview = { navigateSafely("activity") }, onStop = homeViewModel::cancelSession)
            com.halo.app.ui.components.HaloReadAloudBar(allowVoiceSettings = !kidsMode,
                onVoiceSettings = { navigateSafely("settings/voice/output") })
            if (!mini && !kidsMode && !editingWorkspace && !expandedNavigation && backStackEntry?.destination?.route != "guided_setup") {
                HaloNavigation(
                    navController = navController,
                    items = remember { bottomNavItems.map { NavigationItem(it.route, it.title, it.glyph) } }
                )
            }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Row(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
            if (!immersiveScreen && !mini && !kidsMode && !editingWorkspace && expandedNavigation && backStackEntry?.destination?.route != "guided_setup") {
                NavigationRail {
                    bottomNavItems.forEach { item ->
                        NavigationRailItem(
                            selected = primaryNavigationRoute(backStackEntry?.destination?.route) == item.route,
                            onClick = { navigateSafely(item.route) },
                            icon = { com.halo.app.ui.icons.HaloIcon(item.glyph, contentDescription = uiText(item.title), modifier = Modifier.size(24.dp)) },
                            label = { Text(uiText(item.title)) }
                        )
                    }
                }
            }
        Column(Modifier.weight(1f).fillMaxSize()) {
        kidsLoadError?.let { Text(uiText(it), Modifier.padding(16.dp)) }
        if (kidsMode && backStackEntry?.destination?.route != "home" && backStackEntry?.destination?.route != "guided_setup")
            TextButton(onClick = { navigateSafely("home") }) { Text(uiText("Back to my learning space")) }
        if (setupSession && backStackEntry?.destination?.route != "guided_setup") TextButton(onClick = { navigateSafely("guided_setup") }) { Text(uiText("Return to guided setup")) }
        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            SharedTransitionLayout {
            if (!developerRouteBlocked) NavHost(
                navController = navController,
                startDestination = if (initialSetupNeeded) "guided_setup" else Screen.Home.route,
                modifier = Modifier.widthIn(max = 1040.dp).fillMaxSize(),
                enterTransition = { if (reducedMotion) androidx.compose.animation.EnterTransition.None else fadeIn(tween(140)) },
                exitTransition = { if (reducedMotion) androidx.compose.animation.ExitTransition.None else fadeOut(tween(90)) },
                popEnterTransition = { if (reducedMotion) androidx.compose.animation.EnterTransition.None else fadeIn(tween(140)) },
                popExitTransition = { if (reducedMotion) androidx.compose.animation.ExitTransition.None else fadeOut(tween(90)) },
            ) {
                composable("mini_halo") {
                    val activity = com.halo.app.ui.mini.miniHaloActivityOwner(androidx.compose.ui.platform.LocalContext.current)
                    androidx.activity.compose.BackHandler(enabled = activity is com.halo.app.ui.mini.MiniHaloActivity) {
                        homeViewModel.cancelVoiceInput()
                        activity?.finish()
                    }
                    com.halo.app.ui.mini.MiniHaloScreen(homeViewModel, onRequestPermissions,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                        allowSharedOrb = com.halo.app.ui.presence.canShareHaloOrb(
                            activity !is com.halo.app.ui.mini.MiniHaloActivity, reducedMotion),
                        onOpen = { screen ->
                        if (activity is com.halo.app.ui.mini.MiniHaloActivity) {
                            activity.startActivity(com.halo.app.ui.mini.MiniHaloEntry.intent(activity, screen)); activity.finish()
                        } else navigateSafely(screen)
                    },
                        onClose = {
                            homeViewModel.cancelVoiceInput()
                            if (activity is com.halo.app.ui.mini.MiniHaloActivity) {
                                activity.finish()
                            } else if (!navController.popBackStack()) {
                                // In-app Mini is a destination, not a new window:
                                // closing it returns to Home instead of hiding Halo.
                                navController.navigateToPrimary(Screen.Home.route)
                            }
                        })
                }
                composable("guided_setup") {
                    LaunchedEffect(Unit) {
                        setupSession = true
                        // The welcome wizard is offered once, never imposed on every
                        // app restart. Its stage remains resumable from Settings.
                        if (initialSetupNeeded) setupStore.dismissAutomaticLaunch()
                    }
                    com.halo.app.ui.onboarding.HaloSetupScreen(setupStore, onOpen = ::navigateSafely, onExit = {
                        setupStore.dismissAutomaticLaunch()
                        setupSession = false
                        navController.navigate("home") { popUpTo("guided_setup") { inclusive = true }; launchSingleTop = true }
                    })
                }
                composable(Screen.Home.route) {
                    if (kidsMode) com.halo.app.ui.screens.kids.KidsModeScreen(homeViewModel, onRequestPermissions,
                        onOpenCanvas = { id -> navigateSafely("notebook/canvas/${android.net.Uri.encode(id)}") })
                    else Column(Modifier.fillMaxSize()) {
                        if (screenMode) {
                            val screenDensity = androidx.compose.ui.platform.LocalDensity.current
                            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                                androidx.compose.ui.unit.Density(screenDensity.density, screenDensity.fontScale * if (screenFull) 1.4f else 1f)) {
                            com.halo.app.screen.HaloScreen(ManualDependencyGraph.screenStore, screenFull, { screenFull = it },
                            onOpen = ::navigateSafely, onTalk = {
                                screenMode = false; screenFull = false
                                val current = homeViewModel.uiState.value
                                if (!current.microphonePermissionGranted) onRequestPermissions()
                                else if (!current.speechReady) navigateSafely("voice_settings")
                                else homeViewModel.startListening()
                            }, onChat = { screenMode = false; screenFull = false })
                            }
                        } else HomeScreen(
                        surfaceSwitcher = { com.halo.app.screen.HomeSurfaceSwitch(false, { screenMode = it; if (!it) screenFull = false }) },
                        onOpenCapture = { id -> navigateSafely("notebook/note/${android.net.Uri.encode(id)}") },
                        onDailyAction = { action -> navigateSafely(dailyOwnerRoute(action)) },
                        viewModel = homeViewModel,
                        onConfigureVision = { navigateSafely("settings/intelligence/roles") },
                        onOpenNotificationInbox = { navigateSafely("notifications") },
                        onOpenActivity = { navigateSafely(Screen.Activity.route) },
                        onContextPrivacy = { navigateSafely("settings/intelligence/context") },
                        onAssistantSetup = { navigateSafely("android_assistant") },
                        onOpenProvider = { navigateSafely("settings/intelligence/providers/${android.net.Uri.encode(it)}") },
                        onRequestMicrophonePermission = onRequestPermissions,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                        allowSharedOrb = com.halo.app.ui.presence.canShareHaloOrb(true, reducedMotion)
                    )
                    }
                }
                composable("study?captureSource={captureSource}&goal={goal}", arguments = listOf(
                    navArgument("captureSource") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("goal") { type = NavType.StringType; nullable = true; defaultValue = null }
                )) { entry ->
                    val model: com.halo.app.ui.screens.study.StudyHomeViewModel = viewModel(
                        factory = com.halo.app.ui.screens.study.StudyHomeViewModel.Factory(ManualDependencyGraph.studyUseCases, ManualDependencyGraph.workspaceRepository, ManualDependencyGraph.workspaceFileRepository, ManualDependencyGraph.studySourceResolver,ManualDependencyGraph.studyLessonStore, executionStorage = ManualDependencyGraph.storage))
                    LaunchedEffect(model, entry.arguments?.getString("captureSource")) { entry.arguments?.getString("captureSource")?.let(model::select) }
                    com.halo.app.ui.screens.study.StudyHomeScreen(model,
                        onResume = { id -> navigateSafely("notebook/study/${android.net.Uri.encode(id)}") },
                        onReview = { id -> navigateSafely("notebook/study/${android.net.Uri.encode(id)}?review=true") },
                        onNotebook = {
                            ManualDependencyGraph.notebookViewModel.setFilter(com.halo.app.ui.screens.notebook.NotebookFilter.NOTES)
                            navigateSafely("notebook")
                        },
                        initialSourceId = entry.arguments?.getString("captureSource"),
                        initialGoal = entry.arguments?.getString("goal"))
                }
                composable("activity?runId={runId}", arguments = listOf(navArgument("runId") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                    ActivityScreen(ManualDependencyGraph.activityViewModel, focusRunId = entry.arguments?.getString("runId"),
                        onHome = { navController.navigateToPrimary("home") },
                        onReviewRun = interactionViewModel::reviewRun, onOpenObject = { id ->
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

                composable(Screen.Notebook.route) { notebookEntry ->
                    val requestLocalSearch by notebookEntry.savedStateHandle.getStateFlow("openLocalSearch", false).collectAsStateWithLifecycle()
                    val notebookViewModel = ManualDependencyGraph.notebookViewModel
                    LaunchedEffect(notebookViewModel) {
                        notebookViewModel.navigationEvents.collectLatest { event ->
                            when (event) {
                                is NotebookNavigation.OpenMemoryHub -> navigateSafely("notebook/memory?section=${event.section}" + event.noteId?.let { "&noteId=${android.net.Uri.encode(it)}" }.orEmpty())
                                is NotebookNavigation.OpenNote -> navigateSafely("notebook/note/${event.id}")
                                is NotebookNavigation.OpenConversation -> navigateSafely("notebook/conversation/${event.id}")
                                is NotebookNavigation.OpenFile -> navigateSafely("notebook/file/${event.id}")
                                is NotebookNavigation.OpenMemory -> navigateSafely("notebook/memory/${event.id}")
                                is NotebookNavigation.OpenCanvas -> navigateSafely("notebook/canvas/${event.id}" + (event.nodeId?.let { "?nodeId=${android.net.Uri.encode(it)}" } ?: ""))
                                is NotebookNavigation.ReviewAutomation -> navigateSafely("automation_review/${android.net.Uri.encode(event.routineId)}/${android.net.Uri.encode(event.suggestionId)}")
                                NotebookNavigation.ReviewCapabilities -> navigateSafely("ai_settings")
                                is NotebookNavigation.OpenStudy -> navigateSafely("notebook/study/${event.sessionId}?review=${event.review}")
                                is NotebookNavigation.StartStudyFromSource -> navigateSafely(
                                    "study?captureSource=${android.net.Uri.encode(event.objectId)}")
                            }
                        }
                    }
                    NotebookScreen(notebookViewModel,
                        requestLocalSearch = requestLocalSearch,
                        onLocalSearchOpened = { notebookEntry.savedStateHandle["openLocalSearch"] = false },
                        onOpenStudy = { navigateSafely(Screen.Study.route) },
                        onSkills = { navigateSafely("skills") },
                        onOpenModels = { navigateSafely("settings/intelligence/on-device") })
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
                                { (!ManualDependencyGraph.kidsPreferences.enabled.value || ManualDependencyGraph.kidsPreferences.cloudAllowed.value) && ManualDependencyGraph.storage.readRuntimeAiContextPolicy().allowCloudWorkspace },
                                imageService = ManualDependencyGraph.workspaceImageService,
                                study = ManualDependencyGraph.studyUseCases,
                                structuredResults = ManualDependencyGraph.notebookStructuredResults,
                            )
                        )
                        NoteEditorScreen(
                            viewModel = editorViewModel,
                            onBack = { navController.popBackStack() },
                            onOpenStudy = { sessionId -> navigateSafely("notebook/study/$sessionId") },
                            onStartStudySetup = { goal ->
                                val source = "study?captureSource=${android.net.Uri.encode(noteId)}"
                                navigateSafely(source + (goal?.takeIf { it.isNotBlank() }?.let {
                                    "&goal=${android.net.Uri.encode(it)}"
                                } ?: ""))
                            },
                            onReviewMemory = { id -> navigateSafely("notebook/memory?noteId=${android.net.Uri.encode(id)}") },
                        )
                    }
                }

                composable(
                    "notebook/study/{sessionId}?review={review}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }, navArgument("review") { type = NavType.BoolType; defaultValue = false })
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
                                lessons = ManualDependencyGraph.studyLessonStore,
                                workspace = ManualDependencyGraph.workspaceRepository,
                                notes = ManualDependencyGraph.notebookUseCases,
                                readerStorage = ManualDependencyGraph.storage,
                            ),
                        )
                        LaunchedEffect(studyViewModel) {
                            launch { studyViewModel.restartedLessons.collectLatest { id -> navigateSafely("notebook/study/${android.net.Uri.encode(id)}") } }
                            studyViewModel.savedNotes.collectLatest { id -> navigateSafely("notebook/note/${android.net.Uri.encode(id)}") }
                        }
                        StudyScreen(studyViewModel, startInReview = entry.arguments?.getBoolean("review") == true, onOpenSource = { id -> shellScope.launch {
                            val item = ManualDependencyGraph.workspaceUseCases.getObject(id)
                            val kind = if (item?.type == WorkspaceObjectType.NOTE) "note" else "file"
                            navigateSafely("notebook/$kind/${android.net.Uri.encode(id)}")
                        } }, onConfigureVision = { navigateSafely("settings/intelligence/roles") }) { navController.popBackStack() }
                    }
                }

                composable(
                    "notebook/canvas/{canvasId}?nodeId={nodeId}",
                    arguments = listOf(navArgument("canvasId") { type = NavType.StringType }, navArgument("nodeId") { type = NavType.StringType; nullable = true; defaultValue = null })
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
                                workspace = ManualDependencyGraph.workspaceUseCases,
                                memoTranscriber = ManualDependencyGraph.localMemoTranscriber,
                                permitsAi = ManualDependencyGraph.workspaceMediaAiPolicy::permitsObject,
                                router = ManualDependencyGraph.aiProviderRouter,
                                allowCloudSharing = { (!ManualDependencyGraph.kidsPreferences.enabled.value || ManualDependencyGraph.kidsPreferences.cloudAllowed.value) && ManualDependencyGraph.storage.readRuntimeAiContextPolicy().allowCloudWorkspace },
                                imageService = ManualDependencyGraph.workspaceImageService,
                                assets = ManualDependencyGraph.notebookAssetRepository,
                                notes = ManualDependencyGraph.notebookUseCases,
                                study = ManualDependencyGraph.studyUseCases,
                            ),
                        )
                        LaunchedEffect(canvasViewModel) {
                            canvasViewModel.navigationEvents.collectLatest { event ->
                                when (event) {
                                    is com.halo.app.ui.screens.notebook.CanvasEditorNavigation.OpenNote -> navigateSafely("notebook/note/${android.net.Uri.encode(event.id)}")
                                    is com.halo.app.ui.screens.notebook.CanvasEditorNavigation.OpenStudy -> navigateSafely("notebook/study/${android.net.Uri.encode(event.sessionId)}")
                                    is com.halo.app.ui.screens.notebook.CanvasEditorNavigation.StartStudyFromSource -> navigateSafely(
                                        "study?captureSource=${android.net.Uri.encode(event.noteId)}&goal=${android.net.Uri.encode(event.goal)}")
                                    is com.halo.app.ui.screens.notebook.CanvasEditorNavigation.OpenObject -> {
                                        val item = event.objectRef
                                        val kind = when (item.type) {
                                            WorkspaceObjectType.NOTE -> "note"
                                            WorkspaceObjectType.CANVAS -> "canvas"
                                            WorkspaceObjectType.CONVERSATION -> "conversation"
                                            WorkspaceObjectType.MEMORY -> "memory"
                                            else -> if (item.contentRef.startsWith("canvas:")) "canvas" else "file"
                                        }
                                        navigateSafely("notebook/$kind/${android.net.Uri.encode(item.id.value)}")
                                    }
                                }
                            }
                        }
                        CanvasEditorScreen(canvasViewModel, focusNodeId = entry.arguments?.getString("nodeId")) { navController.popBackStack() }
                    }
                }

                composable("notebook/file/{objectId}",
                    arguments = listOf(navArgument("objectId") { type = NavType.StringType })) { entry ->
                    entry.arguments?.getString("objectId")?.let { id ->
                        WorkspaceObjectDetailScreen(id, "file", ManualDependencyGraph.workspaceUseCases, onStudy = { sourceId ->
                            // Source preselection only; Study's own wizard owns
                            // the explicit device/cloud execution choice.
                            navigateSafely("study?captureSource=${android.net.Uri.encode(sourceId)}")
                        }) {
                            navController.popBackStack()
                        }
                    }
                }
                composable("notebook/memory?section={section}&noteId={noteId}", arguments = listOf(
                    navArgument("section") { type = NavType.StringType; defaultValue = "Memories" },
                    navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )) { entry ->
                    val hub: com.halo.app.ui.screens.notebook.MemoryHubViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = ManualDependencyGraph.memoryHubViewModel() as T
                    })
                    val noteId = entry.arguments?.getString("noteId")
                    val captureMemoryCandidate = remember(noteId) { ManualDependencyGraph.contextualCapture.takeMemoryCandidate() }
                    val noteReview by androidx.compose.runtime.produceState<Pair<Boolean, com.halo.app.data.memory.MemoryCandidate?>>(false to null, noteId) {
                        value = true to noteId?.let { ManualDependencyGraph.notebookRepository.getNote(it) }?.takeIf { it.lifecycle == com.halo.app.data.notebook.WorkspaceLifecycle.ACTIVE }?.let {
                            com.halo.app.data.memory.MemoryCandidate(it.body, com.halo.app.data.notebook.WorkspaceObjectId(it.id), it.revision)
                        }
                    }
                    LaunchedEffect(Unit) { ManualDependencyGraph.notebookViewModel.navigationEvents.collectLatest { event ->
                        when (event) {
                            is NotebookNavigation.ReviewAutomation -> navigateSafely("automation_review/${android.net.Uri.encode(event.routineId)}/${android.net.Uri.encode(event.suggestionId)}")
                            NotebookNavigation.ReviewCapabilities -> navigateSafely("ai_settings")
                            is NotebookNavigation.OpenMemory -> navigateSafely("notebook/memory/${event.id}")
                            is NotebookNavigation.OpenNote -> navigateSafely("notebook/note/${event.id}")
                            is NotebookNavigation.OpenFile -> navigateSafely("notebook/file/${event.id}")
                            else -> Unit
                        }
                    } }
                    if (noteId == null || noteReview.second != null) com.halo.app.ui.screens.notebook.MemoryHubScreen(hub, ManualDependencyGraph.notebookViewModel,
                        onBack = { navController.popBackStack() }, onOpenMemory = { navigateSafely("notebook/memory/$it") },
                        onSettings = { navigateSafely("memory_learning") }, initialSection = entry.arguments?.getString("section") ?: "Memories", initialCandidate = noteReview.second ?: captureMemoryCandidate)
                    else if (noteReview.first) androidx.compose.material3.TextButton({ navController.popBackStack() }) { Text(uiText("Original note is unavailable · Back")) }
                }
                composable("notebook/memory/{objectId}",
                    arguments = listOf(navArgument("objectId") { type = NavType.StringType })) { entry ->
                    entry.arguments?.getString("objectId")?.let { id ->
                        WorkspaceObjectDetailScreen(id, "memory", ManualDependencyGraph.workspaceUseCases,
                            onOpenSource = { item ->
                                val route = when (item.type) {
                                    WorkspaceObjectType.NOTE -> "note"
                                    WorkspaceObjectType.CONVERSATION -> "conversation"
                                    WorkspaceObjectType.MEMORY -> "memory"
                                    WorkspaceObjectType.CANVAS -> "canvas"
                                    WorkspaceObjectType.ARTIFACT -> if (item.contentRef.startsWith("canvas:")) "canvas" else "file"
                                    else -> "file"
                                }
                                navigateSafely("notebook/$route/${item.id.value}")
                            }) {
                            navController.popBackStack()
                        }
                    }
                }

                composable(Screen.Devices.route) {
                    val viewModel = ManualDependencyGraph.devicesViewModel
                    LaunchedEffect(viewModel) {
                        viewModel.navigationEvents.collectLatest(::navigateSafely)
                    }
                    DevicesScreen(viewModel, onTestAudio = { navigateSafely("settings/voice/output") })
                }

                composable("device/{deviceId}", arguments = listOf(navArgument("deviceId") { type = NavType.StringType })) { entry ->
                    val viewModel = ManualDependencyGraph.devicesViewModel
                    val deviceId = entry.arguments?.getString("deviceId")
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val deviceKind = state.devices.firstOrNull { it.id == deviceId }?.kind
                    when {
                        deviceId == null -> navController.popBackStack()
                        deviceKind == com.halo.sdk.runtime.embodiment.DeviceKind.BUDDYBOT -> BuddyBotControlScreen(deviceId, viewModel) { navController.popBackStack() }
                        deviceKind == com.halo.sdk.runtime.embodiment.DeviceKind.HEYCYAN_GLASSES -> HeyCyanControlScreen(deviceId, viewModel) { navController.popBackStack() }
                        else -> navController.popBackStack()
                    }
                }

                composable("integrations") {
                    com.halo.app.ui.screens.settings.ConnectedAppsScreen(onBack = { navController.popBackStack() },
                        onActions = { navigateSafely("skills") })
                }
                composable("advanced/servers") {
                    val model: com.halo.app.ui.screens.integrations.McpServersViewModel = viewModel(
                        factory = com.halo.app.ui.screens.integrations.McpServersViewModel.Factory(ManualDependencyGraph.mcpSettingsService))
                    com.halo.app.ui.screens.integrations.IntegrationsScreen(model,
                        onBack = { navController.popBackStack() }, onServer = { navigateSafely("advanced/servers/${android.net.Uri.encode(it)}") })
                }
                composable("advanced/servers/{serverId}", arguments = listOf(navArgument("serverId") { type = NavType.StringType })) { entry ->
                    val id = entry.arguments?.getString("serverId").orEmpty()
                    val model: com.halo.app.ui.screens.integrations.McpServerViewModel = viewModel(
                        key = "mcp:$id", factory = com.halo.app.ui.screens.integrations.McpServerViewModel.Factory(id, ManualDependencyGraph.mcpSettingsService))
                    com.halo.app.ui.screens.integrations.McpServerScreen(model, onBack = { navController.popBackStack() })
                }
                listOf("skills", "advanced/skills").forEach { skillRoute ->
                composable(skillRoute) {
                    SkillsScreen(
                        onIntegrations = { navigateSafely("integrations") },
                        appCapabilities = ManualDependencyGraph.appFunctionCapabilityIndex,
                        refreshAppCapabilities = { ManualDependencyGraph.appFunctionConsumer.refresh() },
                        screenControlAvailable = { com.halo.host.android.accessibility.HaloAccessibilityService.isEnabled },
                        providerRoleCheck = { skill ->
                            val capabilities = skill.requirements.filterIsInstance<com.halo.sdk.runtime.skills.SkillRequirement.ProviderCapability>().map { it.capability.uppercase() }
                            val role = when {
                                "vision" in skill.id.value || "IMAGE" in capabilities -> com.halo.sdk.provider.AiModelRole.VISION
                                skill.id.value == "halo.research" || "TOOLS" in capabilities -> com.halo.sdk.provider.AiModelRole.AGENTIC
                                else -> null
                            }
                            if (role == null) null else try {
                                kotlinx.coroutines.withTimeout(10_000) {
                                    ManualDependencyGraph.aiProviderRouter.resolveExecutionTarget(
                                        com.halo.sdk.provider.AiRequest(toolUseMode = if (role == com.halo.sdk.provider.AiModelRole.AGENTIC) com.halo.sdk.provider.ToolUseMode.REQUIRED else com.halo.sdk.provider.ToolUseMode.NONE),
                                        com.halo.sdk.provider.AiWorkloadSpec(
                                            requiredCapabilities = if (role == com.halo.sdk.provider.AiModelRole.VISION) setOf(com.halo.sdk.provider.AiCapability.TEXT, com.halo.sdk.provider.AiCapability.IMAGE) else setOf(com.halo.sdk.provider.AiCapability.TEXT),
                                            tools = if (role == com.halo.sdk.provider.AiModelRole.AGENTIC) com.halo.sdk.provider.AiFeatureRequirement.REQUIRED else com.halo.sdk.provider.AiFeatureRequirement.NOT_NEEDED,
                                            preferredRole = role))
                                }
                                null
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                                "Needs ${role.name} model setup"
                            } catch (_: Exception) { "Needs ${role.name} model setup" }
                        },
                        executionService = ManualDependencyGraph.skillExecutionService,
                        skillRegistry = ManualDependencyGraph.skillRegistry,
                        onBack = { navController.popBackStack() },
                        toolRegistry = ManualDependencyGraph.toolRegistry,
                        availabilityResolver = ManualDependencyGraph.toolAvailabilityResolver,
                        capabilityCatalogue = ManualDependencyGraph.capabilityCatalogue,
                        onInstallCustomSkill = ManualDependencyGraph::installCustomSkillPackage,
                        instructionSource = ManualDependencyGraph.skillInstructionSource,
                        technical = skillRoute == "advanced/skills",
                        packageIssues = ManualDependencyGraph.customSkillPackages.issues,
                        onLocalModels = { navigateSafely("settings/intelligence/on-device") },
                        onModelRoles = { navigateSafely("settings/intelligence/roles") },
                        capabilityStatus = ManualDependencyGraph.capabilityStatusViewModel,
                        onRequestSkillAccess = onRequestSkillPermissions,
                    )
                }
                }

                composable("workload_diagnostics") {
                    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp)) {
                        com.halo.app.ui.design.HaloScreenHeader(uiText("Workload diagnostics"), navigation = { navController.popBackStack() })
                        com.halo.app.ui.screens.settings.WorkloadDiagnosticsPanel(ManualDependencyGraph.aiProviderRouter)
                    }
                }
                composable("advanced_settings") {
                    val viewModel = ManualDependencyGraph.settingsViewModel
                    LaunchedEffect(viewModel) { viewModel.navigationEvents.collectLatest(::navigateSafely) }
                    SettingsScreen(viewModel, initialAdvanced = true, onBack = { navController.popBackStack() },
                        onSetup = { stage -> setupStore.open(stage); setupSession = true; navigateSafely("guided_setup") })
                }

                composable("runtime_status") {
                    RuntimeScreen(
                        projection = ManualDependencyGraph.runtimeUiProjection,
                        catalogueSource = ManualDependencyGraph.capabilityCatalogue,
                        onBack = { navController.popBackStack() },
                        onOpenSystemCheck = { navigateSafely("system_check") }
                    )
                }

                listOf("system_check", "technical_report", "developer_check").forEach { checkRoute ->
                composable(checkRoute) {
                    val context = LocalContext.current
                    val viewModel = remember(context) {
                        SystemCheckViewModel(
                            appContext = context.applicationContext,
                            providerRouter = ManualDependencyGraph.aiProviderRouter,
                            modelManager = ManualDependencyGraph.modelManager,
                            inferenceLedger = ManualDependencyGraph.inferenceLedger,
                            // RUNTIME-056: the Phone abilities list reports real descriptor
                            // availability instead of hardcoded "Available" rows.
                            toolRegistry = ManualDependencyGraph.toolRegistry,
                            toolAvailabilityResolver = ManualDependencyGraph.toolAvailabilityResolver,
                            remoteProviderRegistry = ManualDependencyGraph.providerGraph.remoteProviderPresenceRegistry,
                            remoteCredentialPresent = { key ->
                                !ManualDependencyGraph.storage.getString(key, null).isNullOrBlank()
                            },
                            // MINING-P0-001 native residency vs execution lease truth.
                            runtimeSnapshotSource = ManualDependencyGraph.runtimeSnapshotManager,
                            // MINING-P2-001 passive active-surface count for the presentation section.
                            presentationProjection = ManualDependencyGraph.presentationProjection,
                            payloadStore = ManualDependencyGraph.contentPayloadStore,
                            agentRuns = ManualDependencyGraph.agentRunStore,
                            speechDiagnostics = ManualDependencyGraph.speechDiagnosticRunner,
                            voicePipelineReport = { ManualDependencyGraph.voicePipelineTraceRecorder.report() },
                            localProfileSource = ManualDependencyGraph.localInferenceProfileSource,
                            modelProfileRepository = ManualDependencyGraph.modelRuntimeProfileRepository,
                            memoryPressure = { ManualDependencyGraph.providerGraph.nativeResourceCoordinator.budget.value.pressure },
                        )
                    }
                    if (checkRoute == "system_check") com.halo.app.ui.screens.settings.CheckUpScreen(
                        viewModel = viewModel, onBack = { navController.popBackStack() },
                        onIntelligence = { navigateSafely("ai_settings") },
                        onVoice = { navigateSafely("voice_settings") },
                    ) else if (checkRoute == "technical_report") com.halo.app.ui.screens.settings.TechnicalReportScreen(
                        viewModel = viewModel, onBack = { navController.popBackStack() },
                    ) else SystemCheckScreen(
                        viewModel = viewModel,
                        onOpenPhoneSandbox = { navigateSafely("phone_control_sandbox") },
                        onOpenAutomationSandbox = { navigateSafely("automation_sandbox") },
                        onOpenIntelligence = { navigateSafely("ai_settings") },
                        onOpenVoiceSettings = { navigateSafely("voice_settings") },
                        onBack = { navController.popBackStack() }
                    )
                }

                }
                composable("automation_sandbox") {
                    com.halo.app.ui.screens.settings.AutomationSandboxScreen(onBack = { navController.popBackStack() })
                }
                composable("phone_control_sandbox") {
                    com.halo.app.ui.screens.settings.PhoneControlSandboxScreen(onBack = { navController.popBackStack() })
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
                        val history by ManualDependencyGraph.conversationStore.history.collectAsStateWithLifecycle()
                        val conversations by ManualDependencyGraph.conversationStore.conversations.collectAsStateWithLifecycle()
                        // NB08: the raw history flow retains turns in Trash so
                        // they can be restored. A direct/deep-linked detail must
                        // revalidate the Workspace lifecycle before displaying them.
                        val available by androidx.compose.runtime.produceState<Boolean?>(
                            initialValue = null, key1 = conversationId, key2 = conversations
                        ) {
                            value = ManualDependencyGraph.conversationStore
                                .isConversationAvailable(conversationId)
                        }
                        when (available) {
                            null -> com.halo.app.ui.design.HaloProgressPresentation(
                                uiText("Loading…"))
                            false -> Column(
                                Modifier.fillMaxSize().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(uiText("Chat unavailable"),
                                    style = MaterialTheme.typography.titleLarge)
                                Text(uiText("This chat may be in Trash. Restore it from Notebook to read it again."),
                                    style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { navController.popBackStack() }) {
                                    Text(uiText("Back to Notebook"))
                                }
                            }
                            true -> {
                                val turns = history.filter { it.conversationId == conversationId }
                                ConversationDetailScreen(
                                    conversationId = conversationId,
                                    savedTitle = conversations.firstOrNull { it.id == conversationId }?.title,
                                    turns = turns,
                                    onContinue = {
                                        scope.launch {
                                            if (ManualDependencyGraph.conversationStore
                                                    .activateExistingConversation(conversationId)) {
                                                navController.navigateToPrimary("home")
                                            }
                                        }
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }

                composable(Screen.Settings.route) {
                    val viewModel = ManualDependencyGraph.settingsViewModel
                    LaunchedEffect(viewModel) { viewModel.navigationEvents.collectLatest(::navigateSafely) }
                    SettingsScreen(viewModel, onSetup = { stage -> setupStore.open(stage); setupSession = true; navigateSafely("guided_setup") })
                }

                composable("automation?routineId={routineId}", arguments = listOf(navArgument("routineId") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                    com.halo.app.ui.screens.settings.AutomationScreen(ManualDependencyGraph.routinesViewModel,
                        focusRoutineId = entry.arguments?.getString("routineId"), onBack = { navController.popBackStack() }, onNotifications = { navigateSafely("notification_settings") },
                        onActivity = { navigateSafely(Screen.Activity.route) }, onInbox = { navigateSafely("notifications") })
                }
                composable("automation_review/{routineId}/{suggestionId}") { entry ->
                    com.halo.app.ui.screens.settings.AutomationScreen(ManualDependencyGraph.routinesViewModel,
                        onBack={navController.popBackStack()},onNotifications={navigateSafely("notification_settings")},
                        onActivity={navigateSafely(Screen.Activity.route)},onInbox={navigateSafely("notifications")},
                        reviewRoutineId=entry.arguments?.getString("routineId"),
                        reviewSuggestionId=entry.arguments?.getString("suggestionId"))
                }
                composable("memory_learning") {
                    com.halo.app.ui.screens.settings.LearningSettingsScreen(onBack={navController.popBackStack()})
                }
                composable("notifications") {
                    val model: com.halo.app.ui.screens.notifications.NotificationInboxViewModel = viewModel(
                        factory = com.halo.app.ui.screens.notifications.NotificationInboxViewModel.Factory(ManualDependencyGraph.notificationInboxSource))
                    com.halo.app.ui.screens.notifications.NotificationInboxScreen(model,
                        onBack = { navController.popBackStack() }, onOpen = { navigateSafely("notifications/${android.net.Uri.encode(it)}") })
                }
                composable("notifications/{notificationKey}", arguments = listOf(navArgument("notificationKey") { type = NavType.StringType })) { entry ->
                    val key = entry.arguments?.getString("notificationKey").orEmpty()
                    val model: com.halo.app.ui.screens.notifications.NotificationDetailViewModel = viewModel(
                        key = "notification:$key", factory = com.halo.app.ui.screens.notifications.NotificationDetailViewModel.Factory(key,
                            ManualDependencyGraph.notificationInboxSource, ManualDependencyGraph.applicationContext, ManualDependencyGraph.notificationReplyDispatcher))
                    com.halo.app.ui.screens.notifications.NotificationDetailScreen(model,
                        onBack = { navController.popBackStack() }, onPrivacy = { navigateSafely("notification_settings") })
                }
                composable("notification_settings") {
                    com.halo.app.ui.screens.settings.NotificationSettingsScreen(ManualDependencyGraph.routinesViewModel,
                        onOpenInbox = { navigateSafely("notifications") }, onBack = { navController.popBackStack() })
                }
                composable("ai_settings") {
                    IntelligenceScreen(
                        control = ManualDependencyGraph.providerControlRepository,
                        onBack = { navController.popBackStack() },
                        onBrowseModels = { navigateSafely("settings/intelligence/models") },
                        onProviderSetup = { navigateSafely("settings/intelligence/providers") },
                        onRoles = { navigateSafely("settings/intelligence/roles") },
                        onDevice = { navigateSafely("settings/intelligence/on-device") },
                        onContext = { navigateSafely("settings/intelligence/context") },
                        onDiagnostics = { navigateSafely("system_check") },
                        onVisionLab = { navigateSafely("vision_lab") },
                        onAudioLab = { navigateSafely("local_audio_lab") },
                        onAdvanced = { navigateSafely("advanced_settings") },
                        onNotebookSearch = {
                            navigateSafely(Screen.Notebook.route)
                            navController.getBackStackEntry(Screen.Notebook.route).savedStateHandle["openLocalSearch"] = true
                        },
                        onUsage = { navigateSafely("settings/shared-requests") },
                    )
                }
                composable("local_audio_lab") {
                    val model: com.halo.app.ui.screens.settings.LocalAudioLabViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    com.halo.app.ui.screens.settings.LocalAudioLabScreen(model,
                        onBack = { navController.popBackStack() },
                        onModels = { navigateSafely("settings/intelligence/on-device") })
                }
                composable("vision_lab") {
                    val model: com.halo.app.ui.screens.settings.VisionLabViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    com.halo.app.ui.screens.settings.VisionLabScreen(model,
                        onBack = { navController.popBackStack() }, onPrivacy = { navigateSafely("settings/intelligence/context") },
                        onCamera = { navigateSafely("home") })
                }
                composable("provider_setup") {
                    com.halo.app.ui.screens.settings.ProviderCenterScreen(ManualDependencyGraph.providerControlRepository,
                        onBack = { navController.popBackStack() }, onProvider = { navigateSafely(if (it == com.halo.providers.litert.LiteRtLocalProvider.ProviderIds.LITERT) "settings/intelligence/on-device" else "settings/intelligence/providers/${android.net.Uri.encode(it)}") })
                }
                composable("settings/intelligence/providers") {
                    com.halo.app.ui.screens.settings.ProviderCenterScreen(ManualDependencyGraph.providerControlRepository,
                        onBack = { navController.popBackStack() }, onProvider = { navigateSafely(if (it == com.halo.providers.litert.LiteRtLocalProvider.ProviderIds.LITERT) "settings/intelligence/on-device" else "settings/intelligence/providers/${android.net.Uri.encode(it)}") })
                }
                composable("settings/intelligence/providers/{providerId}") { entry ->
                    com.halo.app.ui.screens.settings.ProviderDetailScreen(ManualDependencyGraph.providerControlRepository,
                        providerId = entry.arguments?.getString("providerId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onModels = { navigateSafely("settings/intelligence/models?providerId=${android.net.Uri.encode(it)}") },
                        onAssignRole = { navigateSafely("settings/intelligence/roles") },
                        onDevice = { navigateSafely("settings/intelligence/on-device") },
                        onImages = { navigateSafely("settings/intelligence/images/${android.net.Uri.encode(entry.arguments?.getString("providerId").orEmpty())}") },
                        onDiagnostics = { navigateSafely("system_check") })
                }
                composable("settings/intelligence/images/{providerId}") { entry ->
                    val provider = entry.arguments?.getString("providerId").orEmpty()
                    com.halo.app.ui.screens.settings.ImageModelsScreen(ManualDependencyGraph.workspaceImageService, provider,
                        onBack = { navController.popBackStack() }, onSetup = { navigateSafely("settings/intelligence/providers/${android.net.Uri.encode(provider)}") })
                }
                composable("model_browser") {
                    com.halo.app.ui.screens.settings.ModelBrowserScreen(ManualDependencyGraph.modelBrowserViewModel,
                        onBack = { navController.popBackStack() }, onProvider = { navigateSafely(if (it == com.halo.providers.litert.LiteRtLocalProvider.ProviderIds.LITERT) "settings/intelligence/on-device" else "settings/intelligence/providers/${android.net.Uri.encode(it)}") })
                }
                composable("settings/intelligence/models?providerId={providerId}&role={role}", arguments = listOf(
                    androidx.navigation.navArgument("providerId") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
                    androidx.navigation.navArgument("role") { type = androidx.navigation.NavType.StringType; defaultValue = "" })) { entry ->
                    com.halo.app.ui.screens.settings.ModelBrowserScreen(ManualDependencyGraph.modelBrowserViewModel,
                        onBack = { navController.popBackStack() }, onProvider = { navigateSafely(if (it == com.halo.providers.litert.LiteRtLocalProvider.ProviderIds.LITERT) "settings/intelligence/on-device" else "settings/intelligence/providers/${android.net.Uri.encode(it)}") },
                        initialProvider = entry.arguments?.getString("providerId")?.takeIf { it.isNotBlank() },
                        initialRole = entry.arguments?.getString("role")?.let { raw -> com.halo.sdk.provider.AiModelRole.entries.firstOrNull { it.name == raw } })
                }
                composable("settings/shared-requests") {
                    com.halo.app.ui.screens.settings.SharedAnswerHistoryScreen(ManualDependencyGraph.sharedAnswerHistory,
                        onBack = { navController.popBackStack() })
                }
                composable("settings/intelligence/usage") {
                    com.halo.app.ui.screens.settings.IntelligenceUsageScreen(ManualDependencyGraph.inferenceLedger,
                        onBack = { navController.popBackStack() }, onDiagnostics = { navigateSafely("system_check") })
                }
                composable("settings/intelligence/roles") {
                    com.halo.app.ui.screens.settings.ModelRolesScreen(ManualDependencyGraph.providerControlRepository,
                        onBack = { navController.popBackStack() }, onPick = { navigateSafely("settings/intelligence/models?role=${it.name}") },
                        evidence = ManualDependencyGraph.localRoleEvidenceController,
                        onExplore = { ManualDependencyGraph.intelligenceViewModel.exploreLocalCandidates(it); navigateSafely("settings/intelligence/on-device") })
                }
                composable("settings/intelligence/on-device") {
                    com.halo.app.ui.screens.settings.OnDeviceModelsScreen(ManualDependencyGraph.intelligenceViewModel, onBack = { navController.popBackStack() }, onConfigureRoles = { navigateSafely("settings/intelligence/roles") }, onAudioLab = { navigateSafely("local_audio_lab") }, onVisionLab = { navigateSafely("vision_lab") }, onLocalSearch = { navigateSafely(Screen.Notebook.route) })
                }
                composable("settings/intelligence/context") {
                    com.halo.app.ui.screens.settings.ContextPrivacyScreen(ManualDependencyGraph.contextPrivacyViewModel,
                        onBack = { navController.popBackStack() },
                        onAdvanced = { navigateSafely("settings/intelligence/policy") },
                        onPermissions = { navigateSafely("permissions") })
                }
                composable("settings/voice/input") {
                    com.halo.app.ui.screens.settings.SpeechRecognitionSettingsScreen(ManualDependencyGraph.voiceSettingsViewModel,
                        onBack = { navController.popBackStack() }, onSpeechLab = { navigateSafely("settings/voice/speech-lab") })
                }
                composable("settings/voice/speech-lab") {
                    val lab = remember { com.halo.app.ui.screens.settings.SpeechLabViewModel(ManualDependencyGraph.speechDiagnosticRunner) }
                    com.halo.app.ui.screens.settings.SpeechLabScreen(lab, onBack = { navController.popBackStack() })
                }
                listOf("settings/voice/output", "advanced/voices").forEach { voiceRoute ->
                composable(voiceRoute) {
                    com.halo.app.ui.screens.settings.SpeechOutputSettings(ManualDependencyGraph.voiceSettingsViewModel,
                        technical = voiceRoute == "advanced/voices",
                        onElevenLabs = { navigateSafely("settings/voice/elevenlabs") }, onBack = { navController.popBackStack() })
                }
                }
                composable("settings/intelligence/policy") {
                    com.halo.app.ui.screens.settings.IntelligencePolicyScreen(ManualDependencyGraph.intelligenceViewModel,
                        onBack = { navController.popBackStack() }, onBrowseModels = { navigateSafely("settings/intelligence/on-device") })
                }
                composable("settings/voice/elevenlabs") {
                    val provider = ManualDependencyGraph.speechSynthesisRegistry.providers.filterIsInstance<com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider>().first()
                    com.halo.app.ui.screens.settings.ElevenLabsSettingsScreen(provider,
                        onBack = { navController.popBackStack() },
                        onTestVoice = { ManualDependencyGraph.haloSpeechOutputCoordinator.preview(provider.metadata.id) },
                        onUseProvider = { ManualDependencyGraph.speechSynthesisRegistry.selector.selectTts(provider.metadata.id) })
                }
                composable("assistant_diagnostics") {
                    AndroidAssistantScreen(ManualDependencyGraph.assistantRoleController,
                        audioAffinity = ManualDependencyGraph.haloSpeechOutputCoordinator.session.affinity,
                        showDiagnostics = true, onBack = { navController.popBackStack() })
                }
                composable("android_assistant") {
                    AndroidAssistantScreen(
                        ManualDependencyGraph.assistantRoleController,
                        // MINING-P0-001 interaction audio affinity: the coordinator that owns the
                        // audio leases owns this record, so it stays the single source of truth.
                        audioAffinity = ManualDependencyGraph.haloSpeechOutputCoordinator.session.affinity,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("voice_settings") {
                    val viewModel = ManualDependencyGraph.voiceSettingsViewModel
                    LaunchedEffect(viewModel) {
                        viewModel.navigationEvents.collectLatest { if (it == "back") navController.popBackStack() }
                    }
                    VoiceSettingsScreen(viewModel, onBack = { navController.popBackStack() }, onSpeechOutput = { navigateSafely("settings/voice/output") },
                        onSpeechRecognition = { navigateSafely("settings/voice/input") })
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
            } // SharedTransitionLayout; external Activity cannot share across windows
        }
        }
    }
    }
    }
}
