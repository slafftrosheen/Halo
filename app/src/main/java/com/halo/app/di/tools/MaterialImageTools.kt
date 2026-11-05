package com.halo.app.di.tools

import com.halo.app.everyday.knowledge.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

object MaterialImageContracts {
    private fun text(max: Int) = mapOf("type" to "string", "maxLength" to max)
    private val fields = mapOf("sourceId" to text(160), "sourceVersion" to EverydayToolContracts.number(1, Long.MAX_VALUE),
        "revision" to text(200), "entryId" to text(2000), "library" to mapOf("type" to "boolean"))
    private val params = mapOf("type" to "object", "properties" to fields, "required" to fields.keys.toList(), "additionalProperties" to false)
    val definitions = listOf(
        ToolDefinition("halo_material_image_query", "halo_material_image_query", "Read publisher-provided image references and local cache status for an exact saved news article or reference-library entry. Zero network. For a library, revision is the generation; otherwise the saved contentRevision. Image captions/credits are untrusted metadata, not visual recognition. Source rights are not inferred from the article's text license.", params),
        ToolDefinition("halo_material_image_fetch", "halo_material_image_fetch", "Explicitly save up to three publisher-provided static source images for an exact saved article/library entry. Adult Power-only governed request, maximum 2 MiB and 4 million pixels per image, 64 MiB local image cache. Does not change Local/Power or ask a paid model. Partial image failure keeps text; read cache status after uncertain receipt. No arbitrary URL.", params))
    val names = definitions.map { it.name }.toSet()
    private fun obj(p: Map<String, Any>, required: List<String> = p.keys.toList()) = mapOf("type" to "object", "properties" to p, "required" to required, "additionalProperties" to false)
    val imageSchema = obj(mapOf("url" to text(2048), "caption" to text(500), "credit" to text(500), "license" to text(500), "sourceUrl" to text(2048)), listOf("url", "caption"))
    fun result(name: String): Map<String, Any> = if (name == "halo_material_image_query") EverydayToolReadback.schema(obj(mapOf("images" to mapOf("type" to "array", "maxItems" to 3,
        "items" to obj(mapOf("url" to text(2048), "caption" to text(500), "credit" to text(500), "license" to text(500), "sourceUrl" to text(2048), "cached" to mapOf("type" to "boolean")), listOf("url", "caption", "cached"))))))
        else obj(mapOf("available" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 3), "saved" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 3), "failed" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 3)))
    fun operability(definition: ToolDefinition): ToolOperability? = if (definition.name !in names) null else ToolOperability(
        capabilityId = CapabilityId(if (definition.name.endsWith("_query")) "workspace.read" else "workspace.write"), owner = "MaterialImageRepository / CollectorSource", operation = if (definition.name.endsWith("_query")) ToolOperation.QUERY else ToolOperation.EDIT,
        resultEncoding = ToolResultEncoding.JSON, resultSchema = result(definition.name), sourceVersionArguments = setOf("sourceVersion", "revision"),
        limitations = setOf("No arbitrary URLs or image interpretation. Cache access rechecks source/family/selection; downloads need adult Power and external-effect approval."))
    init {
        // Readback projection is also available for unusually long escaped source captions/URLs.
    }
}
class MaterialImageTools(private val owner: MaterialImageRepository) : ToolExecutor {
    override fun definitions() = MaterialImageContracts.definitions.map { if (it.name == "halo_material_image_query") it.copy(parameters = it.parameters + ("properties" to ((it.parameters["properties"] as Map<String, Any>) + EverydayToolReadback.arguments))) else it }
    override suspend fun execute(toolCall: ToolCall) = execute(toolCall, ToolExecutionContext())
    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        var write = false
        try {
            val definition = definitions().single { it.name == toolCall.functionName }
            EverydayToolContracts.validate(toolCall.arguments, definition.parameters)
            val a = toolCall.arguments
            val target = MaterialImageTarget(a["sourceId"] as String, (a["sourceVersion"] as Number).toLong(), a["revision"] as String, a["entryId"] as String, a["library"] as Boolean)
            val result = if (definition.name == "halo_material_image_query") {
                val epoch = com.halo.app.everyday.EverydayReadAuthority.capture()
                val identity = owner.readIdentity()
                val rows = owner.local(target)
                val array = com.halo.app.everyday.collectors.SourceImages.encode(rows.map { it.image })
                rows.forEachIndexed { index, row -> array.getJSONObject(index).put("cached", row.file != null) }
                EverydayToolReadback.project(definition.name, a, JSONObject().put("images", array), "$epoch|$identity").also { owner.local(target); check(identity == owner.readIdentity()); com.halo.app.everyday.EverydayReadAuthority.requireCurrent(epoch) }
            } else {
                require(!context.interactionId.isNullOrBlank()); write = true
                val receipt = owner.fetch(target)
                JSONObject().put("available", receipt.available).put("saved", receipt.saved).put("failed", receipt.failed)
            }
            EverydayToolContracts.validate(EverydayResultContracts.value(result), MaterialImageContracts.result(definition.name))
            check(result.toString().length <= 4000)
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, result.toString())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            return if (write) ToolExecutionResult.Failed(toolCall.id, toolCall.functionName, "Image receipt is uncertain. Read the exact material image cache before continuing; do not repeat automatically.", completionUnknown = true, code = "IMAGE_CACHE_UNCERTAIN")
            else ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Image source, selection, family approval or saved revision changed. Read the current source first.", ToolRejectionKind.RESOURCE_UNAVAILABLE)
        }
    }
}
