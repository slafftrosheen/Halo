package com.halo.host.android.appfunctions

import android.content.Context
import android.os.Build
import androidx.appfunctions.*
import androidx.appfunctions.metadata.*
import com.halo.runtime.cognition.apps.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Experimental AndroidX and platform identities terminate here. No opaque platform ID is durable. */
class AndroidAppFunctionConsumer(private val context: Context, private val index: AppFunctionCapabilityIndex,
    private val scope: CoroutineScope, private val changed: () -> Unit = {}) : SemanticRouteExecutor {
    private var manager: AppFunctionManager? = null
    private var observer: Job? = null
    private var packageName: String? = null
    private var activity: Any? = null
    private var activityIdentity: String? = null
    private var direct = emptyList<SemanticAppCapability>()
    private var metadata = emptyMap<String, AppFunctionMetadata>()
    private val refreshMutex = Mutex()
    fun attach(packageName: String, identity: String?, activityId: Any?, directActions: List<SemanticAppCapability>) {
        this.packageName = packageName; activityIdentity = identity; activity = activityId; direct = directActions
        index.invalidate(); changed()
        scope.launch(Dispatchers.IO) { refresh() }
    }
    fun detach() { activity = null; activityIdentity = null; direct = emptyList(); metadata = emptyMap()
        index.invalidate(); changed() }
    suspend fun refresh(): Unit = refreshMutex.withLock {
        val pkg = packageName ?: context.packageName
        android.util.Log.i("HaloAppFunctions", "HALO_APP_CAPABILITY_REFRESH package=$pkg")
        val identity = activityIdentity
        val platformActivity = activity
        try {
            // getInstance owns feature detection, including API 34 extension devices and profiles.
            val api = manager ?: AppFunctionManager.getInstance(context)?.also { manager = it }
            if (api == null) {
                index.publish(AppFunctionAvailability.UNSUPPORTED, packageName, identity, direct); changed(); return@withLock
            }
            // UNDISPATCHED establishes collection before the explicit initial search/state read.
            if (observer == null || observer?.isCompleted == true) observer = scope.launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try { api.observeAppFunctions().collect {
                    index.invalidate(); changed()
                    scope.launch(Dispatchers.IO) { refresh() }
                } } catch (cancelled: CancellationException) { throw cancelled }
                  catch (_: SecurityException) { index.publish(AppFunctionAvailability.PERMISSION_DENIED, packageName, activityIdentity, direct); changed() }
                  catch (_: Exception) { index.publish(AppFunctionAvailability.ERROR, packageName, activityIdentity, direct); changed() }
            }
            val found = withTimeout(AppOperationDeadlines.DISCOVERY_MS) {
                api.searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(pkg)))
            }.take(64)
            val states = withTimeout(AppOperationDeadlines.STATE_MS) { api.getAppFunctionStates(found.map { it.name }) }.associateBy { it.functionName }
            val scoped = if (Build.VERSION.SDK_INT >= 37 && platformActivity is android.app.appfunctions.AppFunctionActivityId)
                withTimeout(AppOperationDeadlines.STATE_MS) { api.getAppFunctionActivityStates(setOf(platformActivity)) }
                    .flatMap { it.functionNames }.toSet() else emptySet()
            val revision = context.packageManager.getPackageInfo(pkg, 0).lastUpdateTime
            val capabilities = found.mapNotNull { m ->
                if (m.parameters.size > 24) return@mapNotNull null
                val parameters = m.parameters.map { p -> SemanticParameter(p.name, type(p.dataType) ?: return@mapNotNull null, p.isRequired) }
                val isActivity = m.scope == AppFunctionMetadata.SCOPE_ACTIVITY
                if (isActivity && m.name !in scoped) return@mapNotNull null
                SemanticAppCapability(m.id, m.packageName, SemanticActionMapping.fromIdentifier(m.id) ?: SemanticActionId("external_" + safeIdentifier(m.id)),
                    safeIdentifier(m.id), m.description, parameters, AppOperationRoute.APP_FUNCTION,
                    if (isActivity) CapabilityScope.CURRENT_ACTIVITY else CapabilityScope.GLOBAL,
                    states[m.name]?.isEnabled == true, if (isActivity) identity else null, revision)
            }
            if (packageName != pkg && !(packageName == null && pkg == context.packageName) || identity != activityIdentity) return@withLock
            metadata = found.associateBy { it.id }
            index.publish(AppFunctionAvailability.AVAILABLE, packageName, identity, capabilities + direct)
            changed()
            android.util.Log.i("HaloAppFunctions", "HALO_APPFUNCTION_DISCOVERY package=$pkg functions=${capabilities.size}")
        } catch (cancelled: CancellationException) { if (cancelled is TimeoutCancellationException) {
            index.publish(AppFunctionAvailability.ERROR, packageName, identity, direct); changed()
        } else throw cancelled }
          catch (_: SecurityException) { index.publish(AppFunctionAvailability.PERMISSION_DENIED, packageName, identity, direct); changed() }
          catch (_: LinkageError) { index.publish(AppFunctionAvailability.UNSUPPORTED, packageName, identity, direct); changed() }
          catch (_: Exception) { index.publish(AppFunctionAvailability.ERROR, packageName, identity, direct); changed() }
    }
    override suspend fun execute(operation: SemanticAppOperation, capability: SemanticAppCapability, markDispatched: () -> Unit): SemanticRouteOutcome {
        val api = manager ?: return rejected(SemanticOperationFailure.FEATURE_UNAVAILABLE)
        val m = metadata[capability.id] ?: return rejected(SemanticOperationFailure.NOT_FOUND)
        val executionActivity = activity
        val executionIdentity = activityIdentity
        if (capability.scope == CapabilityScope.CURRENT_ACTIVITY && capability.activityIdentity != executionIdentity)
            return rejected(SemanticOperationFailure.STALE_GENERATION)
        val states = try { withTimeout(AppOperationDeadlines.STATE_MS) { api.getAppFunctionStates(listOf(m.name)) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { return rejected(mapError(error)) }
        if (states.singleOrNull()?.isEnabled != true) return rejected(SemanticOperationFailure.DISABLED)
        if (context.packageManager.getPackageInfo(capability.packageName, 0).lastUpdateTime != capability.packageRevision)
            return rejected(SemanticOperationFailure.NOT_FOUND)
        if (capability.scope == CapabilityScope.CURRENT_ACTIVITY) {
            if (Build.VERSION.SDK_INT < 37 || executionActivity !is android.app.appfunctions.AppFunctionActivityId)
                return rejected(SemanticOperationFailure.STALE_GENERATION)
            val visible = try { withTimeout(AppOperationDeadlines.STATE_MS) { api.getAppFunctionActivityStates(setOf(executionActivity)) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { return rejected(mapError(error)) }
            if (executionIdentity != activityIdentity || visible.none { m.name in it.functionNames })
                return rejected(SemanticOperationFailure.STALE_GENERATION)
        }
        val builder = AppFunctionData.Builder(m.parameters, m.packageMetadata.components)
        for ((key, value) in operation.arguments) when (value) {
            is SemanticValue.Text -> builder.setString(key, value.value)
            is SemanticValue.Integer -> if (m.parameters.first { it.name == key }.dataType is AppFunctionIntTypeMetadata)
                {
                    if (value.value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return rejected(SemanticOperationFailure.INVALID_ARGUMENTS)
                    builder.setInt(key, value.value.toInt())
                } else builder.setLong(key, value.value)
            is SemanticValue.Number -> builder.setDouble(key, value.value)
            is SemanticValue.Boolean -> builder.setBoolean(key, value.value)
        }
        val request = if (Build.VERSION.SDK_INT >= 37) {
            val attribution = android.app.AppInteractionAttribution.Builder(android.app.AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY).build()
            ExecuteAppFunctionRequest(capability.packageName, capability.id, builder.build(), attribution,
                if (capability.scope == CapabilityScope.CURRENT_ACTIVITY) executionActivity as? android.app.appfunctions.AppFunctionActivityId else null)
        } else ExecuteAppFunctionRequest(capability.packageName, capability.id, builder.build())
        if (capability.scope == CapabilityScope.CURRENT_ACTIVITY && executionIdentity != activityIdentity)
            return rejected(SemanticOperationFailure.STALE_GENERATION)
        // The broker may revoke eligibility here. Do not turn that rejection into an
        // Android API error or claim a dispatch that never reached the platform.
        markDispatched()
        return try {
            android.util.Log.i("HaloAppFunctions", "HALO_APPFUNCTION_EXECUTION operationId=${operation.operationId} package=${capability.packageName}")
            when (val response = api.executeAppFunction(request)) {
                is ExecuteAppFunctionResponse.Success -> SemanticRouteOutcome(DispatchTruth.DISPATCHED, EffectTruth.UNCONFIRMED)
                is ExecuteAppFunctionResponse.Error -> SemanticRouteOutcome(DispatchTruth.DISPATCHED, EffectTruth.UNKNOWN, mapError(response.error))
            } // Returned app data is untrusted and is not effect evidence.
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (error: Exception) { val failure = mapError(error)
            SemanticRouteOutcome(if (failure in SemanticAppActionBroker.PRE_DISPATCH_REJECTIONS) DispatchTruth.NOT_DISPATCHED else DispatchTruth.DISPATCHED,
                EffectTruth.UNKNOWN, failure) }
    }
    companion object {
        fun mapError(error: Exception): SemanticOperationFailure = when (error) {
            is SecurityException, is AppFunctionDeniedException -> SemanticOperationFailure.CALLER_NOT_AUTHORIZED
            is AppFunctionPermissionRequiredException -> SemanticOperationFailure.TARGET_PERMISSION_REQUIRED
            is AppFunctionDisabledException -> SemanticOperationFailure.DISABLED
            is AppFunctionFunctionNotFoundException -> SemanticOperationFailure.NOT_FOUND
            is AppFunctionInvalidArgumentException, is IllegalArgumentException -> SemanticOperationFailure.INVALID_ARGUMENTS
            is AppFunctionNotSupportedException -> SemanticOperationFailure.FEATURE_UNAVAILABLE
            is TimeoutCancellationException -> SemanticOperationFailure.TIMEOUT
            is AppFunctionAppUnknownException -> SemanticOperationFailure.TARGET_CRASHED
            else -> SemanticOperationFailure.PLATFORM_ERROR
        }
        private fun rejected(failure: SemanticOperationFailure) = SemanticRouteOutcome(DispatchTruth.NOT_DISPATCHED, EffectTruth.UNKNOWN, failure)
        private fun type(type: AppFunctionDataTypeMetadata): SemanticValueType? = when (type) {
            is AppFunctionStringTypeMetadata -> SemanticValueType.STRING
            is AppFunctionIntTypeMetadata, is AppFunctionLongTypeMetadata -> SemanticValueType.INTEGER
            is AppFunctionDoubleTypeMetadata -> SemanticValueType.NUMBER
            is AppFunctionBooleanTypeMetadata -> SemanticValueType.BOOLEAN
            else -> null // Unsupported nested/resource/URI schemas are excluded rather than guessed or granted globally.
        }
        private fun safeIdentifier(id: String) = id.substringAfterLast('/').substringAfterLast('#').replace(Regex("[^A-Za-z0-9_ ]"), "_").take(120)
    }
}
