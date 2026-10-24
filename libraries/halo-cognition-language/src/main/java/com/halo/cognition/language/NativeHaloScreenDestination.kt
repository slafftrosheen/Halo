package com.halo.cognition.language

/** Parameter-free Halo navigation only. Object IDs and protected setup flows are resolved by their owners. */
enum class NativeHaloScreenDestination(val wireId: String, vararg val phrases: String) {
    HOME("home", "open halo", "open halo home", "open halo home screen", "открой главную halo", "atver halo sākumu", "otwórz stronę główną halo"),
    SCREEN("screen", "open halo screen", "открой экран halo", "atver halo ekrānu", "otwórz ekran halo"),
    WORKSPACE("notebook", "open workspace", "open my workspace", "open halo workspace", "open notebook", "open my notebook", "open halo notebook", "show my notebook", "открой рабочее пространство", "atver darbvietu", "otwórz obszar roboczy"),
    STUDY("study", "open study", "open halo study", "open study page", "открой обучение", "atver mācības", "otwórz naukę"),
    STUDIO("studio", "open studio", "open halo studio", "открой студию", "atver studiju", "otwórz studio"),
    FILE_TOOLS("file_tools", "open file tools", "open halo file tools", "открой инструменты файлов", "atver failu rīkus", "otwórz narzędzia plików"),
    DEVICES("devices", "open halo devices", "open devices in halo", "открой устройства halo", "atver halo ierīces", "otwórz urządzenia halo"),
    SETTINGS("settings", "open halo settings", "open halo preferences", "открой настройки halo", "atver halo iestatījumus", "otwórz ustawienia halo"),
    ACTIVITY("activity", "open halo activity", "open my halo activity", "открой активность halo", "atver halo aktivitāti", "otwórz aktywność halo"),
    SKILLS("skills", "open halo skills", "open skills in halo", "открой навыки halo", "atver halo prasmes", "otwórz umiejętności halo"),
    SYSTEM_CHECK("system_check", "open halo system check", "открой проверку системы halo", "atver halo sistēmas pārbaudi", "otwórz kontrolę systemu halo"),
    CONVERSATIONS("notebook/conversations", "open halo conversations", "открой беседы halo", "atver halo sarunas", "otwórz rozmowy halo"),
    MEMORY("notebook/memory", "open halo memory", "открой память halo", "atver halo atmiņu", "otwórz pamięć halo"),
    NOTIFICATIONS("notifications", "open halo notifications", "открой уведомления halo", "atver halo paziņojumus", "otwórz powiadomienia halo"),
    INTELLIGENCE("ai_settings", "open halo intelligence", "открой интеллект halo", "atver halo intelektu", "otwórz inteligencję halo"),
    VOICE("voice_settings", "open halo voice settings", "открой голосовые настройки halo", "atver halo balss iestatījumus", "otwórz ustawienia głosu halo"),
    PERMISSIONS("permissions", "open halo permissions", "открой разрешения halo", "atver halo atļaujas", "otwórz uprawnienia halo"),
    INTEGRATIONS("integrations", "open halo integrations", "открой интеграции halo", "atver halo integrācijas", "otwórz integracje halo"),
    AUTOMATION("automation", "open halo automation", "открой автоматизацию halo", "atver halo automatizāciju", "otwórz automatyzację halo"),
    STORAGE("storage_backup", "open halo storage and backup", "открой хранилище и резервные копии halo", "atver halo krātuvi un dublēšanu", "otwórz pamięć i kopie zapasowe halo"),
    SPEECH_INPUT("settings/voice/input", "open halo speech input", "открой распознавание речи halo", "atver halo runas ievadi", "otwórz rozpoznawanie mowy halo"),
    SPEECH_OUTPUT("settings/voice/output", "open halo speech output", "открой синтез речи halo", "atver halo runas izvadi", "otwórz syntezę mowy halo"),
    PROVIDERS("settings/intelligence/providers", "open halo providers", "открой провайдеры halo", "atver halo pakalpojumu sniedzējus", "otwórz dostawców halo"),
    MODELS("model_browser", "open halo models", "открой модели halo", "atver halo modeļus", "otwórz modele halo");

    companion object {
        fun fromWire(value: String): NativeHaloScreenDestination? = entries.firstOrNull { it.wireId == value }
        fun parse(command: String): NativeHaloScreenDestination? = entries.firstOrNull { command in it.phrases }
    }
}
