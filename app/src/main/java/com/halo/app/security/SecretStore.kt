package com.halo.app.security

interface SecretStore {
    suspend fun put(key: String, value: String?)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
}
