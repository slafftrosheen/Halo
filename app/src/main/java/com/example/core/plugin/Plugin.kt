package com.example.core.plugin

interface Plugin {
    val id: String
    fun initialize()
    fun intents()
    fun execute()
}
