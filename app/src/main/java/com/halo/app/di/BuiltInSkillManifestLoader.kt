package com.halo.app.di

import android.content.Context
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.sdk.runtime.skills.HostPermission
import com.halo.sdk.runtime.skills.SkillCategory
import com.halo.sdk.runtime.skills.SkillDescriptor
import com.halo.sdk.runtime.skills.SkillId
import com.halo.sdk.runtime.skills.SkillRisk
import com.halo.sdk.runtime.skills.SkillSource
import com.halo.sdk.runtime.skills.SkillManifestPolicy
import com.halo.sdk.runtime.skills.SkillProvenance
import org.json.JSONObject
import java.security.MessageDigest

class BuiltInSkillManifestLoader(private val context: Context) {
    fun load(): List<SkillDescriptor> {
        val files = context.assets.list("skills").orEmpty().filter { it.endsWith(".json") }.sorted()
        val descriptors = files.mapNotNull { file ->
            runCatching {
                val bytes = context.assets.open("skills/$file").use { it.readBytes() }
                require(bytes.size <= SkillManifestPolicy.MAX_MANIFEST_BYTES) { "Manifest exceeds size limit" }
                val json = JSONObject(bytes.toString(Charsets.UTF_8))
                val tools = json.getJSONArray("tools")
                val permissions = json.optJSONArray("requiredPermissions")
                val descriptor = SkillDescriptor(
                    id = SkillId(json.getString("id")),
                    name = json.getString("name"),
                    description = json.getString("description"),
                    category = SkillCategory.valueOf(json.getString("category")),
                    schemaVersion = json.optInt("schemaVersion", SkillManifestPolicy.CURRENT_SCHEMA_VERSION),
                    risk = SkillRisk.valueOf(json.getString("risk")),
                    tools = (0 until tools.length()).map { tools.getString(it) }.toSet(),
                    toolDefinitions = json.optJSONArray("toolDefinitions")?.let { definitions ->
                        (0 until definitions.length()).map { index ->
                            val tool = definitions.getJSONObject(index)
                            com.halo.sdk.runtime.skills.SkillToolDefinition(
                                toolId = tool.getString("toolId"),
                                risk = SkillRisk.valueOf(tool.getString("risk"))
                            )
                        }
                    }.orEmpty(),
                    requiredPermissions = permissions?.let { values ->
                        (0 until values.length()).map { HostPermission.valueOf(values.getString(it)) }.toSet()
                    }.orEmpty(),
                    source = SkillSource.BUILT_IN,
                    provenance = SkillProvenance(
                        sourceId = "asset:skills/$file",
                        contentSha256 = bytes.sha256(),
                        loadedAtMs = System.currentTimeMillis(),
                    ),
                )
                val errors = SkillManifestPolicy.validate(descriptor)
                require(errors.isEmpty()) { errors.joinToString("; ") }
                descriptor
            }.onFailure { android.util.Log.e("HaloSkills", "Invalid built-in skill manifest: $file", it) }.getOrNull()
        }
        // Duplicate ids are resolved by BuiltInSkillMerge, which keeps the strictest declared risk
        // and unions tools/permissions. A plain associateBy here let a manifest override the
        // registry with a weaker classification for the same skill.
        return BuiltInSkillMerge.merge(
            defaults = DefaultSkillRegistry.defaultBuiltInSkills(),
            manifests = descriptors,
        )
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
