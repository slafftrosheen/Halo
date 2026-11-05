package com.halo.app.ui.presence

/** Presentation only: these IDs still dispatch through their live page/domain owners. */
enum class ShellActionRole { DESTINATION, PRIMARY, CONTEXTUAL, SECONDARY }
enum class ShellActionGroup { FILE, EDIT, INSERT, VIEW, OPTIONS }
enum class ShellPrimaryPlacement { TOOLBAR, INLINE }
data class ShellActionPresentation(val role: ShellActionRole, val group: ShellActionGroup, val order: Int = 100, val placement: ShellPrimaryPlacement = ShellPrimaryPlacement.TOOLBAR)

object WorkspaceActionPresentation {
    private val placements: Map<String, ShellActionPresentation> = buildMap {
        put("child.back", ShellActionPresentation(ShellActionRole.DESTINATION, ShellActionGroup.VIEW))
        put("page.references.import", ShellActionPresentation(ShellActionRole.PRIMARY, ShellActionGroup.FILE, placement = ShellPrimaryPlacement.INLINE))
        put("page.references.search", ShellActionPresentation(ShellActionRole.PRIMARY, ShellActionGroup.VIEW, placement = ShellPrimaryPlacement.INLINE))
        listOf(
            "page.office.new",
            "page.office.open",
            "page.office.export",
            "page.office.graphics",
            "page.note.export",
            "page.studio.new",
            "page.studio.new-canvas",
            "page.studio.save",
            "page.studio.export",
            "page.studio.export-jpeg",
            "page.studio.versions",
            "page.studio.copy",
            "page.studio.discard",
            "page.canvas.export",
            "page.canvas.retry-save",
            "page.notebook.new-note",
            "page.notebook.files", "page.notebook.import",
            "page.study.new",
            "page.study.save-notes",
            "page.study.delete"
        ).forEach { put(it, ShellActionPresentation(ShellActionRole.SECONDARY, ShellActionGroup.FILE)) }
        listOf(
            "page.note.undo",
            "page.note.redo",
            "page.note.rename",
            "page.note.find",
            "page.canvas.rename",
            "page.canvas.undo",
            "page.canvas.find",
            "page.canvas.arrange",
            "page.studio.undo",
            "page.studio.redo",
            "page.studio.rename",
            "page.studio.background",
            "page.studio.transform",
            "page.office.edit",
            "page.notebook.organize",
            "page.notebook.select-visible",
            "page.notebook.selection-actions",
            "page.notebook.selection-done"
        ).forEach { put(it, ShellActionPresentation(ShellActionRole.SECONDARY, ShellActionGroup.EDIT)) }
        listOf(
            "page.note.insert",
            "page.canvas.add",
            "page.studio.add-layer",
            "page.studio.text",
            "page.studio.shape",
            "page.studio.draw",
            "page.studio.image",
            "page.studio.camera",
            "page.office.attach",
            "page.study.material",
            "page.memory.add",
            "page.notebook.capture"
        ).forEach { put(it, ShellActionPresentation(ShellActionRole.SECONDARY, ShellActionGroup.INSERT)) }
        listOf(
            "page.note.outline",
            "page.note.view-edit",
            "page.note.view-read",
            "page.canvas.fit",
            "page.canvas.view-navigation",
            "page.canvas.view-select",
            "page.studio.layers",
            "page.study.details",
            "page.study.read",
            "page.study.listen",
            "page.study.stop-reading",
            "page.study.practice",
            "page.study.back-to-lesson",
            "page.memory.view-history",
            "page.memory.view-memories",
            "page.memory.view-suggestions",
            "page.notebook.view-library",
            "page.notebook.view-tools",
            "page.notebook.chats",
            "page.notebook.context",
            "page.notebook.related",
            "page.notebook.inbox"
        ).forEach { put(it, ShellActionPresentation(ShellActionRole.SECONDARY, ShellActionGroup.VIEW)) }
        listOf(
            "page.note.assist",
            "page.note.tools",
            "page.canvas.options",
            "page.canvas.tools",
            "page.memory.settings",
            "page.office.study",
            "page.office.tools",
            "page.studio.ai",
            "page.studio.tools",
            "page.studio.continue",
            "page.study.finish",
            "page.study.next",
            "page.study.options",
            "page.notebook.continue",
            "page.notebook.memory-review",
            "page.notebook.review"
        ).forEach { put(it, ShellActionPresentation(ShellActionRole.SECONDARY, ShellActionGroup.OPTIONS)) }
        listOf(
            "page.studio.add-layer",
            "page.studio.layers",
            "page.studio.tools",
            "page.studio.ai",
            "page.note.insert",
            "page.note.outline",
            "page.note.tools",
            "page.note.assist",
            "page.canvas.add",
            "page.canvas.arrange",
            "page.canvas.fit",
            "page.canvas.tools",
            "page.notebook.new-note",
            "page.notebook.import",
            "page.study.new",
            "page.study.material",
            "page.study.practice",
            "page.study.read",
            "page.memory.add", "page.office.new", "page.office.open"
        ).forEachIndexed { index, id -> put(id, getValue(id).copy(role = ShellActionRole.PRIMARY, order = index)) }
        listOf(
            "page.notebook.view-library",
            "page.notebook.view-tools",
            "page.notebook.chats",
            "page.notebook.inbox",
            "page.memory.view-memories",
            "page.memory.view-history",
            "page.memory.view-suggestions",
            "page.study.back-to-lesson"
        ).forEach { id -> put(id, getValue(id).copy(role = ShellActionRole.DESTINATION)) }
    }
    fun find(id: String): ShellActionPresentation? = placements[id.removePrefix("page:")]
    fun require(id: String): ShellActionPresentation = requireNotNull(find(id)) { "Missing Workspace action presentation: $id" }
}
