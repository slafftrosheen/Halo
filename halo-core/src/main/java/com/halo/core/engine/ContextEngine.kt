package com.halo.core.engine

import kotlinx.coroutines.flow.StateFlow

interface ContextEngine : Engine {
    val context: StateFlow<ContextSnapshot>
}

interface ContextSnapshot
