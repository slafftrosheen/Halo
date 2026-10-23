# Canvas ordinary editing parity — #54

Base main `5a65439e53f7f0ecdfa3d2c3a08fed8e5ef8c177`, freshly fetched0/0 and clean before source. The existing UI's ordinary saved edits now have eight additional registered typed operations (nine manual outcomes). No new editor, store, approval/execution owner or layout engine.

| Operation | Canonical owner method | Result / policy |
|---|---|---|
| halo_canvas_rename | rename | saved board ID/version/title; reversible user policy |
| halo_canvas_add_group | addGroupNode | new local group zone ID; reversible user policy |
| halo_canvas_tag | tagNodes | new tag card linked to the exact selection; reversible user policy |
| halo_canvas_duplicate | duplicateNode | new card ID, existing source reference/content; no source-byte copy/repair; reversible user policy |
| halo_canvas_arrange | manualEdit Arrange / Bring forward | exact selected IDs and saved board version; reversible user policy |
| halo_canvas_delete | manualEdit Clear | removed selected IDs and incident links; destructive ALWAYS approval |
| halo_canvas_ungroup | manualEdit Ungroup | removed GROUP zones/incident links; member cards retained; destructive ALWAYS approval |
| halo_canvas_disconnect | disconnect | removed exact edge ID; both cards retained; destructive ALWAYS approval |

## Arguments, results and safety

All schemas are closed, require the exact observed board version and bounded IDs. Selection arrays accept1–100 unique IDs, never implicit selection/all-cards or a guessed subset. Every selected card must exist on that board; ungroup rejects a mixed group/content selection atomically. Active board and revision checks share the Room transaction with canonical owner writes. Linked source notes/files remain intact. Text, title, enum and coordinate limits are checked before mutation. Missing/stale/foreign/trash sources return rejection; reads are used to resolve ambiguity, never refreshing an approval and replaying a conflicted write.

ToolOperability v1 uses workspace.write, the canonical owner, TEXT/VOICE_FINAL channel metadata, exact version argument and saved version/identity receipts. Large selection receipts have an explicit16000-character output budget, sufficient for100 legal100-character IDs without truncation. All eight writes are NEVER_REPLAY with the existing serial10s timeout/cancellation owner. Destructive descriptors remain ALWAYS-confirmed even with ALWAYS_ALLOW or older scoped grants. Ordinary allowed edits retain user policy. Existing UI live undo snapshots are not exposed as a second durable undo service; stale snapshots cannot overwrite later assistant mutations. No automatic deletion undo or replay is claimed.

Discovery instructions, one native Workspace skill, bundled per-tool risks and Workspace remote-result provenance include every operation. Approval labels contain no raw IDs; two new labels have EN/RU/LV/PL source. All prior4015 generated values per language and4032 lookup mappings remain unchanged. Generated catalogue4017 entries and total Polish coverage4798;15 localization Python regressions and generator freshness/format/coverage pass.

## Additional parity/lifetime corrections

The existing typed move previously moved only a group box, unlike the editor. Canvas drag membership is now one shared pure resolver used by the editor and repository: explicit `group` links take priority; otherwise non-group cards whose centres lie inside the zone move with it. Nested groups and outside cards stay put in the spatial fallback. The repository persists the entire move in one transaction; the typed operation still requires its exact observed board revision. Tests compare both real editor and assistant persistence for spatial and explicit membership and reject stale repeated moves. No new grouping or layout algorithm.

The Android installed-artifact inspection fixture previously opened an independent unpinned ModelInfo handle. It now calls the existing canonical LiteRtArtifactInspector with the model inventory pin and NativeResourceCoordinator lifetime/cleanup. It reports only task/text backends/vision/native-functions/embedding dimension exposed by that owner. Missing audio/context/minimum-runtime/backend detail is not fabricated from catalogue declarations; inspection is not engine, retrieval accuracy or role qualification. This change is test-only.

## Evidence boundary

The initial Canvas gate was explicitly cancelled (terminal exit1) to include the subsequently discovered group-move gap before app Kotlin compilation/tests/APK assembly. It is an interrupted attempt, not a code/test failure or a final pass; its log and source identities are retained. All extended production/regression sources are complete before the resumed grouped gate. Initial and follow-up read-only reviews found no actionable issue, including the group-move and inspection-helper deltas. New tests cover exact saved outcomes, source preservation, stale writes for all operations, malformed/foreign/duplicate/oversized and mixed selections, all-or-nothing failure, old UI undo rejection, maximum receipt budget, policy/contract and raw/default/merged manifest risk. The isolated native registered-owner fixture exercises all eight new commands and corrected group movement, and reviews all15 registered Canvas schemas; it does not invoke every older tool again. Real spoken recognition and permitted Power remain separate.

The resumed grouped gate completed production compilation and app packaging, **44 app cases / six classes plus six runtime cases / two classes** with zero failures/errors/skips. It then failed at phone-test Kotlin compilation because the extended fixture reused the local name `selected` for card IDs, conflicting with its WorkspaceContextStore. This is retained as a failed gate (**9m7s**), not an integrated success. The fixture-only variable rename to `selectedCards` preserved the original context owner and Study version lookup. Test-APK-only assembly then passed in **1m12s**, with unchanged production APK hash; no production source changed and the passed focused checks were not unnecessarily rerun. The six runtime checks reused unchanged-source results as UP-TO-DATE in the resumed gate.

[Compiled source phases, interrupted/failed/successful logs, XML results, APK identities and localization preservation](artifacts/HALO-CANVAS-EDIT-054-2026-10-24/) are retained. Installed/readback-matched on f01fe673 / 23124RA7EO / API35:

- production `37d9ffe5598051af3a6033f7c0955d32a30a4e6cf014b92313979aa1a797c146` (287548931 bytes)
- test `7de107e32b8cc261408ceeae8f82d72ede08a81ef8d8ccff0fd34cdd74a43361` (1963640 bytes)

The native Canvas/exact Study owner case passed in **1.244s**, zero skips, with all new commands, member offsets, saved-object readback, unselected-card preservation and stale removal rejection. Linked note/file preservation is covered by the desktop owner regressions. MainActivity resumed after launch. Isolated synthetic Room data only; no user Workspace/model-role/OS preference changes. The optional actual-spoken participation question remains separate from this automated result.

No completed-feature, physical pen, OS process-death, actual STT or provider-success claim. #54 remains OPEN until its required three real typed/spoken cross-tool workflows on-device and permitted Power are verified. Viewport/gesture/selection affordances remain UI view state rather than content-edit operations exposed by this contract; physical pen gestures and protected hardware retain their explicit exceptions. AI organization/provider review is a separate existing owner and is not silently invoked here. No CI/Actions.


## Physical embedding metadata supplement (#35)

With these same installed packages, the corrected canonical inspection helper passed one native case in **54.498s** on the same phone. Exact installed `embeddinggemma2-740m`, SHA256 `e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c`, 484622336 bytes; LiteRT-LM0.18.0. Run `c08adb0e65ff40b59684482e371fd8ea`, exact app/test SHA identities in sourceStamp. Host stamp includes base5a65439e plus dirty checkout and both exact APKs; final compiled-source identities are adjacent.

Native metadata reports task **EMBEDDING**, `isConversationGenerator:false`, dimension **768**, text backends **CPU,GPU**, declared vision **true**, native function calling **false**. Canonical native metadata inspection itself took789ms; the54.498s test includes graph/verification/startup overhead, and fresh inventory time is additional. These are not engine load/generation or retrieval latency measurements.

Before/after process PSS337363/371508KiB and native allocated heap20281632/20338640bytes are samples, not continuous peak/RSS/energy qualification. Successful owner return includes pinned native-handle teardown. No EmbeddingEngine/Conversation, downloads, role/model/backend preference changes, selection evidence or capability promotion. The catalogue currently enables TEXT; declared vision metadata does not enable it automatically or prove a working vision encoder. Audio/vision CPU backend detail and minimum runtime/context are not exposed by this inspection owner and remain unqualified.

[Exact filtered metadata run, inventory selection, runner and launch evidence](artifacts/HALO-CANVAS-EDIT-054-2026-10-24/embedding-metadata/) are retained. MainActivity resumed after inspection. #35 remains OPEN for native text/image/audio/video similarity, exact backend/encoder support, measured inference/memory/low-RAM, cancellation/model-switching and actual consumer/language qualification. This result supports the next explicit native embedding probe; it is not cross-modal acceptance.
