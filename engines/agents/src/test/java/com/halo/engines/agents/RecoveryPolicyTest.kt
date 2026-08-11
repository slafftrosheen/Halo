package com.halo.engines.agents

import com.halo.icl.ontology.ConversationPayload
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.engines.reasoning.service.GoalManager
import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.RecoveryAction
import com.halo.runtime.cognition.RecoveryContext
import com.halo.runtime.cognition.Task
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPolicyTest {

    private val recoveryPolicy = DefaultRecoveryPolicy()
    
    private fun createTestContext(attempt: Int = 1): RecoveryContext {
        val goal = GoalManager().createGoal(
            type = GoalType.CONVERSATION,
            intent = null,
            payload = ConversationPayload("test")
        )
        val plan = ExecutionPlan(
            goal = goal,
            tasks = emptyList()
        )
        return RecoveryContext(
            goal = goal,
            plan = plan,
            task = null,
            attempt = attempt,
            deviceState = "idle",
            networkState = "connected"
        )
    }

    @Test
    fun testRetryableFailureWithLowAttemptReturnsRetryExecution() = runTest {
        val failure = Outcome.RetryableFailure("Network timeout", null, retryCount = 0)
        val context = createTestContext(attempt = 1)
        
        val action = recoveryPolicy.determineRecovery(failure, context)
        
        assertTrue("Should return RetryExecution for low attempt count", action is RecoveryAction.RetryExecution)
        val retryAction = action as RecoveryAction.RetryExecution
        assertEquals("Should have 500ms delay", 500L, retryAction.delayMs)
    }

    @Test
    fun testRetryableFailureWithHighAttemptReturnsNotifyUser() = runTest {
        val failure = Outcome.RetryableFailure("Network timeout", null, retryCount = 0)
        val context = createTestContext(attempt = 3)
        
        val action = recoveryPolicy.determineRecovery(failure, context)
        
        assertTrue("Should return NotifyUser for high attempt count", action is RecoveryAction.NotifyUser)
        val notifyAction = action as RecoveryAction.NotifyUser
        assertTrue("Message should mention attempt count", notifyAction.message.contains("3 attempts"))
    }

    @Test
    fun testPermanentFailureReturnsNotifyUser() = runTest {
        val failure = Outcome.PermanentFailure("App not found", null)
        val context = createTestContext()
        
        val action = recoveryPolicy.determineRecovery(failure, context)
        
        assertTrue("Should return NotifyUser for permanent failure", action is RecoveryAction.NotifyUser)
        val notifyAction = action as RecoveryAction.NotifyUser
        assertEquals("App not found", notifyAction.message)
    }

    @Test
    fun testCancelledReturnsAbortToIdle() = runTest {
        val failure = Outcome.Cancelled("User cancelled")
        val context = createTestContext()
        
        val action = recoveryPolicy.determineRecovery(failure, context)
        
        assertEquals("Should return AbortToIdle for cancelled", RecoveryAction.AbortToIdle, action)
    }

    @Test
    fun testUnknownOutcomeReturnsAbortToIdle() = runTest {
        val failure = Outcome.Success("Unexpected success")
        val context = createTestContext()
        
        val action = recoveryPolicy.determineRecovery(failure, context)
        
        assertEquals("Should return AbortToIdle for unknown outcome", RecoveryAction.AbortToIdle, action)
    }

    @Test
    fun testRetryActionContainsDelay() = runTest {
        val failure = Outcome.RetryableFailure("Temporary failure", null, retryCount = 0)
        val context = createTestContext(attempt = 1)
        
        val action = recoveryPolicy.determineRecovery(failure, context) as RecoveryAction.RetryExecution
        
        assertNotNull(action)
        assertTrue("Delay should be positive", action.delayMs > 0)
    }
}
