package com.halo.app.di.tools.host

import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.toolkit.MerchantCheckoutRequests
import com.halo.app.ui.toolkit.MerchantQuote
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class MerchantCheckoutTools(private val foregroundUnlocked: () -> Boolean) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition(
        OPEN, OPEN, "Submit a merchant and exact total for review on the adult phone. Supply only a public HTTPS merchant link and a total the person provided. The person reviews and authenticates on the phone before their browser opens. This cannot buy, pay, or open a payment link automatically.",
        mapOf("type" to "object", "additionalProperties" to false, "required" to listOf("url", "total", "currency"),
            "properties" to mapOf(
                "url" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 1000),
                "total" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 16),
                "currency" to mapOf("type" to "string", "minLength" to 3, "maxLength" to 3),
            )),
    ))

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        if (toolCall.functionName != OPEN) return reject("Unknown checkout operation.", ToolRejectionKind.TOOL_UNKNOWN)
        val args = toolCall.arguments
        val quote = if (args.keys == setOf("url", "total", "currency")) runCatching {
            MerchantQuote.parse(args.getValue("url") as String, args.getValue("total") as String, args.getValue("currency") as String)
        }.getOrNull() else null
        if (quote == null) return reject("A public merchant link, exact total and currency are required.", ToolRejectionKind.INVALID_ARGUMENTS)
        return withContext(Dispatchers.Main.immediate) {
            val revision = runCatching { Graph.kidsPreferences.adultActionRevision() }.getOrNull()
            if (revision == null || !foregroundUnlocked() || Graph.isHaloPaused())
                return@withContext reject("Open Halo on the unlocked adult phone.", ToolRejectionKind.PERMISSION_REQUIRED)
            val id = MerchantCheckoutRequests.dispatch(quote, revision, Graph.requestedHaloScreen)
                ?: return@withContext reject("Another review is already pending.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            ToolExecutionResult.Success(toolCall.id, OPEN, JSONObject().put("reviewId", id).toString(), ActionTruth.DISPATCHED)
        }
    }

    companion object {
        const val OPEN = "halo_review_merchant_checkout"

        fun descriptor(definition: ToolDefinition): ToolDescriptor = ToolDescriptor(
            id = ToolId(OPEN), definition = definition, category = ToolCategory.HOST_CONTROL,
            keywords = setOf("merchant", "checkout", "buy", "purchase", "купить", "оплата"),
            security = ToolSecurityPolicy(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            operability = ToolOperability(capabilityId = CapabilityId("halo.checkout"), owner = "MerchantCheckoutRequests",
                operation = ToolOperation.PRESENT, resultEncoding = ToolResultEncoding.JSON,
                resultSchema = mapOf("type" to "object", "additionalProperties" to false,
                    "properties" to mapOf("reviewId" to mapOf("type" to "string")), "required" to listOf("reviewId")),
                resultIdFields = setOf("reviewId"), limitations = setOf("Phone review and device authentication precede the browser handoff.",
                    "Merchant handles payment and receipt. Halo presents a quote and does not charge a card.")),
        )
    }
}
