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
        val descriptors = loadBundledManifests(strict = true)
        val defaults = DefaultSkillRegistry.defaultBuiltInSkills()

        val duplicateIds = descriptors.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicateIds.isEmpty()) {
            "Duplicate bundled skill ids: ${duplicateIds.joinToString { it.value }}"
        }

        val manifestIds = descriptors.mapTo(linkedSetOf()) { it.id }
        val missingManifests = defaults.map { it.id }.filterNot(manifestIds::contains)
        require(missingManifests.isEmpty()) {
            "Built-in skills missing manifests: ${missingManifests.joinToString { it.value }}"
        }

        // Runtime defaults are now a compatibility/risk floor only. Every built-in skill must
        // ship a validated manifest; invalid assets fail closed instead of silently disappearing.
        return BuiltInSkillMerge.merge(
            defaults = defaults,
            manifests = descriptors,
        )
    }

    /** Strict entry point used by the build guard so no invalid bundled asset can be ignored. */
    fun loadBundledManifests(strict: Boolean = true): List<SkillDescriptor> {
        val files = context.assets.list("skills").orEmpty().filter { it.endsWith(".json") }.sorted()
        return files.mapNotNull { file ->
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
            }.onFailure { android.util.Log.e("HaloSkills", "Invalid built-in skill manifest: $file", it) }
                .fold(
                    onSuccess = { it },
                    onFailure = { if (strict) throw IllegalArgumentException("Invalid built-in skill manifest: $file", it) else null },
                )
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
