package com.halo.app.di

import android.content.Context
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import com.halo.sdk.runtime.skills.HostPermission
import com.halo.sdk.runtime.skills.SkillCategory
import com.halo.sdk.runtime.skills.SkillDescriptor
import com.halo.sdk.runtime.skills.SkillId
import com.halo.sdk.runtime.skills.SkillRisk
import com.halo.sdk.runtime.skills.SkillSource
import org.json.JSONObject

class BuiltInSkillManifestLoader(private val context: Context) {
    fun load(): List<SkillDescriptor> {
        val files = context.assets.list("skills").orEmpty().filter { it.endsWith(".json") }.sorted()
        val descriptors = files.mapNotNull { file ->
            runCatching {
                val json = context.assets.open("skills/$file").bufferedReader().use {
                    JSONObject(it.readText())
                }
                val tools = json.getJSONArray("tools")
                val permissions = json.optJSONArray("requiredPermissions")
                SkillDescriptor(
                    id = SkillId(json.getString("id")),
                    name = json.getString("name"),
                    description = json.getString("description"),
                    category = SkillCategory.valueOf(json.getString("category")),
                    risk = SkillRisk.valueOf(json.getString("risk")),
                    tools = (0 until tools.length()).map { tools.getString(it) }.toSet(),
                    requiredPermissions = permissions?.let { values ->
                        (0 until values.length()).map { HostPermission.valueOf(values.getString(it)) }.toSet()
                    }.orEmpty(),
                    source = SkillSource.BUILT_IN
                )
            }.getOrNull()
        }
        return descriptors.ifEmpty { DefaultSkillRegistry.defaultBuiltInSkills() }
    }
}
