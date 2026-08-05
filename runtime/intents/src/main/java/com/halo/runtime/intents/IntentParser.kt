package com.halo.runtime.intents

import com.halo.sdk.runtime.*

interface IntentParser {
    suspend fun parse(text: String): HaloIntent
}

class DefaultIntentParser : IntentParser {
    override suspend fun parse(text: String): HaloIntent {
        val input = text.lowercase()

        return when {
            input.startsWith("call ") ->
                CallContactIntent(input.removePrefix("call ").trim())
            input.startsWith("search ") ->
                SearchIntent(input.removePrefix("search ").trim())
            input.startsWith("open ") ->
                OpenApplicationIntent(input.removePrefix("open ").trim())
            else ->
                UnknownIntent
        }
    }
}
