package com.halo.app.skills

import com.halo.runtime.cognition.skills.SkillRegistry
import com.halo.sdk.runtime.Storage

/** Updated instructions require a fresh user enablement decision. */
internal fun installCustomSkillPackage(
    repository: CustomSkillPackageRepository,
    storage: Storage,
    registry: SkillRegistry,
    fileName: String,
    bytes: ByteArray,
): Result<Unit> = runCatching {
    val parsed = SkillMarkdownPackageParser.parse("import:$fileName", bytes)
    // Persist the disabled state before publishing new instructions, including after a restart.
    storage.putBoolean("skill_enabled_${parsed.descriptor.id.value}", false)
    try {
        repository.importPackage(fileName, bytes)
    } finally {
        registry.replaceCustomSkills(repository.refresh())
    }
}
