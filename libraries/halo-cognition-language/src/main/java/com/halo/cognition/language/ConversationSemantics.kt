package com.halo.cognition.language

/** Conservative anchored language cues. Mentioning an app or action is not an instruction. */
object ConversationSemantics {
    private val requestPrefix = Regex("^(?:(?:can|could|would|will) you\\s+(?:please\\s+)?|please\\s+)", RegexOption.IGNORE_CASE)
    fun commandText(text: String): String = text.trim().replaceFirst(requestPrefix, "").trimEnd('.', '!', '?')
    private val modelOnly = Regex("\\b(?:don['’]?t|do not) (?:search|use tools)\\b|answer from your model", RegexOption.IGNORE_CASE)
    private val explanation = Regex("^(?:how|why|what|who|when|where|explain|describe|tell me (?:about|how)|write|translate|summari[sz]e|give me|help me brainstorm)\\b", RegexOption.IGNORE_CASE)
    private val workspace = Regex("^(?:find|search|use|read|look up) my (?:notes?|notebook|documents?|files?|memories)\\b", RegexOption.IGNORE_CASE)
    // Explicit domain writes still route through the normal tool authorizer when
    // the optional local intent classifier is absent. These cues supply no slots.
    private val workspaceWrite = Regex(
        "^(?:create|add|edit|update|rename|delete|remove|export|save|attach|duplicate|move|connect|start)\\s+" +
            "(?:(?:a|an|the|my|this|that|new|local|empty|selected|existing|text|canvas|study)\\s+){0,3}" +
            "(?:notes?|documents?|files?|cards?|boards?|canvas|study\\s+session)\\b|" +
            "^(?:создай|добавь|измени|обнови|переименуй|удали|экспортируй|сохрани|прикрепи|перемести)\\s+" +
            "(?:(?:новую|новый|новое|выбранную|выбранный|выбранные|эту|этот|мою|мой|пустую|пустой)\\s+){0,2}" +
            "(?:заметк[ауи]|документ(?:ы|а)?|файл(?:ы|а)?|карточк[ауи]|холст|доск[ау])\\b|" +
            "^(?:izveido|pievieno|rediģē|maini|pārdēvē|dzēs|eksportē|saglabā|pārvieto)\\s+" +
            "(?:(?:jaunu|atlasīto|izvēlēto|šo|manu)\\s+){0,2}(?:piezīmi|piezīmes|dokumentu|failu|karti|kartīti|tāfeli)\\b|" +
            "^(?:utwórz|dodaj|edytuj|zmień|usuń|eksportuj|zapisz|dołącz|przenieś)\\s+" +
            "(?:(?:nową|nowy|wybraną|wybrany|tę|ten|moją|mój)\\s+){0,2}(?:notatkę|notatki|dokument|plik|kartę|tablicę)\\b",
        RegexOption.IGNORE_CASE
    )
    private val screen = Regex("^(?:read this screen and|press|tap|click|scroll|swipe)\\b", RegexOption.IGNORE_CASE)
    /** Composite requests with unresolved app goals must not degrade into a single app name. */
    private val appWorkflow = Regex(
        "^(?:open|launch|start)\\s+.+?\\s+(?:and\\s+then|then|and)\\s+(?:find|search|look\\s+for|tap|click|press|type|enter|scroll|select|choose|read|show)\\b|" +
            "^(?:find|search|look\\s+for)\\s+.+?\\s+(?:in|inside|within)\\s+.+\\b|" +
            "^search\\s+(?:in|inside)\\s+.+?\\s+for\\s+.+$",
        RegexOption.IGNORE_CASE
    )
    private val device = Regex("^(?:turn (?:on|off) (?:the )?(?:flashlight|torch)|send .+ (?:a )?message)\\b", RegexOption.IGNORE_CASE)
    private val flexibleDevice = Regex("^(?:set|change|adjust|increase|decrease|lower|raise) (?:my |the )?(?:phone |screen |media )?(?:volume|brightness|ringer)\\b|^(?:turn|switch) (?:on|off) (?:my |the )?(?:wifi|wi-fi|bluetooth)\\b|^reply to (?:the |my )?(?:notification|message)\\b|^dismiss (?:the |all |my )?notifications?\\b|^copy .+ to (?:the )?clipboard\\b", RegexOption.IGNORE_CASE)
    // The local intent LLM can be unavailable while a different conversational
    // model is resident. These narrowly anchored multilingual hints preserve
    // common device/app requests without treating explanatory questions as acts.
    private val multilingualExplanation = Regex(
        "^(?:как|почему|зачем|что\\s+такое|объясни|расскажи|kā|kāpēc|kas\\s+ir|paskaidro|jak|dlaczego|co\\s+to|wyjaśnij)\\b",
        RegexOption.IGNORE_CASE
    )
    private val multilingualScreen = Regex(
        "^(?:нажми|прокрути|введи|выбери|tap|nospied|ritini|ievadi|izvēlies|kliknij|naciśnij|przewiń|wpisz|wybierz)\\s+\\S+",
        RegexOption.IGNORE_CASE
    )
    private val multilingualComposite = Regex(
        "^(?:открой|запусти|atver|palaid|otwórz|uruchom)\\s+.+?\\s+" +
            "(?:и|затем|un|tad|i|następnie)\\s+" +
            "(?:найди|поищи|нажми|введи|atrodi|meklē|nospied|ievadi|znajdź|wyszukaj|kliknij|wpisz)\\b",
        RegexOption.IGNORE_CASE
    )
    private val multilingualWorkspace = Regex(
        "^(?:найди|поищи|покажи)\\s+(?:мои|мою|в\\s+моих)\\s+(?:заметки|заметку|записи|документы)|" +
            "^(?:atrodi|meklē)\\s+(?:manas|manus|manā)\\s+(?:piezīmes|dokumentus|piezīmēs)|" +
            "^(?:znajdź|wyszukaj)\\s+(?:moje|moich|mój)\\s+(?:notatki|dokumenty|pliki)\\b",
        RegexOption.IGNORE_CASE
    )
    private val multilingualDevice = Regex(
        "^(?:(?:включи|выключи)\\s+(?:фонарик|фонарь|bluetooth|блютуз)|" +
            "(?:поставь|установи)\\s+(?:таймер|будильник)|" +
            "отправь\\s+сообщение|atver\\s+\\S+|otwórz\\s+\\S+|" +
            "(?:ieslēdz|izslēdz)\\s+(?:lukturīti|bluetooth)|" +
            "(?:uzstādi|iestati)\\s+(?:taimeri|modinātāju)|" +
            "nosūti\\s+ziņu|(?:włącz|wyłącz)\\s+(?:latarkę|bluetooth)|" +
            "ustaw\\s+(?:minutnik|budzik)|wyślij\\s+wiadomość)\\b",
        RegexOption.IGNORE_CASE
    )
    fun answerFromModel(text: String): Boolean = modelOnly.containsMatchIn(text.replace('\u2019', '\''))
    fun isExplanation(text: String): Boolean = explanation.containsMatchIn(text.trim()) ||
        multilingualExplanation.containsMatchIn(text.trim()) ||
        Regex("^search engines?\\b", RegexOption.IGNORE_CASE).containsMatchIn(text.trim())
    fun actionReason(text: String): ToolActionReason? {
        if (answerFromModel(text) || isExplanation(text)) return null
        val command = commandText(text)
        return when {
            workspaceWrite.containsMatchIn(command) -> ToolActionReason.ACTION_INTENT
            appWorkflow.containsMatchIn(command) -> ToolActionReason.SCREEN_OPERATION
            workspace.containsMatchIn(command) -> ToolActionReason.WORKSPACE_LOOKUP
            screen.containsMatchIn(command) -> ToolActionReason.SCREEN_OPERATION
            multilingualComposite.containsMatchIn(command) ||
                multilingualScreen.containsMatchIn(command) -> ToolActionReason.SCREEN_OPERATION
            multilingualWorkspace.containsMatchIn(command) -> ToolActionReason.WORKSPACE_LOOKUP
            device.containsMatchIn(command) -> ToolActionReason.ACTION_INTENT
            multilingualDevice.containsMatchIn(command) -> ToolActionReason.ACTION_INTENT
            flexibleDevice.containsMatchIn(command) -> ToolActionReason.ACTION_INTENT
            else -> null
        }
    }
}
