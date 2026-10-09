# UI-LANGUAGE-001 — English, Russian and Latvian

One persistent app language owns the interface and the default AI response language. Settings offers native names: English, Русский, Latviešu. With no saved selection, a supported device language is used; other device languages fall back to English.

## Behavior and ownership

- Android resource catalogues contain matching EN/RU/LV keys. MainActivity and the native Assistant surface use a localized Compose resource context. Switching language keeps navigation, current operations and unsent drafts; it does not restart the Activity/runtime or change the system locale.
- The first catalogue covers navigation, Home/orb state labels, common controls, Settings, capture/extraction review, Workspace entry actions and Study/reading/practice. Advanced technical/provider diagnostics can retain their original English wording. Source titles, quotations, model output, code and tool results are not passed through the UI catalogue.
- The same selected language is supplied as a bounded provider-neutral default-response instruction through both Runtime context assembly (text/voice) and standalone AI routing (Workspace/Study, including remote streaming and fallback). It changes neither provider/model selection nor tool authority or context-sharing permissions.
- Explicit user language requests and Study explanation-language overrides take precedence. A blank explanation-language field follows the app language. Original assignment terms and quoted material retain their own language.
- Selecting the language also updates the existing recognition-language preference. Speech output defaults to the app language when the speech event has no explicit language. Installed provider/model/voice language support remains authoritative: nothing downloads, connects or switches providers automatically. The optional English Kokoro pack remains English-only.
- Draft/history/card storage remains canonical. Study summaries use numeric data and resource formatting so changing language refreshes counters without altering saved records.

## Maintenance

Edit `app/src/main/localization/ui.tsv`, then run `python scripts/generate_ui_languages.py`. This deterministically writes the three Android XML catalogues and the bounded app-authored-label lookup. Use `uiText` only for app-authored UI text; never apply it to generated replies or user/source text. Formatted counters use indexed arguments and language-neutral count wording.

Android resource behavior follows the [Compose resources documentation](https://developer.android.com/develop/ui/compose/resources).

## Acceptance

Automated gates cover compiled Cyrillic/Latvian resources, the English catalogue, formatted counters, persistent selection, safe unsupported-language fallback, new-request language changes, direct/streaming routing, preservation of explicit request text and conversation privacy with the language preference enabled.

The combined phone pass must select each language, reopen Halo, verify navigation/capture/Study controls and larger-text wrapping, ask the same simple question in Home, voice, Workspace and Study, then request a different reply language explicitly. Confirm source wording is unchanged. Actual model language quality, TTS availability and native-reader usability require observation; compiled resources and prompt tests do not establish those results.
