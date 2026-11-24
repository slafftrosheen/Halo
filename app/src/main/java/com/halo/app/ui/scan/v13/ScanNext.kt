package com.halo.app.ui.scan.v13

/**
 * halo 13.0 · Scan offers what fits the page after it is taken, instead of a dozen buttons around the camera.
 * Actions map to the existing HaloScanScreen instrument ids at 99aac2f (scan.read_it_to_me, scan.translate, …).
 */
enum class PageKind { TEXT, FORM, RECEIPT, OBJECT }

enum class ScanAction(val instrumentId: String?) {
    READ_IT_TO_ME("scan.read_it_to_me"), TRANSLATE("scan.translate"), SAVE_NOTE(null), SAVE_PDF("scan.save_pdf"),
    COPY_TEXT("scan.found_text"), FILL_FORM("scan.fill_a_form"), SIGN("scan.sign"), SAVE_TO_INBOX(null),
    DATE_TO_PLANNER(null), WHAT_IS_THIS(null), FIX_PAGE("scan.edges"),
}

object ScanNext {
    private val money = Regex("""\d+[.,]\d{2}\s*(€|EUR|eur)|(€|EUR)\s*\d+[.,]\d{2}|(TOTAL|KOPĀ|ИТОГО|SUMMA)""", RegexOption.IGNORE_CASE)

    /** Same rule as the existing form review: lines with a label ending in a colon. */
    fun kind(text: String): PageKind {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return PageKind.OBJECT
        if (lines.count { money.containsMatchIn(it) } >= 2) return PageKind.RECEIPT
        if (lines.count { Regex("""^[\p{L} ]{2,40}:\s*$""").matches(it) } >= 2) return PageKind.FORM
        return PageKind.TEXT
    }

    /** At most 5 choices, most useful first. Kids never get online look-up without an approved source. */
    fun offers(kind: PageKind, kids: Boolean, approvedKidsSource: Boolean): List<ScanAction> = when (kind) {
        PageKind.TEXT -> listOf(ScanAction.READ_IT_TO_ME, ScanAction.TRANSLATE, ScanAction.SAVE_NOTE, ScanAction.SAVE_PDF, ScanAction.COPY_TEXT)
        PageKind.FORM -> if (kids) listOf(ScanAction.READ_IT_TO_ME) else listOf(ScanAction.FILL_FORM, ScanAction.SIGN, ScanAction.SAVE_PDF, ScanAction.READ_IT_TO_ME)
        PageKind.RECEIPT -> if (kids) listOf(ScanAction.READ_IT_TO_ME) else listOf(ScanAction.SAVE_TO_INBOX, ScanAction.DATE_TO_PLANNER, ScanAction.SAVE_PDF)
        PageKind.OBJECT -> if (!kids || approvedKidsSource) listOf(ScanAction.WHAT_IS_THIS) else emptyList()
    }
}
