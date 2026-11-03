package com.halo.app.data.studio

enum class StudioPreset { BLANK, SQUARE, POSTCARD, PORTRAIT, POSTER, LABEL, SLIDE, MOODBOARD }

/** Small local templates, made from the same editable layers rather than pre-rendered assets. */
object StudioTemplates {
    fun document(preset: StudioPreset, heading: String): StudioDocument {
        require(heading.isNotBlank() && heading.length <= 120)
        if (preset == StudioPreset.BLANK) return StudioDocument()
        val (width, height) = when (preset) {
            StudioPreset.POSTCARD -> 1536 to 1024
            StudioPreset.PORTRAIT -> 1080 to 1920
            StudioPreset.POSTER -> 1440 to 2048
            StudioPreset.LABEL -> 1200 to 600
            StudioPreset.SLIDE -> 1920 to 1080
            StudioPreset.MOODBOARD -> 1600 to 1200
            else -> 1024 to 1024
        }
        return StudioDocument(width, height, background = 0xffffffff.toInt(), layers = listOf(
            StudioLayer(name = heading, kind = StudioLayerKind.RECTANGLE, bounds = StudioRect(60, 60, 880, 880), color = 0xfff0f0f0.toInt()),
            StudioLayer(name = heading, kind = StudioLayerKind.TEXT, bounds = StudioRect(120, 120, 760, 200),
                text = heading, fontSize = 65)))
            .also(StudioDocument::requireValid)
    }
}
