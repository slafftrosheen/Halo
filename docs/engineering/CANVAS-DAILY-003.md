# CANVAS-DAILY-003 — Capture, reviewed understanding, approved organization

Issue [#25](https://github.com/slafftrosheen/Halo/issues/25), delivery 2026-10-10, implemented against synchronized `main` at `29d960551b87c1882a9d9d3b93da9457fca044ff`. This replaces DAILY-002's speculative LLM AUDIO transcription path. It adds no Actions workflow, projects database, background microphone or automatic cloud upload.

## Delivered behavior and ownership

- **Stabilization:** strict UTF-8 markup decoding; original-text and file-version checks for annotations; normalized ink over the actual image/PDF aspect ratio; bounded previews with cancellation-safe bitmap ownership and closed page/descriptor/renderer handles. Sketch/markup payloads stay out of exported prose and AI context. Drawing recovery uses the existing Room draft table, including original text, page layers and source version. Explicit Cancel discards the draft; Resume drawing recovers it.
- **Editing:** serialized commands, race-safe final text/viewport flush, atomic group/multi-card position batches and an immutable final edit snapshot when the ViewModel clears. Group dragging uses explicit membership where present and spatial membership for legacy groups; selected members move once. Clearing cards and undo affect board nodes/edges, never original notes/files. Undo refuses to overwrite subsequent edits. Source markup is never flattened into the original file.
- **Capture:** Home's action menu, Workspace and Canvas Quick add open one Inbox capture dialog. Text, Android document imports, phone photo and explicit voice memo save existing Workspace objects. Add to current Canvas creates references to those same IDs. Photo-bearing Inbox notes get image-card sizing instead of a cramped text card. Android SEND/SEND_MULTIPLE includes text and distinct stream/ClipData URIs; up to 16 files per share and partial-success counts. Explicit share imports use the existing application scope across rotation and retain only a weak Activity reference for the optional chooser. Each imported file gets a bounded 64 MiB owned copy before board placement, preserving source provenance. Copy failure/cancellation removes partial bytes; a copy already committed on cancellation stays in Inbox. Failed imports retain the external source, and failed board placement retains the saved Inbox item.
- **Audio:** memo recording and playback acquire the existing runtime resource leases without preemption. Recording requests the phone's built-in microphone; playback requests its speaker. Permission and foreground checks precede recording. Recorder limits are two minutes/12 MB; incomplete recordings are discarded. Failed memo imports remain in a bounded 20-file staging area with explicit retry/discard. Capture cancellation releases only its own microphone/session resources.
- **Real transcription:** Android host `MediaExtractor`/`MediaCodec` decodes a readable audio track (including supported AAC/M4A), capped at 12 MB and 120 seconds. Streaming PCM16 partial reads are joined correctly, mono/stereo at 8–96 kHz are converted to 16 kHz mono, then passed as 20-second buffers to the existing selected reloadable offline STT provider. Source rate/channel/duration remain available in `DecodedMemo`. No LLM AUDIO capability, microphone acquisition, chat transcription or remote fallback is involved. Unsupported format, source loss, decoder timeout, absent model and cancellation are failures, never successful empty transcripts. The UI shows model/language/readiness and always offers Write transcript.
- **Reviewed understanding:** editable transcript/OCR review creates a normal Inbox note with source/card/board identity and timestamps, plus a connected reference card. Saving is transactional and idempotent; original drawings, recordings and documents remain unchanged. OCR uses the existing local ML Kit Latin recognizer on sketches and image/PDF page 1. Russian/Cyrillic and handwriting accuracy are explicitly not promised.
- **Search:** the existing Workspace search engine supplements its derived AppSearch index with fresh paged Room projections, using Unicode-normalized EN/RU/LV matching. It searches note content, memories/conversation turns, board prose, tags and reviewed transcripts/OCR. Board and source-type filters are part of the query; Canvas exposes All/Notes/Files. Results identify a board and card for tap-to-focus. Deleted/archived sources, raw encoded ink and credential-classified content are excluded. Full content is checked in 64-row pages, rather than only the first 12,000 characters; visible snippets stay bounded. Room projections reflect edits/removal/relink immediately and AppSearch contributes relevance only; every displayed snippet and card jump comes from current Room data.
- **Organizer:** multi-select/whole-board scope, manual group/ungroup/move/tag/link/arrange/layer/clear/undo work without AI. Typed proposals contain bounded source fingerprints/linked-object versions, headings, memberships, optional suggestions and linked-checklist choice. Preview shows counts, source mappings and impact; headings/members/groups/next steps are editable. AI is tool-disabled document reasoning through the existing provider router, with effective per-object Workspace permission checks and no remote fallback unless every included object and the board allow it. Completion produces an untrusted preview, never authority to mutate. Approval is the local act boundary: validate all sources, arrange members, replace their group memberships, persist links and optionally create an Inbox checklist in one Room transaction. Durable operation receipts make repeated approval a no-op across repository recreation. A later failure rolls back earlier writes; stale previews must be recreated. Suggested tasks remain unchecked text, with no cross-app actions.

## Existing wearable path and model requirements

`ExistingAccessoryActionOwners` and `HeyCyanCaptureCoordinator` remain authoritative. Configured photo controls use the connected exact device's confirmed AI-thumbnail capture and contextual review; Save persists through `LocalCaptureStore`. Explicit media synchronization imports actually received PHOTO items into Inbox using their stable identity. Merely pressing a button or listing device media is not proof of delivery. Disconnected/unsupported endpoints fail through existing receipts. No generic wearable microphone capture, automatic connection, full-resolution button guarantee or new driver transport is claimed.

STT uses the existing pinned `SherpaSpeechModelCatalog` and Voice Settings installation/selection. Multilingual Whisper Tiny/Base/Small catalog tags include `en`, `ru`, `lv`; Automatic prefers installed Base, then Tiny. Approximate ONNX/token downloads: Tiny 104 MB, Base 161 MB, Small 375 MB. They require explicit model installation and existing artifact verification; this batch downloads nothing. Whisper's upstream [MIT license](https://github.com/openai/whisper/blob/main/LICENSE) and sherpa-onnx's [Apache 2.0 license](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE) apply with the existing distribution notices. The experimental Omnilingual entry is not newly qualified here. Model compatibility tags are **not** measured Latvian/Russian accuracy. Twenty-second segmentation and simple linear resampling may affect recognition at segment boundaries; always review.

## Local validation

Local SDK/JDK are available. Commands use Android Studio JBR, one worker, no daemon/parallel, 2 GB heap:

```powershell
python scripts/generate_ui_languages.py
python scripts/audit_ui_strings.py --limit 0
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :providers:sherpa-stt:testDebugUnitTest :engines:speech:test :runtime:cognition:test --no-parallel --max-workers=1 --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m'
```

The final serial gate passed in 4m 41s: 522 app, 19 Sherpa STT, 21 speech and 251 cognition tests (813 total), with zero failures/errors/skips, plus debug Kotlin compilation and APK assembly. App tests reran on the final source; unchanged provider/speech/cognition tasks reused the earlier successful gate. Tests cover malformed/legacy serialization, actual rollback after a later failed SQL mutation, stale/repeated proposals, source preservation/undo, reviewed provenance and multilingual search, partial/empty/oversized/revoked reads, duplicate shares and cancellation, PCM channel/rate/chunk/duration boundaries, provider absence, immediate leave flush, group-plus-member movement, and microphone ownership. A failed first run exposed permissive UTF-8 decoding; that production bug was fixed. A subsequent filesystem test exposed AndroidX FileProvider's Unix '/' root check on Windows JVM paths (confirmed in the pinned library bytecode). File copy tests inject a fixture URI adapter; production still uses FileProvider. This does not qualify Android URI grants.

The canonical catalogue now has 1,391 entries per language (88 new English keys, with RU/LV parity). Regeneration is deterministic. The broader UI string audit still reports pre-existing unrelated labels; touched Canvas/capture labels use the canonical catalogue. EN/RU/LV generated resources must be regenerated with the script, never edited independently.

## Physical acceptance still required

ADB reports **no connected device**. No Android instrumentation, native AAC reference corpus, installed-model recognition, camera permission interaction or HeyCyan delivery is verified by JVM tests. Do not close the physical acceptance items based on an assembled APK.

1. On a phone, switch to airplane mode and disable/uninstall selected AI/STT models. Capture note/file/photo/memo from Home/Workspace/Canvas; deny/regrant camera/mic permissions; interrupt/background/rotate at each stage. Check truthful receipts, no stolen Halo microphone, source recovery, no duplicate bytes and Inbox identity across boards/restart.
2. Share image/PDF/DOCX/M4A and a mixed multiple share; revoke one source grant during copying. Confirm partial counts, nonempty local files, no `.copying` artifacts and restart/open/relink behavior. Try 64 MiB+ documents and 12 MB+/120 s+ clips.
3. Install a catalog multilingual model explicitly. Record three consenting 10–20 second reference sentences in EN/RU/LV, record selected model/hash/language, latency and exact recognized/reference text. Test stereo 44.1/48 kHz, missing/corrupt codec, cancel during decode/STT and airplane mode. Save only after editing; search a phrase from each reviewed note and jump to its linked board card. Actual RU/LV accuracy remains unqualified until this corpus passes.
4. Annotate portrait/landscape images and PDF pages 1/2; switch pages, rotate, kill/reopen, Resume drawing, Save/Cancel and verify source bytes/hash unchanged. Edit sketches, OCR Latin/Latvian, test Cyrillic/unreadable handwriting, pan/pinch and long-press multi-group drag. Check no doubled member movement.
5. Multi-select → manual preview → rename/move/remove groups → approve/cancel/undo, then restart. Edit/delete a linked source while preview is open and confirm refusal. Force failed approval then retry. Verify linked checklist/source mapping, no source changes or external tasks. Repeat with missing AI and mixed LOCAL_ONLY/EXCLUDE/cloud settings; excluded data must never leave the policy boundary.
6. Connect exact HeyCyan hardware, configure supported photo control, capture/review/Save and explicitly sync photos. Record transport/receipt and Inbox identity. Disconnect during transfer and retry. Confirm no new wearable mic support is implied and local playback stays on the phone. Validate EN/RU/LV large-font labels/tap targets on device.

## Delivery evidence

Final gate: **BUILD SUCCESSFUL**, 813 tests, zero failures/errors/skips; `app/build/outputs/apk/debug/app-debug.apk` assembled. Local log: `%TEMP%/halo-canvas-delivery-gate.log`. `git diff --check` passed. No GitHub Actions were modified or manually invoked; the delivery commit uses `[skip ci]`. The implementation file manifest (IDE files are excluded):

```text
CURRENT-STATE.md
app/src/main/java/com/halo/app/MainActivity.kt
app/src/main/java/com/halo/app/capture/LocalMemoTranscriber.kt
app/src/main/java/com/halo/app/capture/MemoPcmDecoder.kt
app/src/main/java/com/halo/app/data/WorkspaceContextStore.kt
app/src/main/java/com/halo/app/data/notebook/CanvasDrawingDraft.kt
app/src/main/java/com/halo/app/data/notebook/CanvasFileMarkupCodec.kt
app/src/main/java/com/halo/app/data/notebook/CanvasOrganization.kt
app/src/main/java/com/halo/app/data/notebook/NotebookCanvas.kt
app/src/main/java/com/halo/app/data/notebook/NotebookDao.kt
app/src/main/java/com/halo/app/data/search/AppSearchWorkspaceSearchEngine.kt
app/src/main/java/com/halo/app/data/search/WorkspaceLocalContentSearch.kt
app/src/main/java/com/halo/app/data/search/WorkspaceSearchEngine.kt
app/src/main/java/com/halo/app/data/workspace/SendIntentHandler.kt
app/src/main/java/com/halo/app/data/workspace/WorkspaceFileDao.kt
app/src/main/java/com/halo/app/data/workspace/WorkspaceFileRepository.kt
app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt
app/src/main/java/com/halo/app/language/UiCatalogue.kt
app/src/main/java/com/halo/app/notebook/WorkspaceUseCases.kt
app/src/main/java/com/halo/app/ui/screens/home/HomeScreen.kt
app/src/main/java/com/halo/app/ui/screens/notebook/CanvasEditorScreen.kt
app/src/main/java/com/halo/app/ui/screens/notebook/CanvasEditorViewModel.kt
app/src/main/java/com/halo/app/ui/screens/notebook/CanvasLocalMediaPreview.kt
app/src/main/java/com/halo/app/ui/screens/notebook/CanvasOrganizationDialog.kt
app/src/main/java/com/halo/app/ui/screens/notebook/CanvasSketchEditor.kt
app/src/main/java/com/halo/app/ui/screens/notebook/CanvasSketchOcr.kt
app/src/main/java/com/halo/app/ui/screens/notebook/CanvasSourceInkDialog.kt
app/src/main/java/com/halo/app/ui/screens/notebook/CanvasVoiceMemoRecorder.kt
app/src/main/java/com/halo/app/ui/screens/notebook/InboxCaptureDialog.kt
app/src/main/java/com/halo/app/ui/screens/notebook/NotebookScreen.kt
app/src/main/java/com/halo/app/ui/screens/notebook/NotebookViewModel.kt
app/src/main/java/com/halo/app/ui/shell/AppShell.kt
app/src/main/localization/ui.tsv
app/src/main/res/values-lv/ui_strings.xml
app/src/main/res/values-ru/ui_strings.xml
app/src/main/res/values/ui_strings.xml
app/src/test/java/com/halo/app/capture/LocalMemoTranscriberTest.kt
app/src/test/java/com/halo/app/capture/MemoPcmAdapterTest.kt
app/src/test/java/com/halo/app/data/notebook/CanvasDailyRepositoryTest.kt
app/src/test/java/com/halo/app/data/search/WorkspaceLocalContentSearchTest.kt
app/src/test/java/com/halo/app/data/search/WorkspaceSearchEngineTest.kt
app/src/test/java/com/halo/app/data/workspace/WorkspaceRepositoryTest.kt
app/src/test/java/com/halo/app/ui/screens/notebook/CanvasEditorPersistenceTest.kt
app/src/test/java/com/halo/app/ui/screens/notebook/CanvasMemoOwnershipTest.kt
docs/10-features/CANVAS-DAILY-FLOW.md
docs/engineering/CANVAS-DAILY-003.md
```
