package com.halo.host.android.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AssistantRoleState {
    data object Unsupported : AssistantRoleState
    data object Available : AssistantRoleState
    data object Active : AssistantRoleState
}

interface AssistantRoleController {
    val state: StateFlow<AssistantRoleState>
    fun refresh()
    fun createRoleRequestIntent(): Intent?
}

class AndroidAssistantRoleController(context: Context) : AssistantRoleController {
    private val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.applicationContext.getSystemService(RoleManager::class.java)
    } else null
    private val mutableState = MutableStateFlow<AssistantRoleState>(AssistantRoleState.Unsupported)
    override val state: StateFlow<AssistantRoleState> = mutableState.asStateFlow()

    init { refresh() }

    override fun refresh() {
        val manager = roleManager
        mutableState.value = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || manager == null -> AssistantRoleState.Unsupported
            !manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) -> AssistantRoleState.Unsupported
            manager.isRoleHeld(RoleManager.ROLE_ASSISTANT) -> AssistantRoleState.Active
            else -> AssistantRoleState.Available
        }
    }

    override fun createRoleRequestIntent(): Intent? {
        val manager = roleManager ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return null
        return manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
    }
}
