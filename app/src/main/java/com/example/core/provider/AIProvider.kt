package com.example.core.provider

interface AIProvider {
    suspend fun chat()
    suspend fun summarize()
    suspend fun vision()
    suspend fun embeddings()
}
