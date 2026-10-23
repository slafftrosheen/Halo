package com.halo.app.data.workspace

enum class WorkspaceFileToolState { READY, LIMITED, IN_DEVELOPMENT }
data class WorkspaceFileToolCapability(val id: String, val state: WorkspaceFileToolState, val limits: String)

/** Shared capability truth for UI and assistant; no provider setup is required for deterministic local tools. */
object WorkspaceFileToolCatalog {
    val capabilities = listOf(
        WorkspaceFileToolCapability("text", WorkspaceFileToolState.READY, "UTF-8 TXT/Markdown; 200000 characters, 1 MB; original files preserved"),
        WorkspaceFileToolCapability("csv", WorkspaceFileToolState.READY, "Literal CSV; 1000 rows, 64 columns, 10000 cells, 16000 characters per cell; no formula execution"),
        WorkspaceFileToolCapability("image", WorkspaceFileToolState.READY, "Local crop/rotate/mirror/resize/PNG/JPEG; 20 MB input/output, 4096 px edge, 12 MP"),
        WorkspaceFileToolCapability("image_batch", WorkspaceFileToolState.READY, "1–6 images processed serially; durable per-item receipts; no automatic replay"),
        WorkspaceFileToolCapability("collage", WorkspaceFileToolState.READY, "2–6 images; horizontal/vertical/grid/hero; new managed output"),
        WorkspaceFileToolCapability("pdf_page_image", WorkspaceFileToolState.LIMITED, "Bounded PDF viewing and raster PNG page extraction; source PDF unchanged; no native PDF text editing"),
        WorkspaceFileToolCapability("pdf_annotations", WorkspaceFileToolState.LIMITED, "Source-linked vector annotation notes; up to 16 marked pages/65536 points; exports page PNG, not modified PDF"),
        WorkspaceFileToolCapability("native_pdf_edit", WorkspaceFileToolState.IN_DEVELOPMENT, "Format-preserving merge/split/reorder/text editing is not available"),
        WorkspaceFileToolCapability("office_roundtrip", WorkspaceFileToolState.IN_DEVELOPMENT, "DOCX/XLSX editing and faithful roundtrips are not available")
    )
}
