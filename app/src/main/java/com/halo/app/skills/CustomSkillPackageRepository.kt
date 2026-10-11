package com.halo.app.skills

import android.content.Context
import com.halo.runtime.cognition.skills.SkillInstruction
import com.halo.runtime.cognition.skills.SkillInstructionSource
import com.halo.sdk.runtime.skills.*
import java.io.File
import android.util.AtomicFile
import com.halo.runtime.cognition.skills.DefaultSkillRegistry
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ParsedSkillPackage(
    val descriptor: SkillDescriptor,
    val instruction: SkillInstruction,
)

data class SkillPackageIssue(val sourceId: String, val message: String)

/** Strict, dependency-free parser for bounded SKILL.md packages. */
object SkillMarkdownPackageParser {
    const val MAX_INSTRUCTION_CHARS = 16_000

    fun parse(sourceId: String, bytes: ByteArray, nowMs: Long = System.currentTimeMillis()): ParsedSkillPackage {
        require(bytes.size <= SkillManifestPolicy.MAX_MANIFEST_BYTES) { "Skill package exceeds size limit" }
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString().replace("\r\n", "\n")
        require(text.startsWith("---\n")) { "Skill package must start with frontmatter" }
        val end = text.indexOf("\n---\n", startIndex = 4)
        require(end > 4) { "Skill package frontmatter is not closed" }
        val fields = parseFrontmatter(text.substring(4, end))
        require(fields.required("id") !in builtInIds) { "Custom packages cannot replace a built-in skill" }
        val body = text.substring(end + 5).trim()
        require(body.isNotBlank()) { "Skill instructions are empty" }
        require(body.length <= MAX_INSTRUCTION_CHARS) { "Skill instructions exceed $MAX_INSTRUCTION_CHARS characters" }

        val tools = csv(fields.required("tools"))
        val permissions = csv(fields["permissions"].orEmpty()).map { HostPermission.valueOf(it.uppercase()) }.toSet()
        val requirements = parseRequirements(fields["requirements"].orEmpty())
        val risk = SkillRisk.valueOf(fields.required("risk").uppercase())
        val descriptor = SkillDescriptor(
            id = SkillId(fields.required("id")),
            name = fields.required("name"),
            description = fields.required("description"),
            category = SkillCategory.valueOf(fields.required("category").uppercase()),
            schemaVersion = fields["schemaVersion"]?.let { it.toIntOrNull() ?: error("Invalid schemaVersion") }
                ?: SkillManifestPolicy.CURRENT_SCHEMA_VERSION,
            // Imported packages never self-enable. Enablement is a separate user decision.
            enabled = false,
            tools = tools,
            toolDefinitions = tools.map { SkillToolDefinition(it, risk) },
            requiredPermissions = permissions + requirements.filterIsInstance<SkillRequirement.Permission>().map { it.permission },
            requirements = requirements,
            risk = risk,
            source = SkillSource.CUSTOM,
            provenance = SkillProvenance(sourceId, bytes.sha256(), nowMs),
        )
        val errors = SkillManifestPolicy.validate(descriptor)
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return ParsedSkillPackage(
            descriptor,
            SkillInstruction(descriptor.id, body, descriptor.provenance?.contentSha256),
        )
    }

    private fun parseFrontmatter(value: String): Map<String, String> = buildMap {
        value.lineSequence().forEachIndexed { index, line ->
            if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed
            val separator = line.indexOf(':')
            require(separator > 0) { "Invalid frontmatter line ${index + 1}" }
            val key = line.substring(0, separator).trim()
            require(key in ALLOWED_FIELDS) { "Unknown frontmatter field: $key" }
            require(key !in this) { "Duplicate frontmatter field: $key" }
            put(key, line.substring(separator + 1).trim().removeSurrounding("\""))
        }
    }

    private fun csv(value: String): Set<String> = value.removePrefix("[").removeSuffix("]")
        .split(',').map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }.toSet()

    private fun parseRequirements(value: String): List<SkillRequirement> = csv(value).map { entry ->
        val separator = entry.indexOf(':')
        require(separator > 0 && separator < entry.lastIndex) { "Invalid skill requirement: $entry" }
        val kind = entry.substring(0, separator).trim().lowercase()
        val argument = entry.substring(separator + 1).trim()
        require(argument.length <= MAX_REQUIREMENT_CHARS) { "Skill requirement is too long" }
        when (kind) {
            "permission" -> SkillRequirement.Permission(HostPermission.valueOf(argument.uppercase()))
            "tool" -> SkillRequirement.ToolRegistered(argument)
            "provider" -> SkillRequirement.ProviderCapability(argument)
            "runtime" -> SkillRequirement.RuntimeFeature(argument)
            "minsdk" -> SkillRequirement.MinSdk(argument.toIntOrNull()?.takeIf { it > 0 }
                ?: error("Invalid minimum SDK requirement"))
            "device" -> SkillRequirement.DeviceConnected(argument)
            else -> error("Unknown skill requirement type: $kind")
        }
    }.also {
        require(it.size <= MAX_REQUIREMENTS) { "Skill package declares too many requirements" }
    }

    private fun Map<String, String>.required(key: String): String =
        get(key)?.takeIf { it.isNotBlank() } ?: error("Missing frontmatter field: $key")

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private val ALLOWED_FIELDS = setOf(
        "id", "name", "description", "category", "risk", "tools", "permissions", "requirements", "schemaVersion", "enabled"
    )

    private const val MAX_REQUIREMENTS = 24
    private const val MAX_REQUIREMENT_CHARS = 160
    private val builtInIds by lazy { DefaultSkillRegistry.defaultBuiltInSkills().map { it.id.value }.toSet() }
}

/** Local custom packages are inert by default and become available only after validation. */
class CustomSkillPackageRepository(context: Context) : SkillInstructionSource {
    private val directory = File(context.filesDir, "skills")
    @Volatile private var packages: Map<SkillId, ParsedSkillPackage> = emptyMap()
    private val _issues = MutableStateFlow<List<SkillPackageIssue>>(emptyList())
    val issues: StateFlow<List<SkillPackageIssue>> = _issues.asStateFlow()

    @Synchronized fun refresh(): List<SkillDescriptor> {
        if (!directory.exists()) directory.mkdirs()
        val issues = mutableListOf<SkillPackageIssue>()
        packages = directory.listFiles { file -> file.isFile && (file.extension.equals("md", true) || file.name.endsWith(".md.bak")) }
            .orEmpty().map { if (it.name.endsWith(".md.bak")) File(directory, it.name.removeSuffix(".bak")) else it }
            .distinctBy { it.name }.sortedBy { it.name }.mapNotNull { file ->
                runCatching { SkillMarkdownPackageParser.parse("file:${file.name}", AtomicFile(file).openRead().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= SkillManifestPolicy.MAX_MANIFEST_BYTES) { "Skill package exceeds size limit" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }) }
                    .onFailure { issues += SkillPackageIssue(file.name, it.message ?: "Invalid skill package") }
                    .getOrNull()
            }.groupBy { it.descriptor.id }.mapNotNull { (id, entries) ->
                if (entries.size == 1) id to entries.single() else {
                    issues += SkillPackageIssue(id.value, "Duplicate skill id; keep only one package")
                    null
                }
            }.toMap()
        _issues.value = issues
        return packages.values.map { it.descriptor }
    }

    @Synchronized fun importPackage(fileName: String, bytes: ByteArray): ParsedSkillPackage {
        val parsed = SkillMarkdownPackageParser.parse("import:$fileName", bytes)
        if (!directory.exists()) check(directory.mkdirs()) { "Could not create skill directory" }
        val target = File(directory, parsed.descriptor.id.value + ".md")
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
        // AtomicFile can log a failed final rename without throwing. Never report an old package as installed.
        check(atomic.openRead().use { input ->
            val committed = ByteArray(bytes.size)
            var offset = 0
            while (offset < committed.size) {
                val count = input.read(committed, offset, committed.size - offset)
                if (count < 0) break
                offset += count
            }
            offset == bytes.size && input.read() == -1 && committed.contentEquals(bytes)
        }) { "Could not commit skill package" }
        refresh()
        return parsed
    }

    override suspend fun load(skillId: SkillId): SkillInstruction? = packages[skillId]?.instruction
}
