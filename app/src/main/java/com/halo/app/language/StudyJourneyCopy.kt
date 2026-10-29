package com.halo.app.language

/** App-authored Study/Context messages only; never translate learner/source/model text. */
internal object StudyJourneyCopy {
    private data class Copy(val ru: String, val lv: String, val pl: String)
    private val messages = mapOf(
        "Open file" to Copy("Открыть файл", "Atvērt failu", "Otwórz plik"),
        "Progress" to Copy("Прогресс", "Progress", "Postęp"),
        "Partial" to Copy("Частично", "Daļēji", "Częściowo"),
        "No readable text" to Copy("Нет доступного текста", "Nav nolasāma teksta", "Brak tekstu do odczytu"),
        "Image · Requires verified vision" to Copy("Изображение · Нужна проверенная модель с поддержкой изображений", "Attēls · Nepieciešams pārbaudīts attēlu izpratnes modelis", "Obraz · Wymagany zweryfikowany model rozumienia obrazów"),
        "PDF page image · Requires verified vision · OCR not installed" to Copy("Изображение страницы PDF · Нужна проверенная модель с поддержкой изображений · Распознавание текста не установлено", "PDF lapas attēls · Nepieciešams pārbaudīts attēlu izpratnes modelis · Teksta atpazīšana nav instalēta", "Obraz strony PDF · Wymagany zweryfikowany model rozumienia obrazów · Rozpoznawanie tekstu nie jest zainstalowane"),
        "Only part of this source is included (%1\$d of %2\$d characters omitted)." to Copy("Используется только часть источника (пропущено %1\$d из %2\$d символов).", "Tiek izmantota tikai daļa avota (izlaistas %1\$d no %2\$d rakstzīmēm).", "Uwzględniono tylko część źródła (pominięto %1\$d z %2\$d znaków)."),
        "Could not save the material selection. Choose it again after reopening this lesson." to Copy("Не удалось сохранить выбор материала. Выберите его снова после открытия урока.", "Materiāla atlasi neizdevās saglabāt. Pēc nodarbības atkārtotas atvēršanas atlasi to vēlreiz.", "Nie udało się zapisać wyboru materiału. Wybierz go ponownie po otwarciu lekcji."),
        "Page" to Copy("Страница", "Lappuse", "Strona"),
        "Selected passage" to Copy("Выбранный отрывок", "Atlasītais fragments", "Zaznaczony fragment"),
        "Study material changed or became unavailable. Reopen the lesson." to Copy("Материал изменился или стал недоступен. Откройте урок заново.", "Materiāls mainījās vai kļuva nepieejams. Atver nodarbību vēlreiz.", "Materiał zmienił się lub stał się niedostępny. Otwórz lekcję ponownie."),
        "The selected lesson changed or its material became unavailable. Reopen the lesson before continuing." to Copy("Выбранный урок изменился или его материал стал недоступен. Откройте урок заново, чтобы продолжить.", "Atlasītā nodarbība mainījās vai tās materiāls kļuva nepieejams. Lai turpinātu, atver nodarbību vēlreiz.", "Wybrana lekcja zmieniła się lub jej materiał stał się niedostępny. Otwórz ją ponownie, aby kontynuować."),
        "Selected source changed or was excluded. Please select it again." to Copy("Выбранный источник изменился или был исключён. Выберите его заново.", "Atlasītais avots mainījās vai tika izslēgts. Atlasi to vēlreiz.", "Wybrane źródło zmieniło się lub zostało wykluczone. Wybierz je ponownie."),
        "Selected lesson changed. Reopen it before continuing." to Copy("Выбранный урок изменился. Откройте его заново, чтобы продолжить.", "Atlasītā nodarbība mainījās. Lai turpinātu, atver to vēlreiz.", "Wybrana lekcja zmieniła się. Otwórz ją ponownie, aby kontynuować."),
        "Try two small hints first, then ask for the full solution again." to Copy("Сначала попробуйте две небольшие подсказки, затем снова попросите полное решение.", "Vispirms izmēģini divus nelielus mājienus, tad vēlreiz lūdz pilnu risinājumu.", "Najpierw skorzystaj z dwóch krótkich wskazówek, a potem ponownie poproś o pełne rozwiązanie."),
        "Study material changed or became unavailable while Halo was replying. Your earlier work is kept; refresh the material before asking again." to Copy("Пока halo отвечал, материал изменился или стал недоступен. Ваша работа сохранена. Обновите материал перед новым вопросом.", "Kamēr halo atbildēja, materiāls mainījās vai kļuva nepieejams. Tavs darbs ir saglabāts. Pirms nākamā jautājuma atsvaidzini materiālu.", "Podczas odpowiedzi halo materiał zmienił się lub stał się niedostępny. Twoja praca jest zachowana. Odśwież materiał przed kolejnym pytaniem."),
        "This reply did not match the current request. No learning progress was accepted." to Copy("Ответ не соответствует текущему запросу. Прогресс обучения не изменён.", "Šī atbilde neatbilst pašreizējam pieprasījumam. Mācību progress netika mainīts.", "Odpowiedź nie pasowała do bieżącego pytania. Postęp nauki nie został zmieniony."),
        "Review changed or unavailable material first" to Copy("Сначала проверьте изменённый или недоступный материал", "Vispirms pārbaudi mainīto vai nepieejamo materiālu", "Najpierw sprawdź zmieniony lub niedostępny materiał"),
        "Study material changed while creating practice. No cards were saved; refresh the material first." to Copy("При создании упражнений материал изменился. Карточки не сохранены. Сначала обновите материал.", "Veidojot vingrinājumus, materiāls mainījās. Kartītes netika saglabātas. Vispirms atsvaidzini materiālu.", "Materiał zmienił się podczas tworzenia ćwiczeń. Nie zapisano fiszek. Najpierw odśwież materiał."),
        "Your saved study note was edited, moved or has an unsaved draft. Open it to keep your changes; Halo has not overwritten it." to Copy("Заметка была изменена, перемещена или содержит несохранённый черновик. Откройте её, чтобы сохранить свои изменения. halo её не перезаписывал.", "Saglabātā mācību piezīme ir mainīta, pārvietota vai tai ir nesaglabāts melnraksts. Atver to, lai saglabātu izmaiņas. halo to nav pārrakstījis.", "Zapisana notatka została zmieniona, przeniesiona lub ma niezapisany szkic. Otwórz ją, aby zachować zmiany. halo jej nie nadpisało."),
        "The study note was saved, but its lesson link could not be saved. Open the note before exporting again." to Copy("Заметка сохранена, но ссылку на неё в уроке сохранить не удалось. Откройте заметку перед повторным экспортом.", "Mācību piezīme ir saglabāta, bet tās saiti nodarbībā nevarēja saglabāt. Pirms atkārtotas eksportēšanas atver piezīmi.", "Notatka została zapisana, ale nie udało się zapisać odnośnika w lekcji. Otwórz notatkę przed ponownym eksportem."),
        "Your selected passage changed. Select it again before starting Study." to Copy("Выбранный отрывок изменился. Выберите его заново перед началом обучения.", "Atlasītais fragments mainījās. Pirms mācību sākšanas atlasi to vēlreiz.", "Zaznaczony fragment zmienił się. Zaznacz go ponownie przed rozpoczęciem nauki."),
        "Select a shorter passage before starting Study." to Copy("Выберите более короткий отрывок перед началом обучения.", "Pirms mācību sākšanas atlasi īsāku fragmentu.", "Zaznacz krótszy fragment przed rozpoczęciem nauki."),
        "Study notes" to Copy("Учебные заметки", "Mācību piezīmes", "Notatki z nauki"),
        "Sources" to Copy("Источники", "Avoti", "Źródła"),
        "Knowledge policy" to Copy("Используемые знания", "Izmantotās zināšanas", "Wykorzystywana wiedza"),
        "Selected material only" to Copy("Только выбранный материал", "Tikai atlasītais materiāls", "Tylko wybrany materiał"),
        "Material and general knowledge" to Copy("Материал и общие знания", "Materiāls un vispārīgās zināšanas", "Materiał i wiedza ogólna"),
        "Explanation language" to Copy("Язык объяснения", "Skaidrojuma valoda", "Język wyjaśnienia"),
        "Depth" to Copy("Подробность", "Detalizācija", "Szczegółowość"),
        "Simpler explanation" to Copy("Более простое объяснение", "Vienkāršāks skaidrojums", "Prostsze wyjaśnienie"),
        "Usual detail" to Copy("Обычная подробность", "Parasta detalizācija", "Zwykła szczegółowość"),
        "Deeper explanation" to Copy("Более глубокое объяснение", "Padziļināts skaidrojums", "Dokładniejsze wyjaśnienie"),
        "Version" to Copy("Версия", "Versija", "Wersja"),
        "Passage" to Copy("Отрывок", "Fragments", "Fragment"),
        "My starting point" to Copy("С чего я начал", "Mans sākuma punkts", "Mój punkt wyjścia"),
        "Introductory question skipped" to Copy("Вводный вопрос пропущен", "Ievadjautājums izlaists", "Pytanie wstępne pominięte"),
        "My reflection" to Copy("Мои выводы", "Manas pārdomas", "Moje przemyślenia"),
        "Explored ideas" to Copy("Изученные идеи", "Izpētītās idejas", "Poznane pomysły"),
        "Tutor notes" to Copy("Объяснения halo", "halo skaidrojumi", "Wyjaśnienia halo"),
        "Your attempts" to Copy("Ваши попытки", "Tavi mēģinājumi", "Twoje próby"),
        "Practice" to Copy("Практика", "Vingrinājumi", "Ćwiczenia"),
        "Source" to Copy("Источник", "Avots", "Źródło"),
        "Not specified" to Copy("Не указан", "Nav norādīta", "Nie podano"),
    )

    fun translate(english: String, language: String): String = messages[english]?.let {
        when (language.substringBefore('-').lowercase()) { "ru" -> it.ru; "lv" -> it.lv; "pl" -> it.pl; else -> english }
    } ?: english
}
