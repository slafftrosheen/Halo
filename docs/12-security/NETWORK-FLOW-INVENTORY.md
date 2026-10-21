# HALO-ADR-0001 — Network flow inventory and privacy release gate

**Reviewed:** 2026-10-21; source-focused review at `8f9dcaa21bf4`. **Confidence:** identified construction/wiring paths, **not exhaustive SDK traffic or device packet capture**. ADR: [ADR-0001](../04-architecture/ADR-0001-HALO-LOCAL-FIRST-COMMERCE.md).

## Network and data-flow inventory

| Source / boundary | Destination and data | Initiator / authority | Exposure, retention and open review |
|---|---|---|---|
| `ProviderGraph.kt`, `AndroidProviderFactory.kt`, `providers/litert` | Local device files / model inference; may download model artifacts during setup | Explicit model installation and local model choice | Local storage. Verify signed model source/checksum, network behavior at install and after reinstall |
| `providers/openrouter`, `providers/openai`, `providers/anthropic`, `providers/gemini` and Groq/Cerebras/XAI/OpenCode adapters | Configured vendor endpoints; user-approved prompts/context, possibly images and tool schemas depending on role | User-chosen key/provider/model and context policy | Vendor's data processing and retention, **not** Halo's. Verify exact URLs, direct transport and cloud consent by tracing execution |
| `providers/ollama`, `providers/remote-custom-openai-compatible`, OpenCode Companion | User-specified LAN, localhost, or remote API; selected request content and credentials if needed | User-entered endpoint and provider selection | User/third-party-operated; distinguish LAN from public endpoint even for custom transport; SSRF and cleartext review |
| `providers/elevenlabs-tts` / Android TTS | Text submitted to selected synthesizer; Android platform TTS implementation may be external | Explicit voice choice / selected OS engine | Third-party speech vendor/OS engine retention differs. Do not label every speech path local |
| Sherpa / Supertonic / Kokoro / LiteRT under `AndroidProviderFactory.kt` | Local model/audio processing after assets installed | Local voice/speech selection | Device-owned paths; verify runtime backend/model loading and downloads before claiming fully offline |
| `app/src/main/java/com/halo/app/mcp/McpSettingsService.kt`, `.../McpIntegrationManager.kt` | Configured MCP server, tool metadata and authorized tool arguments | User config plus per-tool permission / execution gate | Server owner controls storage/retention; approval must be separate from “connected” state |
| Android native actions / OS app integrations / wearable services | Android system apps, Bluetooth/Wi-Fi peripherals and chosen integrations | OS permissions and runtime authorizer | Some actions leave Halo via OS or device SDK; external-app and Google service behavior needs separate review |
| `app/src/main/java/com/halo/app/privacy/SharedAnswerHistory.kt` | **Local** list of remote model request metadata (30-day receipt in UI) | EventBus request/answer observation | **Not** a complete third-party, speech, MCP or network ledger; never imply “no entries” means zero egress |
| Optional Google Play Billing + commerce server (#42) | Purchase token / product / entitlement status, refunds and required security records only | Purchase/restore operations, no Halo functional data | **Not implemented by this ADR.** Legal retention and Play Data Safety declaration pending; never collect assistant data |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | Dependency catalog includes Firebase libraries; Auth/Firestore usage is commented out in app module | Dependency resolution | Not evidence of active Firebase service or no other transitive SDKs; inspect dependency tree/merged manifest when builds are permitted |

## User-facing wording in current app

- `ContextPrivacyScreen.kt` / `brand_privacy_v16.xml` (EN, RU, LV): local-first boundary, external-provider processing, possible commerce-only license verification.
- `SharedAnswerHistoryScreen.kt`: last 30 days of online **answer requests**, excluding other app actions/voice; local record can be cleared but remote copies cannot be deleted from Halo.
- `FIRST-RUN-SETUP.md` and provider onboarding: remote service terms/prices must be shown before user enters a key. Never promise automatic zero-cost inference or fully private cloud processing.
- Polish UI copy is **not** added because no verified Polish app-wide resources are wired here. Add only when that locale is supported end-to-end.

## Mandatory new-feature review checklist

For every new provider, sync path, network permission, SDK, telemetry pipeline, billing change or feature sending content off-device, record the answers before merging:

- [ ] **Operator**: who owns the endpoint, DNS, runtime compute and storage (user, vendor, Halo commerce only)? Include hostname and source file.
- [ ] **Payload**: exact transmitted fields; personal/child data? prompt/media/files/credentials? Which context controls and user approval apply?
- [ ] **Retention and access**: which party can retain/access data, which deletion options exist, and what logs/export/backup are generated?
- [ ] **Offline**: documented behavior without network, fallback and timeouts. No invented completed actions or silent provider switching.
- [ ] **Security**: transport TLS/cleartext exceptions, certificates, keys at rest, least privilege, pin/replay/cancellation/rate limits and sensitive log redaction.
- [ ] **Trust isolation**: no app-owned functional data reaches a Halo-operated licensing endpoint, including via crash/telemetry/error reporting.
- [ ] **UX**: explicit choice and appropriate warnings for providers and Kids/Study; languages actually supported; accessible disclosure and permission explanation.
- [ ] **Play**: confirm actual SDKs and network destinations in release artifact, Data Safety fields, purchase disclosure, privacy policy, age rules and backend retention.
- [ ] **Verification**: evidence commit, paths, peer reviewer, manual device scenarios/packet trace, observed dates and known limitations. Do not treat source or a screen render as phone-tested.

## Open evidence / not waived by this ADR

1. Inspect **all** transitive/Google/Play SDK runtime traffic and merged manifests in an actual release build when tests/builds are authorized; source search alone cannot prove negative network behavior.
2. Capture offline and direct-cloud scenarios on a physical phone. Validate local-model use without connectivity and verify which third-party endpoints receive data with explicit cloud selection.
3. Record exact retention/deletion terms for vendor choices and commerce infrastructure before legal/Play policy publication.
4. If a Google Play unlock is selected, implement and verify purchase, pending, restore, refund/revocation and offline-grace behavior under **#42**.
5. Audit every future installed SDK (including scanning and speech components) for automatic initialization or telemetry beyond reviewed Halo-owned code.

The above remaining checks are **release/commerce gates**, not grounds to silently introduce a Halo functional backend. They must be carried forward to alpha and Play Data Safety review.
