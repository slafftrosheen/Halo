package com.halo.app.ai

import com.halo.providers.litert.models.LiteRtCatalogModel
import com.halo.providers.litert.models.LiteRtModelCatalog
import java.util.Locale

enum class CuratedLocalRole { INTENT_CANDIDATE, LOCAL_SEARCH_CANDIDATE, CONVERSATION_CANDIDATE }

data class CuratedLocalEntry(val artifact: LiteRtCatalogModel, val role: CuratedLocalRole)

/**
 * Consumer discovery projection, NOT runtime qualification or execution authority.
 *
 * Reuse the repository's pinned source identity instead of maintaining a second set of
 * download URLs/checksums. A matching family/name/id, VERIFIED flag, installed file or
 * successful smoke answer must never promote an arbitrary catalogue row to this list.
 * Candidate status deliberately makes no per-language/device/backend recommendation.
 */
object CuratedLocalCatalog {
    val entries: List<CuratedLocalEntry> = listOf(
        CuratedLocalEntry(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4, CuratedLocalRole.INTENT_CANDIDATE),
        CuratedLocalEntry(LiteRtModelCatalog.EMBEDDINGGEMMA2_740M, CuratedLocalRole.LOCAL_SEARCH_CANDIDATE),
        // Existing inspected Apache-2.0 artifact (LOCAL-MODEL-ROLE-002), not a
        // language/device recommendation. An optional explicit download only.
        CuratedLocalEntry(LiteRtModelCatalog.SMOLLM2_360M_INSTRUCT, CuratedLocalRole.CONVERSATION_CANDIDATE),
    )

    // Data-class equality includes source pins, size, task, access, runtime profile,
    // capabilities and minimum runtime, not just a marketing/display identity.
    fun entry(model: LiteRtCatalogModel): CuratedLocalEntry? = entries.firstOrNull { it.artifact == model }

    fun allowsDiscovery(technicalRoute: Boolean, developerToolsEnabled: Boolean): Boolean =
        technicalRoute && developerToolsEnabled

    /**
     * Keep owned/selected/resident/unfinished items manageable without recommending them.
     * Never create a downloadable row absent from the supplied canonical catalogue.
     * Conflicting duplicate IDs are not admitted as curated; a retained conflict uses an
     * unreviewed variant so its warning cannot accidentally become a curated badge.
     */
    fun visible(models: List<LiteRtCatalogModel>, retainedIds: Set<String>): List<LiteRtCatalogModel> {
        val groups = models.groupBy { it.id }
        val curated = entries.mapNotNull { candidate ->
            groups[candidate.artifact.id]?.distinct()?.singleOrNull()
                ?.takeIf { it == candidate.artifact }
        }
        val curatedIds = curated.mapTo(hashSetOf()) { it.id }
        val retained = groups.filterKeys { it in retainedIds && it !in curatedIds }.values.map { variants ->
            variants.lastOrNull { entry(it) == null } ?: variants.last()
        }
        return curated + retained
    }

    /** File integrity only: matching bytes still do not establish execution or language quality. */
    fun matchesInstalledArtifact(model: LiteRtCatalogModel, sha256: String?, bytes: Long, verified: Boolean): Boolean =
        verified && sha256 == model.sha256 && bytes == model.expectedBytes

    /** A known product limitation, not inferred language support from a model name. */
    fun isEnglishFirst(model: LiteRtCatalogModel): Boolean {
        val family = model.family.trim().lowercase(Locale.ROOT)
        val repository = model.repoId.substringAfter('/').lowercase(Locale.ROOT)
        return family == "lfm2.5" || family.startsWith("lfm2.5-") ||
            repository == "lfm2.5" || repository.startsWith("lfm2.5-")
    }
}
