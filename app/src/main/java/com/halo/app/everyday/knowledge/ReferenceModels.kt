package com.halo.app.everyday.knowledge

enum class ReferenceTextFormat { TEXT, WIKITEXT }
enum class ReferenceImportState { REVIEWING, QUEUED, IMPORTING, INDEXING, READY, CANCELLED, FAILED, INTERRUPTED }
enum class ReferenceStarterId(val title: String, val description: String) {
    MEDLINEPLUS("MedlinePlus health topics", "Compact English/Spanish health-topic summaries from the National Library of Medicine. No licensed encyclopedia, drug monographs or images."),
    PREPAREDNESS("Emergency preparedness", "Selected CDC reference text on safe water, food, temperature and disaster cleanup, with original source dates and links. English."),
    WIKIPEDIA("General reference", "A small Wikipedia text selection with optional source images covering science and practical reference, with pinned revisions and attribution. English."),
}
data class ReferenceEntry(
    val id: String, val title: String, val text: String,
    val language: String = "und", val url: String? = null,
    val publisher: String = "Imported library", val revision: String? = null,
    val license: String = "User-supplied; rights not verified", val updatedAt: String? = null,
    val format: ReferenceTextFormat = ReferenceTextFormat.TEXT,
    val images: List<com.halo.app.everyday.collectors.SourceImage> = emptyList(),
)
object ReferenceLimits {
    const val INPUT_BYTES = 256L * 1024 * 1024
    const val EXPANDED_BYTES = 1024L * 1024 * 1024
    const val STORAGE_BYTES = 2L * 1024 * 1024 * 1024
    const val FREE_RESERVE_BYTES = 512L * 1024 * 1024
    const val ENTRY_BYTES = 256 * 1024
    const val METADATA_BYTES = 32 * 1024
    const val PAGE_SIZE = 5
    fun validate(entry: ReferenceEntry) {
        com.halo.app.everyday.collectors.SourceImages.encode(entry.images)
        require(entry.id.isNotBlank() && entry.id.length <= 256 && entry.title.isNotBlank() && entry.title.length <= 500)
        require(entry.text.isNotBlank() && entry.text.toByteArray(Charsets.UTF_8).size <= ENTRY_BYTES) { "An entry exceeds 256 KiB; import a smaller subset" }
        require(entry.language.matches(Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*"))) { "Use a language code such as en, ru, lv, pl or und" }
        require(entry.language.length <= 24 && entry.publisher.length <= 200 && entry.license.length <= 2000 && (entry.revision?.length ?: 0) <= 200 && (entry.updatedAt?.length ?: 0) <= 40)
        entry.url?.let { require(it.length <= 2048 && (it.startsWith("https://") || it.startsWith("http://"))) }
    }
}
