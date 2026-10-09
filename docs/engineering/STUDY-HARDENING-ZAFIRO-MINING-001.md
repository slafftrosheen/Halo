# Study hardening, visual guidance and Zafiro mining

Source baseline: Halo `d76be967`; reference source reviewed, not executed:
[Enlighten bf50c73](https://github.com/mohammedfazilamer-hash/enlighten/tree/bf50c73ea7651cf0bcb3de1afe6644076b900863),
[Zafiro d50b2ab](https://github.com/niki914/zafiro/tree/d50b2abb8fbf062dee23d2675a5d16055798dd50).
Both reference repositories have MIT licenses. These are original adaptations within Halo's existing owners.

## Delivered adaptations

- Extracted text drafts persist atomically in private, backup-excluded storage. Text edits, order, provenance and saved-note identity survive a normal process restart. Restore never restarts extraction, AI or navigation. Busy saves cannot be replaced by another import; deleted saved notes are recreated rather than reused blindly. Ask/Study recheck current source-sharing policy.
- Study library reply counts derive from saved lesson replies, independently of graded attempts.
- MCP responses must match the request ID and contain exactly one result or error. SSE response selection ignores unrelated trailing notifications. Repeated cursors, duplicate tool names, oversized schemas and incomplete pagination fail closed. Failed refresh removes the live surface while retaining historical settings. Reconnection needs a fresh tool listing. Truncation is explicit; uncertain post-dispatch completion never triggers a retry.
- Skill imports and installed custom skills expose selectable instruction text for review. Package text is strict UTF-8. Reviewing text does not enable or execute a skill.
- History editing creates a draft for a new message, requires confirmation before replacing another draft, and never automatically sends or replays actions.
- Standard Material vector symbols replace the shared pixel renderer. The orb displays an icon at every state, across Home, Assistant and compatibility wrappers. Recognizing and planning preserve distinct symbols; preparing context cannot masquerade as approval. Existing runtime projections, progress, reduced motion and accessible state descriptions remain authoritative.
- Study uses labeled visual actions and a short exploration/attempt/practice guide. The learner's own thinking leads; hints and practice support discovery. This is not a child-safety certification or a claim that real children have accepted the interface.

## Orb symbols

| State | Symbol |
|---|---|
| Ready | Open circle |
| Listening | Microphone |
| Capturing | Camera |
| Recognizing | Microphone with Thinking state label |
| Thinking | Lightbulb |
| Planning | Play/action circle with Planning label |
| Acting | Tool |
| Speaking/responding | Speaker |
| Preparing | Eye with Preparing label |
| Approval/question waiting | Shield/question with current state label |
| Success | Check circle |
| Warning/recovering | Warning triangle |
| Error | Error circle |
| Offline | Cloud off |
| Stopped | Stop circle |

Icons are visual signposts, not the sole source of meaning. Visible labels and screen-reader state descriptions retain the exact runtime meaning.

## Enlighten completeness audit

| Reference feature | Halo disposition |
|---|---|
| Photo OCR, editable text, PDF ranges, DOCX/TXT, multiple source ordering | Implemented in previous delivery; persisted review hardened here |
| Reading controls, optional real word timing, sentence/version anchors | Implemented using the canonical speech owner; physical hearing remains pending |
| Listen then explain, compact grounded replies, cards and self-quiz | Implemented; card acceptance and learner attempts remain explicit |
| Model choice, phone/computer execution labels | Existing model fabric and explicit handoff; no automatic fallback or connections |
| Natural voice/Kokoro | Explicit native English pack import and cache; no automatic download, actual pack/device acceptance pending |
| Saved lessons and follow-up history | Existing durable Study/Workspace owners; reply projection corrected here |
| Theme, explanation level, languages, voice/speed | Existing settings plus Study learning setup; visual guidance improved here |
| Saved-set rename/update/delete | Existing session deletion and source editing; arbitrary display-title rename deferred because the current goal is semantic lesson input |
| Profile photo and prototype palette choices | Deferred cosmetic additions; standard accessible theme controls already exist |
| Unqualified OCR/language or locality claims | Rejected; coverage and actual execution locality are explicit |

## Zafiro feature disposition

| Reference feature / inspected owner | Halo decision |
|---|---|
| Provider/BYOK settings | Already owned by provider controls and model fabric |
| Conversation turn identities / ActiveTurnStore | Existing generation-bound readers and runtime turn identity; no second turn store |
| ConversationRepo fork/regenerate | Adapted as edit-as-new-message; destructive rewind and action replay rejected |
| MCP refresh scheduler and tools | Adapted fresh validation, failed-surface withdrawal and pagination/response checks; no automatic connections or retries |
| ToolOutputTruncator | Adapted bounded, explicitly truncated content; unbounded exported output files rejected |
| SkillPathResolver / SkillDetailContent | Existing bounded package importer; strict decoding and instruction review added |
| ToolExecutionPreflight | Existing typed permissions/approval remain authoritative; regex checks are not a sandbox |
| LocalToolResultClassifier / retry classification | Existing typed outcomes retained; unknown completion is never retried |
| Accessibility pruning, visible control and read-after-action | Already mined in ONEBOX-ZAFIRO-MINING.md; retain opaque snapshots, host ownership and dispatched-versus-confirmed truth |
| Durable memory and workspace context | Existing reviewed memory and Workspace policy; no automatic private-memory injection |
| Floating/notification surfaces | Existing assistant/continuity surfaces; no competing service or notification owner |
| Material theme/language | Existing Compose theme and settings; shared standard icons adapted here |
| Python, terminal, Termux, SSH and remote agents | Deferred advanced adapters pending explicit product requirement and execution isolation; not enabled by this patch |
| Root/LSPosed vendor takeover | Rejected as default architecture; no privileged fallback |
| Screenshots, view images, open URI and notifications | Existing host tools/capture/policy; no duplicate execution path |

MCP request validation follows the [JSON-RPC contract](https://modelcontextprotocol.io/specification/2025-06-18/basic) and [tool discovery contract](https://modelcontextprotocol.io/specification/2025-06-18/server/tools). External tool content remains untrusted.

## Acceptance boundary

Automated results are recorded after the final build. Physical acceptance must check orb states, large font and TalkBack, photo draft restart, Ask/Study policy, saved-reply counts and speech on the connected Sony headphones with the phone silent. No HeyCyan or Wear OS acceptance is claimed. Student usability requires observation of real learners; icons alone do not establish it.

## Final software evidence

Source commit `8ffb61232bda`, clean BuildConfig (`GIT_DIRTY=false`); identity also found in packaged DEX.
Serial `:app:testDebugUnitTest :app:assembleDebug` succeeded on 2026-10-09: 450 tests, zero failures, errors or skips.
Includes MCP response/pagination/refresh withdrawal, atomic draft retention, saved-reply restart/deletion, and orb meaning regressions.
APK: `Halo-8ffb6123-debug.apk`; SHA-256 `FCCF153E0C0E9B80704912B13A49A2590EA7383704F191EDB6F5F5F35DDFB51F`.
The Samsung SM-G990B2 (R5CTA0TKLZZ) reconnected after software validation. ADB installation returned Success; explicit MainActivity launch returned Status: ok. Physical hearing, visual-state and student usability acceptance remain pending. Historical installed-build evidence is preserved in the combined acceptance checklist.
