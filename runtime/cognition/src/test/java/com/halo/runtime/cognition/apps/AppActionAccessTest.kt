package com.halo.runtime.cognition.apps

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AppActionAccessTest {
    private val action = SemanticActionId("archive_current")
    private fun capability(route: AppOperationRoute) = SemanticAppCapability(route.name, "example.mail", action,
        "Archive", null, emptyList(), route)
    private fun operation() = SemanticAppOperation("interaction", AppTarget.CurrentApp, action)

    @Test fun deniedActionsAreExcludedFromDiscoveryAndResolution() {
        val index = AppFunctionCapabilityIndex { _, _ -> false }
        index.publish(AppFunctionAvailability.AVAILABLE, "example.mail", null, listOf(capability(AppOperationRoute.APP_FUNCTION)))
        assertTrue(index.currentCandidates().isEmpty())
        val broker = SemanticAppActionBroker(index, executors = emptyMap(), authorize = { true })
        assertTrue(broker.resolve(operation()) is SemanticOperationResolution.NeedsUser)
    }

    @Test fun revocationAfterApprovalStopsDispatchAndFallback() = runTest {
        var allowed = true
        var calls = 0
        val index = AppFunctionCapabilityIndex { _, _ -> allowed }
        index.publish(AppFunctionAvailability.AVAILABLE, "example.mail", null,
            listOf(capability(AppOperationRoute.APP_FUNCTION), capability(AppOperationRoute.DIRECT_ACTION)))
        val executor = SemanticRouteExecutor { _, _, mark -> calls++; mark();
            SemanticRouteOutcome(DispatchTruth.DISPATCHED, EffectTruth.UNCONFIRMED) }
        val broker = SemanticAppActionBroker(index, executors = mapOf(AppOperationRoute.APP_FUNCTION to executor,
            AppOperationRoute.DIRECT_ACTION to executor), authorize = { allowed = false; true })
        val prepared = (broker.resolve(operation()) as SemanticOperationResolution.Prepared).value
        val receipt = broker.executePrepared(prepared)
        assertEquals(0, calls)
        assertEquals(SemanticOperationFailure.DISABLED, receipt.failure)
        assertEquals(DispatchTruth.NOT_DISPATCHED, receipt.dispatchTruth)
    }

    @Test fun revocationAtDispatchMarkerCannotFallBack() = runTest {
        var allowed = true
        var effects = 0
        var fallbacks = 0
        val index = AppFunctionCapabilityIndex { _, _ -> allowed }
        index.publish(AppFunctionAvailability.AVAILABLE, "example.mail", null,
            listOf(capability(AppOperationRoute.APP_FUNCTION), capability(AppOperationRoute.DIRECT_ACTION)))
        val broker = SemanticAppActionBroker(index, executors = mapOf(
            AppOperationRoute.APP_FUNCTION to SemanticRouteExecutor { _, _, mark -> allowed = false; mark(); effects++;
                SemanticRouteOutcome(DispatchTruth.DISPATCHED, EffectTruth.UNCONFIRMED) },
            AppOperationRoute.DIRECT_ACTION to SemanticRouteExecutor { _, _, mark -> fallbacks++; mark();
                SemanticRouteOutcome(DispatchTruth.DISPATCHED, EffectTruth.UNCONFIRMED) }), authorize = { true })
        val receipt = broker.executePrepared((broker.resolve(operation()) as SemanticOperationResolution.Prepared).value)
        assertEquals(0, effects); assertEquals(0, fallbacks)
        assertEquals(SemanticOperationFailure.DISABLED, receipt.failure)
    }

    @Test fun changingAccessInvalidatesPreviouslyPreparedApproval() = runTest {
        val index = AppFunctionCapabilityIndex()
        index.publish(AppFunctionAvailability.AVAILABLE, "example.mail", null, listOf(capability(AppOperationRoute.APP_FUNCTION)))
        val broker = SemanticAppActionBroker(index, executors = emptyMap(), authorize = { error("Stale approval reused") })
        val prepared = (broker.resolve(operation()) as SemanticOperationResolution.Prepared).value
        index.accessChanged()
        assertEquals(SemanticOperationFailure.STALE_GENERATION, broker.executePrepared(prepared).failure)
    }
}
