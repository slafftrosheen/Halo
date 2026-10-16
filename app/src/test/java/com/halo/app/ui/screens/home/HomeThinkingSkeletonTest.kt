package com.halo.app.ui.screens.home

import com.halo.sdk.provider.CognitiveSessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeThinkingSkeletonTest {
    @Test fun onlyRealProviderWorkWithNoAnswerCanShimmer() {
        assertTrue(homeShouldShowThinkingSkeleton(CognitiveSessionState.PreparingContext, "", false))
        assertTrue(homeShouldShowThinkingSkeleton(CognitiveSessionState.Reasoning("litert"), "", false))
        assertFalse(homeShouldShowThinkingSkeleton(CognitiveSessionState.Reasoning("litert"), "answer", false))
        assertFalse(homeShouldShowThinkingSkeleton(CognitiveSessionState.Idle, "", false))
        assertFalse(homeShouldShowThinkingSkeleton(CognitiveSessionState.Reasoning("cloud"), "", true))
        assertFalse(homeShouldShowThinkingSkeleton(CognitiveSessionState.ExecutingTool("halo_timer"), "", false))
    }
}
