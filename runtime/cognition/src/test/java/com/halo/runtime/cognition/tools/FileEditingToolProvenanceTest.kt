package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.RemoteDataCategory
import org.junit.Assert.*
import org.junit.Test

class FileEditingToolProvenanceTest {
    @Test fun everyFileEditingResultPreservesWorkspaceProvenanceIncludingWrites() {
        val names = listOf("halo_file_tool_capabilities", "halo_file_tools_open", "halo_text_create", "halo_text_import", "halo_text_get",
            "halo_text_edit", "halo_text_export", "halo_image_transform", "halo_image_batch", "halo_image_collage", "halo_file_batch_status",
            "halo_pdf_page_info", "halo_pdf_extract_page_image", "halo_pdf_annotation_create", "halo_pdf_annotation_get", "halo_pdf_annotation_edit",
            "halo_pdf_annotation_export_page_image", "halo_file_attach_canvas")
        names.forEach { assertEquals(setOf(RemoteDataCategory.TOOLS, RemoteDataCategory.WORKSPACE), remoteToolResultCategories(it)) }
    }
}
