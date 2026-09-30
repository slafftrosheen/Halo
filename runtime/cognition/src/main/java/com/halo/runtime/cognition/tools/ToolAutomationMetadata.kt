package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.routine.AutomationPermission

/** Unattended execution is explicit, background compatible and safe to persist by contract. */
data class ToolAutomationMetadata(
    val permission: AutomationPermission = AutomationPermission.DISALLOWED,
    val backgroundCompatible: Boolean = false,
    val argumentsPersistable: Boolean = false,
    val requiresModel: Boolean = false,
)
