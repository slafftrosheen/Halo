package com.halo.app.ui.screens.home

import com.halo.app.R
import com.halo.app.online.OnlineProblem
import com.halo.app.ui.icons.HaloGlyph

enum class HomeToolGroup(val title: Int) {
    CREATE(R.string.home_tools_create), FIND(R.string.home_tools_find),
    WORK(R.string.home_tools_work), PHONE(R.string.home_tools_phone),
}

enum class HomeToolAction {
    NEW_NOTE, ATTACH, WEB, RESEARCH_DRAFT, MAPS_DRAFT, NOTEBOOK, STUDY,
    TIMER, OPEN_APP, CONNECTIONS, VOICE_MODE, START_VOICE, END_VOICE, INBOX, REPEAT_VOICE, PLANNED,
}

enum class HomeToolStatus(val label: Int) {
    READY(R.string.home_tool_ready), NEEDS_SETUP(R.string.home_tool_needs_setup),
    LIMITED(R.string.home_tool_limited), IN_DEVELOPMENT(R.string.home_tool_development),
}

enum class HomeToolRecovery { NONE, ANSWERS, PERMISSIONS, DEVICES, SETTINGS, SKILLS, VOICE, NOTEBOOK, ANDROID_APPS }
enum class HomeToolReason(val text: Int) {
    READY(R.string.home_tool_ready_detail), UNAVAILABLE(R.string.home_tool_unavailable),
    ACCESS(R.string.home_tool_access), DEVICE(R.string.home_tool_device), DISABLED(R.string.home_tool_disabled),
    POWER(R.string.home_tool_power), CHILD(R.string.home_tool_child), OFFLINE(R.string.home_tool_offline),
    ANSWERS(R.string.home_tool_answers), UNCHECKED(R.string.home_tool_unchecked),
    MAP_APP(R.string.home_tool_map_app), HANDOFF(R.string.home_tool_handoff),
    EDITOR(R.string.home_tool_editor), IMAGE_REVIEW(R.string.home_tool_image_review),
    IMAGE_SETUP(R.string.home_tool_image_setup),
    WEB_KEY(R.string.home_tool_web_key), WEB_LIMIT(R.string.home_tool_web_limit),
    RESEARCH_OFF(R.string.home_tool_research_off), VOICE(R.string.home_tool_voice_setup),
    BUSY(R.string.home_tool_busy),
    PYTHON(R.string.home_tool_python_future), CALCULATOR(R.string.home_tool_calculator_future),
}

enum class HomeAnswerReadiness { READY, UNCHECKED, NEEDS_SETUP, OFFLINE }
data class HomeRuntimeToolReadiness(
    val available: Boolean,
    val reason: HomeToolReason = HomeToolReason.UNAVAILABLE,
    val recovery: HomeToolRecovery = HomeToolRecovery.SETTINGS,
)

/** Host presentation bindings only. Runtime ToolDescriptor and its authorizer remain authoritative. */
data class HomeToolDescriptor(
    val id: String,
    val group: HomeToolGroup,
    val title: Int,
    val description: Int,
    val action: HomeToolAction,
    val toolIds: Set<String> = emptySet(),
    val needsAnswers: Boolean = false,
    val onlineOnly: Boolean = false,
    val editorOnly: Boolean = false,
    val externalHandoff: Boolean = false,
    val icon: HaloGlyph = when (action) {
        HomeToolAction.NEW_NOTE, HomeToolAction.NOTEBOOK, HomeToolAction.INBOX -> HaloGlyph.NOTE
        HomeToolAction.ATTACH -> HaloGlyph.SEE
        HomeToolAction.WEB, HomeToolAction.RESEARCH_DRAFT -> HaloGlyph.SEARCH
        HomeToolAction.MAPS_DRAFT, HomeToolAction.OPEN_APP -> HaloGlyph.OPEN_APP
        HomeToolAction.STUDY -> HaloGlyph.QUESTION
        HomeToolAction.TIMER -> HaloGlyph.AUTOMATION
        HomeToolAction.CONNECTIONS -> HaloGlyph.INTEGRATIONS
        HomeToolAction.VOICE_MODE, HomeToolAction.START_VOICE, HomeToolAction.END_VOICE -> HaloGlyph.LISTEN
        HomeToolAction.REPEAT_VOICE -> HaloGlyph.SPEAK
        HomeToolAction.PLANNED -> HaloGlyph.TOOL
    },
)

data class HomeToolEntry(
    val descriptor: HomeToolDescriptor,
    val status: HomeToolStatus,
    val reason: HomeToolReason,
    val recovery: HomeToolRecovery = HomeToolRecovery.NONE,
) {
    val canUse get() = status in setOf(HomeToolStatus.READY, HomeToolStatus.LIMITED) &&
        descriptor.action != HomeToolAction.PLANNED
}

data class HomeToolCatalogInput(
    val runtimeTools: Map<String, HomeRuntimeToolReadiness> = emptyMap(),
    val privateMode: Boolean = true,
    val onlineAllowedByFamily: Boolean = false,
    val connected: Boolean = false,
    val answerReadiness: HomeAnswerReadiness = HomeAnswerReadiness.NEEDS_SETUP,
    val answerOffDevice: Boolean = false,
    val answerRequiresInternet: Boolean = false,
    val webProblem: OnlineProblem? = OnlineProblem.UNAVAILABLE,
    val mapHandler: Boolean = false,
    val researchEnabled: Boolean = false,
    val speechReady: Boolean = false,
    val microphoneGranted: Boolean = false,
    val voiceModeActive: Boolean = false,
    val voiceStartAllowed: Boolean = true,
    val attachmentsAvailable: Boolean = false,
    val imageConfigured: Boolean = false,
    val answerProviderName: String? = null,
    val repeatVoiceAvailable: Boolean = false,
)

data class HomeToolCatalogRequest(val voiceModeActive: Boolean, val repeatVoiceAvailable: Boolean,
    val voiceStartAllowed: Boolean = true)

data class HomeToolCatalogSnapshot(val entries: List<HomeToolEntry> = emptyList(), val loading: Boolean = true,
    val answerProviderName: String? = null, val answerOffDevice: Boolean = false)

internal object HomeToolCatalog {
    val descriptors = listOf(
        HomeToolDescriptor("create.note", HomeToolGroup.CREATE, R.string.home_tool_note, R.string.home_tool_note_detail,
            HomeToolAction.NEW_NOTE, setOf("halo_create_empty_note")),
        HomeToolDescriptor("create.image", HomeToolGroup.CREATE, R.string.home_tool_image_create, R.string.home_tool_image_detail,
            HomeToolAction.NOTEBOOK, setOf("halo_open_screen"), onlineOnly = true, editorOnly = true),
        HomeToolDescriptor("create.image_edit", HomeToolGroup.CREATE, R.string.home_tool_image_edit, R.string.home_tool_image_detail,
            HomeToolAction.NOTEBOOK, setOf("halo_open_screen"), onlineOnly = true, editorOnly = true),
        HomeToolDescriptor("find.web", HomeToolGroup.FIND, R.string.online_title, R.string.home_tool_web_detail, HomeToolAction.WEB, onlineOnly = true),
        HomeToolDescriptor("find.research", HomeToolGroup.FIND, R.string.home_tool_research, R.string.home_tool_research_detail,
            HomeToolAction.RESEARCH_DRAFT, setOf("halo_research"), needsAnswers = true),
        HomeToolDescriptor("find.maps", HomeToolGroup.FIND, R.string.home_tool_maps, R.string.home_tool_maps_detail,
            HomeToolAction.MAPS_DRAFT, setOf("halo_native_map"), needsAnswers = true, externalHandoff = true),
        HomeToolDescriptor("work.notebook", HomeToolGroup.WORK, R.string.home_tool_notebook, R.string.home_tool_notebook_detail,
            HomeToolAction.NOTEBOOK, setOf("halo_open_screen")),
        HomeToolDescriptor("work.inbox", HomeToolGroup.WORK, R.string.home_tool_inbox, R.string.home_tool_inbox_detail,
            HomeToolAction.INBOX, setOf("halo_create_empty_note")),
        HomeToolDescriptor("work.canvas", HomeToolGroup.WORK, R.string.home_tool_canvas, R.string.home_tool_canvas_detail,
            HomeToolAction.NOTEBOOK, setOf("halo_open_screen"), editorOnly = true),
        HomeToolDescriptor("work.study", HomeToolGroup.WORK, R.string.home_tool_study, R.string.home_tool_study_detail,
            HomeToolAction.STUDY, setOf("halo_open_screen")),
        HomeToolDescriptor("work.image_read", HomeToolGroup.WORK, R.string.home_tool_image_read, R.string.home_tool_image_read_detail,
            HomeToolAction.ATTACH),
        HomeToolDescriptor("work.files", HomeToolGroup.WORK, R.string.home_tool_files, R.string.home_tool_files_detail,
            HomeToolAction.NOTEBOOK, setOf("halo_search_files"), editorOnly = true),
        HomeToolDescriptor("work.calculate", HomeToolGroup.WORK, R.string.home_tool_calculator, R.string.home_tool_calculator_future,
            HomeToolAction.PLANNED),
        HomeToolDescriptor("work.python", HomeToolGroup.WORK, R.string.home_tool_python, R.string.home_tool_python_future,
            HomeToolAction.PLANNED),
        HomeToolDescriptor("phone.timer", HomeToolGroup.PHONE, R.string.home_tool_timer, R.string.home_tool_timer_detail,
            HomeToolAction.TIMER, setOf("halo_timer"), needsAnswers = true),
        HomeToolDescriptor("phone.apps", HomeToolGroup.PHONE, R.string.home_tool_apps, R.string.home_tool_apps_detail,
            HomeToolAction.OPEN_APP, setOf("halo_open_app"), needsAnswers = true),
        HomeToolDescriptor("phone.connections", HomeToolGroup.PHONE, R.string.home_tool_connections, R.string.home_tool_connections_detail,
            HomeToolAction.CONNECTIONS, setOf("halo_open_screen")),
        HomeToolDescriptor("phone.voice", HomeToolGroup.PHONE, R.string.home_tool_voice, R.string.home_tool_voice_detail,
            HomeToolAction.VOICE_MODE, needsAnswers = true),
        HomeToolDescriptor("phone.repeat", HomeToolGroup.PHONE, R.string.home_tool_repeat, R.string.home_tool_repeat_detail,
            HomeToolAction.REPEAT_VOICE),
    )

    fun project(input: HomeToolCatalogInput): HomeToolCatalogSnapshot = HomeToolCatalogSnapshot(
        descriptors.filter { it.action != HomeToolAction.REPEAT_VOICE || input.repeatVoiceAvailable }
            .map { descriptor -> entry(descriptor, input) }, loading = false,
        answerProviderName = input.answerProviderName, answerOffDevice = input.answerOffDevice)

    private fun entry(d: HomeToolDescriptor, s: HomeToolCatalogInput): HomeToolEntry {
        fun setup(reason: HomeToolReason, recovery: HomeToolRecovery) = HomeToolEntry(d, HomeToolStatus.NEEDS_SETUP, reason, recovery)
        fun limited(reason: HomeToolReason) = HomeToolEntry(d, HomeToolStatus.LIMITED, reason)
        if (d.action == HomeToolAction.PLANNED) return HomeToolEntry(d, HomeToolStatus.IN_DEVELOPMENT,
            if (d.id == "work.python") HomeToolReason.PYTHON else HomeToolReason.CALCULATOR)
        if (d.action == HomeToolAction.VOICE_MODE) {
            if (s.voiceModeActive) return HomeToolEntry(d, HomeToolStatus.READY, HomeToolReason.READY)
            if (!s.voiceStartAllowed) return setup(HomeToolReason.BUSY, HomeToolRecovery.NONE)
            if (!s.microphoneGranted) return setup(HomeToolReason.ACCESS, HomeToolRecovery.PERMISSIONS)
            if (!s.speechReady) return setup(HomeToolReason.VOICE, HomeToolRecovery.VOICE)
        }
        if (d.needsAnswers && s.answerOffDevice && s.privateMode && !d.onlineOnly && !d.externalHandoff)
            return setup(HomeToolReason.ANSWERS, HomeToolRecovery.ANSWERS)
        if (d.onlineOnly || d.externalHandoff || (d.needsAnswers && s.answerOffDevice)) {
            if (!s.onlineAllowedByFamily) return setup(HomeToolReason.CHILD, HomeToolRecovery.SETTINGS)
            if (s.privateMode) return setup(HomeToolReason.POWER, HomeToolRecovery.SETTINGS)
        }
        if ((d.onlineOnly || (d.needsAnswers && s.answerRequiresInternet)) && !s.connected)
            return setup(HomeToolReason.OFFLINE, HomeToolRecovery.NONE)
        if (d.id.startsWith("create.image") && !s.imageConfigured)
            return setup(HomeToolReason.IMAGE_SETUP, HomeToolRecovery.NOTEBOOK)
        if (d.action == HomeToolAction.WEB) return when (s.webProblem) {
            null -> limited(HomeToolReason.WEB_LIMIT)
            OnlineProblem.KEY_REQUIRED -> setup(HomeToolReason.WEB_KEY, HomeToolRecovery.ANSWERS)
            OnlineProblem.LOCAL_ONLY -> setup(HomeToolReason.POWER, HomeToolRecovery.SETTINGS)
            OnlineProblem.CHILD_RESTRICTED -> setup(HomeToolReason.CHILD, HomeToolRecovery.SETTINGS)
            else -> setup(HomeToolReason.UNAVAILABLE, HomeToolRecovery.ANSWERS)
        }
        if (d.action == HomeToolAction.ATTACH) return if (s.attachmentsAvailable) limited(HomeToolReason.IMAGE_REVIEW)
            else setup(HomeToolReason.UNAVAILABLE, HomeToolRecovery.SETTINGS)
        if (d.toolIds.isNotEmpty()) {
            val states = d.toolIds.map { s.runtimeTools[it] ?: HomeRuntimeToolReadiness(false) }
            if (states.none { it.available }) return setup(states.first().reason, states.first().recovery)
        }
        if (d.action == HomeToolAction.MAPS_DRAFT && !s.mapHandler) return setup(HomeToolReason.MAP_APP, HomeToolRecovery.ANDROID_APPS)
        if (d.action == HomeToolAction.RESEARCH_DRAFT && !s.researchEnabled) return setup(HomeToolReason.RESEARCH_OFF, HomeToolRecovery.SETTINGS)
        if (d.needsAnswers) when (s.answerReadiness) {
            HomeAnswerReadiness.NEEDS_SETUP -> return setup(HomeToolReason.ANSWERS, HomeToolRecovery.ANSWERS)
            HomeAnswerReadiness.OFFLINE -> return setup(HomeToolReason.OFFLINE, HomeToolRecovery.ANSWERS)
            HomeAnswerReadiness.UNCHECKED -> return limited(HomeToolReason.UNCHECKED)
            HomeAnswerReadiness.READY -> Unit
        }
        if (d.editorOnly) return limited(HomeToolReason.EDITOR)
        if (d.action == HomeToolAction.MAPS_DRAFT) return limited(HomeToolReason.HANDOFF)
        return HomeToolEntry(d, HomeToolStatus.READY, HomeToolReason.READY)
    }
}
