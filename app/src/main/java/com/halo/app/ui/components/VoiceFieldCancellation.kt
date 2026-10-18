package com.halo.app.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The visible Stop action also cancels editor dictation and uncommitted speech previews. */
object VoiceFieldCancellation {
    private val mutable = MutableStateFlow(0L)
    val epoch = mutable.asStateFlow()
    private val fields = MutableStateFlow<Set<String>>(emptySet())
    val activeFields = fields.asStateFlow()
    internal fun active(id: String, active: Boolean) {
        fields.update { if (active) it + id else it - id }
    }
    fun stop() { mutable.update { it + 1L } }
}
