package com.halo.app.ai

import com.halo.app.audio.SupertonicPackStore
import com.halo.providers.litert.models.LiteRtModelCatalog
import com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog
import java.security.MessageDigest

enum class OfflinePackRole { CONVERSATION, ACTION_INTENT, RECOGNITION, VOICE_ACTIVITY, SPEECH_OUTPUT }

data class OfflinePackFile(val name: String, val bytes: Long, val sha256: String?)

/** Source declarations only. Null metadata must remain unknown until an upstream audit lands. */
data class OfflinePackArtifact(
    val id: String,
    val role: OfflinePackRole,
    val source: String,
    val revision: String?,
    val declaredLicense: String?,
    val runtime: String,
    val declaredLanguages: Set<String>,
    val files: List<OfflinePackFile>,
) {
    val bytes: Long get() = files.fold(0L) { total, file -> Math.addExact(total, file.bytes) }
    val hasExactFilePins: Boolean get() = files.isNotEmpty() && files.all {
        it.bytes > 0 && it.sha256?.matches(Regex("[a-f0-9]{64}")) == true
    }
}

/** Uses the acquisition owners' source pins, never a duplicate hand-maintained asset list.
 * This starter is deliberately incomplete: a CHAT artifact has not been qualified for it.
 * Speech language declarations describe upstream candidates, not measured language coverage.
 */
object PrivateOfflinePack {
    const val VERSION = "private-starter-1"
    val artifacts: List<OfflinePackArtifact> = buildList {
        val intent = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4
        add(OfflinePackArtifact(intent.id, OfflinePackRole.ACTION_INTENT,
            "https://huggingface.co/${intent.repoId}/tree/${intent.revision}", intent.revision,
            intent.license, "LiteRT-LM >= ${intent.minLiteRtLmVersion}; ${intent.runtimeProfile}", emptySet(),
            listOf(OfflinePackFile(intent.filename, intent.expectedBytes, intent.sha256))))
        listOf(SherpaSpeechModelCatalog.whisperSmall to OfflinePackRole.RECOGNITION,
            SherpaSpeechModelCatalog.sileroVad to OfflinePackRole.VOICE_ACTIVITY).forEach { (model, role) ->
            add(OfflinePackArtifact(model.id, role, model.sourceUrl, model.revision.ifBlank { null },
                null, SupertonicPackStore.RUNTIME, model.supportedLanguageTags.orEmpty(),
                model.files.map { OfflinePackFile(it.name, it.expectedBytes, it.sha256) }))
        }
        add(OfflinePackArtifact("supertonic-3-int8", OfflinePackRole.SPEECH_OUTPUT,
            SupertonicPackStore.OFFICIAL_URL, null, null, SupertonicPackStore.RUNTIME,
            setOf("en", "ru", "lv", "pl"), listOf(OfflinePackFile(
                SupertonicPackStore.OFFICIAL_URL.substringAfterLast('/'),
                SupertonicPackStore.OFFICIAL_BYTES, SupertonicPackStore.OFFICIAL_SHA256))))
    }
    /** Acquired independently through the existing model manager, never by START_OR_RESUME. */
    val conversationCandidate = LiteRtModelCatalog.SMOLLM2_360M_INSTRUCT
    val optionalArtifacts: List<OfflinePackArtifact> = listOf(conversationCandidate.let { chat ->
        OfflinePackArtifact(chat.id, OfflinePackRole.CONVERSATION,
            "https://huggingface.co/${chat.repoId}/tree/${chat.revision}", chat.revision, chat.license,
            "LiteRT-LM >= ${chat.minLiteRtLmVersion}; ${chat.runtimeProfile}", emptySet(),
            listOf(OfflinePackFile(chat.filename, chat.expectedBytes, chat.sha256)))
    })
    /** Exact, complete approval text. Never truncate a consequential review. A future
     * catalogue that exceeds the approval surface must use native source review instead.
     */
    fun sourceReviewSummary(scope: String, language: String): String? {
        if (scope !in setOf("STARTER", "CONVERSATION_EXTENSION")) return null
        val copy = when (language.substringBefore('-').substringBefore('_').lowercase()) {
            "ru" -> ReviewCopy("Источники локального пакета", "Загрузить", "Стартовый пакет", "Отдельная модель беседы",
                "лицензия", "неизвестно", "байт", "ревизия",
                "Подтверждение означает ознакомление со всеми указанными источниками и лицензиями, включая неизвестные сведения, и согласие на загрузку только выбранного пакета. Аудит лицензий и прав распространения не завершён. Качество языков и работа на устройстве не подтверждены. Модели не выбираются автоматически. Стартовый пакет не включает беседу. Объём загрузки и временное место указаны в статусе настройки.")
            "lv" -> ReviewCopy("Lokālās pakotnes avoti", "Lejupielādēt", "Sākuma pakotne", "Atsevišķs sarunas modelis",
                "licence", "nav zināms", "baiti", "revīzija",
                "Apstiprinājums nozīmē iepazīšanos ar visiem avotiem un licencēm, arī nezināmo informāciju, un piekrišanu tikai izvēlētās pakotnes lejupielādei. Licenču un izplatīšanas tiesību audits nav pabeigts. Valodu kvalitāte un darbība ierīcē nav pārbaudīta. Modeļi netiek izvēlēti automātiski. Sākuma pakotnē nav sarunas modeļa. Lejupielādes apjoms un pagaidu vieta norādīti iestatīšanas statusā.")
            "pl" -> ReviewCopy("Źródła pakietu lokalnego", "Pobierz", "Pakiet startowy", "Osobny model rozmowy",
                "licencja", "nieznane", "bajty", "rewizja",
                "Zatwierdzenie oznacza zapoznanie się ze wszystkimi źródłami i licencjami, także brakującymi informacjami, oraz zgodę na pobranie tylko wybranego pakietu. Audyt licencji i praw dystrybucji jest niepełny. Jakość języków i działanie na urządzeniu nie są potwierdzone. Modele nie są wybierane automatycznie. Pakiet startowy nie zawiera modelu rozmowy. Rozmiar pobrania i miejsce tymczasowe podaje status konfiguracji.")
            else -> ReviewCopy("Local pack sources", "Download", "Starter pack", "Separate conversation model",
                "license", "unknown", "bytes", "revision",
                "Approval acknowledges all listed sources and licenses, including unknowns, and authorizes only the chosen scope's download. License and redistribution audit is incomplete. Language quality and device readiness are unverified. No model is selected automatically. Starter excludes conversation. Download and temporary storage estimates are in setup status.")
        }
        return buildString {
            append(copy.heading).append(": ").append(VERSION).append('\n')
            append(copy.download).append(": ")
                .append(if (scope == "STARTER") copy.starter else copy.conversation)
                .append(" [").append(scope).append("]\n")
            append("SHA-256: ").append(fingerprint).append('\n')
            (artifacts + optionalArtifacts).forEach { artifact ->
                append(if (artifact in artifacts) copy.starter else copy.conversation)
                    .append(": ").append(artifact.id).append('\n')
                append(artifact.source).append('\n')
                // Exact revision is already visible in pinned source URLs. Do not duplicate it.
                if (artifact.revision == null || !artifact.source.contains(artifact.revision))
                    append(copy.revision).append(": ").append(artifact.revision ?: copy.unknown).append("; ")
                append(copy.license).append(": ").append(artifact.declaredLicense ?: copy.unknown)
                    .append("; ").append(copy.bytes).append(": ").append(artifact.bytes).append('\n')
            }
            append(copy.disclosure)
        }.takeIf { it.length in 1..2_000 }
    }

    private data class ReviewCopy(val heading: String, val download: String, val starter: String,
        val conversation: String, val license: String, val unknown: String, val bytes: String,
        val revision: String, val disclosure: String)

    val downloadBytes: Long get() = artifacts.fold(0L) { total, artifact -> Math.addExact(total, artifact.bytes) }
    val missingRoles: Set<OfflinePackRole> = OfflinePackRole.entries.toSet() - artifacts.map { it.role }.toSet()
    /** Invalidates a displayed review when source pins, runtime or declared metadata change. */
    val fingerprint: String by lazy {
        val identity = buildString {
            append(VERSION)
            (artifacts + optionalArtifacts).forEach { artifact ->
                append('\n').append(artifact.id).append('|').append(artifact.role).append('|')
                    .append(artifact.source).append('|').append(artifact.revision).append('|')
                    .append(artifact.declaredLicense).append('|').append(artifact.runtime).append('|')
                    .append(artifact.declaredLanguages.sorted().joinToString(","))
                artifact.files.forEach { append('|').append(it.name).append(':').append(it.bytes).append(':').append(it.sha256) }
            }
        }
        MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

/** A point-in-time file check. Never grants routing, action authority, or qualification. */
data class OfflinePackInventory(
    val manifestFingerprint: String,
    val verifiedRoles: Set<OfflinePackRole>,
    val compatibleSpeechPackFingerprints: Set<String>,
    val selectedSupertonicPackPresent: Boolean,
    val selectedSupertonicVoiceAudibleConfirmed: Boolean,
) {
    val missingFileRoles: Set<OfflinePackRole> get() = OfflinePackRole.entries.toSet() - verifiedRoles
    // This read-only owner has no exact artifact/backend/device evidence dependency.
    // It cannot attest qualification or disqualify future measured candidates. Existing
    // role/workload evidence owners remain authoritative for that separate question.
    val qualificationStatus: String get() = "NOT_ATTESTED_BY_PACK_INSPECTION"
}

data class OfflineConversationExtensionState(
    val transactionId: String,
    val available: Boolean,
    val busy: Boolean,
    val recoveryRequired: Boolean,
    val phase: String,
    val installedAndIntact: Boolean,
    val remainingDownloadBytes: Long,
    val requiredStorageBytes: Long,
    val availableStorageBytes: Long,
    val hasEnoughStorage: Boolean,
    val message: String?,
)
