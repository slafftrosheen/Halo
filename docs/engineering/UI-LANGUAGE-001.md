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


## Delivery evidence — 2026-10-09

Clean source commit: `21aaca58e759` (`HALO_GIT_DIRTY=false`). The APK contains this source identity. Serial Gradle gate passed: provider SDK 28, AI engine 81, cognition 245, app 453 — **807 tests, zero failures/errors/skips** — plus `:app:assembleDebug`. The Compose language-switch regression confirms that the Activity result owner and unsent draft survive a switch.

`Halo-language-21aaca58-debug.apk` SHA-256: `8e262a7c9183b7d29b89940cce961e68f9f0f9819fe01759757e9387145a4808`. Samsung SM-G990B2 (`R5CTA0TKLZZ`) accepted `adb install -r`; launch returned `Status: ok`. These establish build/install/launch, not real-provider language quality or audible speech acceptance. The combined physical pass remains pending.


## Continued UI coverage — 2026-10-09

The reviewed EN/RU/LV catalogue now includes 1,147 distinct app-authored UI entries. Coverage was expanded in-place on main across Notebook/Canvas/note editing, memory review, model and provider setup, device pairing and controls, voice, automation, permissions, notifications and Home context handoff.

Static labels are routed through \`uiText\` at their Compose presentation sites. Dynamic app-authored memory counts, filter labels, review decisions and episode outcomes are resolved at presentation time. Saved user text, source material, model responses and third-party tool output remain unchanged. Translation resource identifiers are stable SHA-256-derived keys from the English source.

The repository-only consistency check found equal sets of 1,147 identifiers in English, Russian, Latvian and the Kotlin lookup, no duplicate source keys and no indexed format-placeholder discrepancies. This checks source structure **only**; it does not certify compilation, every conditional/dynamic UI path, wording quality, RTL layout, spoken-language quality, or on-device behavior.

### Remaining acceptance and regression gate

1. On a development host, run \`python scripts/generate_ui_languages.py\` and ensure the generated files match the committed files without unrelated diffs.
2. Run \`python scripts/audit_ui_strings.py --limit 25\`. Classify each hit before editing: app-owned UI should be localized; runtime diagnostics can remain English where intentionally scoped; user/source/model/tool text must never be rewritten.
3. Run \`./gradlew :app:assembleDebug\` and existing language/Compose tests locally (no GitHub Actions required).
4. On-device: switch English → Русский → Latviešu mid-navigation and with unsent input; verify memory tabs/counts, note editor, canvas, device controls, provider screens, notification privacy, speech fallback and accessibility labels. Confirm no runtime restart, no lost drafts and no model/provider change.
5. Check longer RU/LV labels at font scale 1.3–1.5 and with compact-width screens. Review English technical diagnostics separately.

The edits in this continuation were pushed directly to \`main\` through the repository interface. Gradle/device validation **has not been run for these new commits**. Do not mark localization release-complete until that gate passes.
