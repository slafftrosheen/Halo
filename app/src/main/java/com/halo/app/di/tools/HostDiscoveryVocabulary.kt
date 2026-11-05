package com.halo.app.di.tools

/** Search metadata, not routing or authority. The host owns domain vocabulary. */
internal object HostDiscoveryVocabulary {
    fun domain(id: String): String? = when {
        id.startsWith("halo_organizer_") || id.startsWith("halo_commitment") -> "Organizer"
        id.startsWith("halo_material_image_") -> "Source images"
        id.startsWith("halo_source_") -> "Sources"
        id.startsWith("halo_research_") || id in ReferenceLibraryContracts.names -> "Reference libraries"
        id.startsWith("halo_library_") -> "Workspace Library"
        id.startsWith("halo_note_") || id.startsWith("halo_text_") -> "Notes"
        id.startsWith("halo_studio_") -> "Studio"
        id.startsWith("halo_canvas_") -> "Canvas"
        id.startsWith("halo_study_") -> "Study"
        id.startsWith("halo_brief_") -> "Your Day"
        id.startsWith("halo_clock_") || id.startsWith("halo_world_clock") -> "World clock"
        else -> null
    }
    fun priority(id: String): Int = if (id in EverydayToolContracts.queries || id in setOf("halo_note_get", "halo_note_read_block", "halo_library_query", "halo_library_catalogue", "halo_library_get")) 10 else 0
    fun keywords(id: String): Set<String> = domainWords(id) + when {
        id in setOf("halo_organizer_create", "halo_commitment_save", "halo_note_create") -> setOf("создай", "добавь", "запиши", "izveido", "utwórz")
        priority(id) > 0 -> setOf("найди", "покажи", "прочитай", "список", "atrast", "pokaż", "znajdź")
        else -> emptySet()
    }
    private fun domainWords(id: String): Set<String> = when (domain(id)) {
        "Organizer" -> setOf("organizer", "calendar", "task", "органайзер", "календарь", "календаре", "задача", "задачу", "задачи", "дела", "напоминание", "будильник", "таймер", "kalendarz", "zadania", "kalendārs", "uzdevumi")
        "Source images" -> setOf("news", "images", "pictures", "photo", "новости", "картинки", "изображения", "фото", "attēli", "ziņas", "obrazy", "zdjęcia", "wiadomości")
        "Sources" -> setOf("sources", "источник", "источники", "источников", "źródła", "avoti")
        "Reference libraries" -> setOf("wiki", "research", "database", "library", "вики", "поиск", "исследование", "библиотека", "библиотеки", "библиотеку", "справочник", "база", "базу", "medical", "health", "survival", "первая помощь", "выживания", "базы", "данных", "медицина", "выживание", "bibliotēka", "pētījums", "biblioteka", "badania")
        "Workspace Library" -> setOf("workspace", "library", "collection", "inbox", "рабочая область", "коллекция", "входящие", "kolekcija", "kolekcja", "darbvieta")
        "Notes" -> setOf("notes", "heading", "paragraph", "заметка", "заметку", "заметки", "заметках", "заголовок", "абзац", "piezīmes", "notatki")
        "Studio" -> setOf("image", "photo", "студия", "изображение", "фото", "attēls", "obraz")
        "Canvas" -> setOf("canvas", "board", "холст", "доска", "audekls", "tablica")
        "Study" -> setOf("study", "учёба", "обучение", "урок", "mācības", "nauka")
        "Your Day" -> setOf("brief", "ваш день", "сводка", "сводку", "kopsavilkums", "podsumowanie")
        "World clock" -> setOf("clock", "часы", "время", "pulkstenis", "zegar")
        else -> emptySet()
    }
}
