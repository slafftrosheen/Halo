package com.halo.runtime.kernel.service

class PermissionManager {
    private val permissions = mutableMapOf<String, Set<String>>()

    fun grant(agentId: String, permission: String) {
        val current = permissions.getOrDefault(agentId, emptySet())
        permissions[agentId] = current + permission
    }

    fun hasPermission(agentId: String, permission: String): Boolean {
        return permissions.getOrDefault(agentId, emptySet()).contains(permission)
    }

    fun getPermissions(agentId: String): Set<String> {
        return permissions.getOrDefault(agentId, emptySet())
    }
}
