# Interface 4.0 delivery — issue 62, 2026-10-25

Status: **IN PROGRESS / KEEP_OPEN**. This ledger separates the new source and host-side checks from physical acceptance. The phone phase resumed, then the owner deferred further human speech after an unsuccessful attempt and requested continued UI work. Autonomous UI checks continue; host, physical UI and spoken acceptance remain separate.

## Source and ownership

The implementation started from synchronized `main` at `d5fe5943c8c3e689c49b8f7513823f6e50054556`. Publication and final binary hashes are recorded after the integrated gate.

- Four fixed roots: Home, Workspace, Devices, Settings. Bell retains Activities and Notifications, with live pending review identities rather than historical review links. Bell subview switching replaces its current entry and retains the previous working-root highlight.
- One shell Orb, a manually rotated contextual tool carousel and a full accessible list. The root controls remain stationary. Compact/large-font layouts use the full-list alternative. Local/Power continues to select the existing PRIVATE/CLOUD profiles and existing disclosure/gating owners.
- Screen utility/composer content moves into the lower zone through a navigation-entry lease. Its original composition locals, callbacks, lifecycle and view-model scope are preserved. Screen disposal removes the projection; a stale or outgoing screen cannot replace the current one.
- Home follows new activity until the user scrolls into history. Library is Workspace's entry, with Office and Studio as non-destructive suite views. Brain, Voice & Reading and Vision are Settings peers; Advanced remains last and gated.
- Nested editors, Settings children and ordinary detail screens project existing navigation/actions. Intrinsic editor/form controls, secure OS dialogs, credentials and physical emergency controls retain their original owners and appropriate placement.
- `halo_current_screen` is a bounded, read-only semantic descriptor, not a UI callback executor. It exports machine identifiers, known canonical revision/selection and allowed action/tool IDs. Selections exceeding 100 IDs explicitly report full count/truncation; changes in the private tail still invalidate the snapshot. It does not export titles, editor text, arbitrary Android notification keys or fabricated revisions. Foreground, stale-snapshot and Kids boundaries apply. Remote metadata requires the existing acknowledged Power profile plus both Screen and Workspace disclosure, checked by the host and runtime; this tool grants neither.
- The actual Library VM now supports scoped query/filter/sort/selection operations. Favorites and collection changes use conditional transactions in the existing Room organization owner. Saved-chat tools use the existing conversation store, exact content versions, local-only access and explicit approval; they cannot trash the active writer or silently continue a session. Human Continue now uses Home's cancellation owner. Mixed lifecycle/Undo/purge retain their original per-object owners and are not claimed as new typed batch support.
- MCP adapters expose only bounded non-secret settings and already-connected refresh through the original service/manager. Local-role adapters use the real router and eligibility checks; an explicit local role can coexist with an unchanged remote conversation default. Inheritance requires a safe local default. No new credentials, provider authority, remote role selection, model downloads or generation are introduced. Post-effect failures/access revocation report UNKNOWN rather than false rejection or success.
- New Sage/Rose accents, Paper/Slate tones and Still/Halo/Mist backgrounds extend saved theme profiles. Preview, cancel/apply, defaults and separate light/dark preferences remain owner-backed. Original procedural Open Ring background fragments are generated in Compose; no external stock assets or new download dependency is required. Decorative motion is throttled and stops under lifecycle, screen, power, thermal and accessibility restrictions. Actual device efficiency remains unqualified.

## Review records

- [Home, nested tools and current-screen ownership](ISSUE-62-HOME-TOOLS.md)
- [Workspace/Settings route inventory and hierarchy](ISSUE-62-WORKSPACE-SETTINGS.md)
- [Other existing nested screens and external identifier boundaries](ISSUE-62-REMAINING-SCREENS.md)
- [Visual system, backgrounds and motion review](ISSUE-62-VISUAL-REVIEW.md)
- [Current brand contract](../15-brand/v4/INTERFACE_4_0.md)

Initial inventories in these reviews describe the pre-migration source; later sections record implemented corrections. A page Back action or a descriptor is not evidence that every field/edit/export operation works through Halo.

## Host checkpoints

Local raw evidence directory: `C:\Users\slavd\HaloEco\verification-2026-10-25-issue62`.

| Checkpoint | Result | Scope |
| --- | --- | --- |
| 01 | FAIL | Initial integrated compile caught an existing color-name collision, a missing library-actions brace, ambiguous Compose scope references and an accidental duplicate navigation block. These were corrected together. |
| 02 | 38 PASS / 1 FAIL | Production and test compilation passed. Focused suites covered owner snapshots, settings persistence, Home following, library order, utility composition scope and shell layout. Settings navigation test needed to scroll to the newly grouped Help row before finding it; corrected. |
| 03 | FAIL: 2,075 PASS / 11 FAIL | 2,086 app tests. One orphan tool registration, one Canvas utility-host fixture, three Appearance fixtures still expecting a dialog, and six Study fixtures missing the relocated utility title. All other app cases passed. Both APKs built. Interpretation: 30 PASS; unchanged cognition 388 and language 55 results reused by Gradle. Raw failures retained. |
| 04 | FAIL: app 162 PASS / 2 FAIL; cognition 389 PASS / 2 FAIL | Both APKs built but were not installed. Missing bundled skill manifests; Library fixture read its second lease before the Room favorite projection settled; old runtime assertions assumed 52 tools and Workspace-only provenance. Raw evidence preserved. |
| 05 | App 174 PASS / 1 FAIL; cognition 391 PASS | 175 affected app tests. Only failure was the Appearance selector matching two identical labels; both APKs built and were installed. |
| 06 | CLI ERROR / NO TESTS RUN | PowerShell split an unquoted dotted Gradle property. Retained as infrastructure failure, not a test result. |
| 07 | 3 PASS | Appearance selector scoped to radio semantics; all original assertions retained. Test-only change; production identical to installed05. |
| 08 | 2,134 PASS / 1 FAIL | Full app suite: 2,135 cases. AssistantTools manifest fixture omitted its installed current-screen owner; exact assertions retained in repair. Both APKs built, not installed. Source `0c7924378f80ea9226b2fc0ef1c36be39df406b11e1d827f8fb34c3f00f54f05` (162 changed source files). |
| 09 | 312 PASS / 3 FAIL | 315 affected tests. All failures are non-Russian Material sheet locale assertions: outer context wrapper did not affect Material dialog context. Both APKs built. Source `d0227355b68b51f211799ac4ff40ec87cfc262d5ce873c4d7e01411e080ec768` (169 changed source files). |
| 10 | 92 PASS | All affected UI cases passed, including four Material sheet locales, original Activity registry, 200% density inheritance and short viewport reachability. Both APKs built, not installed. Source `f083933cb464ce8d5b4465ed08686978dd074c736095e740163eff6a9a4241aa` (177 changed source files). |
| 11 | 2,146 PASS / 1 FAIL | 2,147 full app cases. SavedChatTools fixture closed its Room database before eager collectors stopped; SQLite pool-closed exception, not a version assertion. Both APKs built. Source `798a5a44895119897a060a146a477f3d66f0c77ca041ac70bc55ec8e20698ce5`. |
| 12 | 2,150 PASS | Full app regression, zero failures/errors/skips; both APKs built. Grouped recovery preserves exact saved-chat assertions and adds persistent search naming, page-only main action lists and selective duplicate-tool removal with Kids Study retained. Source `4c129aa67f871cc57707cf4f040d3025d326a6f355e38cbf6a1f2de09db4e363` (178 changed source files). |
| 13 | BUILD PASS | Both APKs rebuilt after one setup scroll-state key change discovered in emulator setup. No tests rerun after this small UI delta; owner requested code now and phone tests later. Source `0d5a1c2464d1aed424562b446ace35b5c0543515fc30fa11ff8e296f13a29d4b` (179 changed source files). |
| Localization catalogue | PASS | Updated generator consistency: 4,168 EN/RU/LV entries and 4,168 PL entries; Polish Android resource check covers 5,015 strings. |

Checkpoint 02 did **not** run `CurrentScreenToolsTest`: its requested package was wrong. The full app suite includes its actual `com.halo.app.context` package. Checkpoint 02 also did not record new fixture images; it is not screenshot evidence.

Checkpoint 03's recorded shell fixture was inspected: the carousel uses synthetic actions and its profile slot was a text-button stub. Checkpoint 04 uses the actual compact Local/Power control, aligns the fixed root rows around its height, and records the large-font alternative. These are host fixtures, not an installed app screenshot or physical hit-test proof.

## Acceptance still requiring actual evidence

**PARTIAL** on installed05: root navigation, Language/Appearance, Bell subviews and Office suite; English 360dp at 200% font. **NOT_RUN / incomplete** on final repaired binary: native suite, remaining root/nested journeys, complete 360/412dp and 200% locale matrix; TalkBack and keyboard; IME/gesture insets; light/dark/high-contrast and persisted theme preview; current selection/revision through rotation/restoration; Kids/parent-gate flows; physical carousel/full-list hit testing; active capture/Mini continuity; sustained motion/resource measurements.

**NOT_ESTABLISHED**: positive human speech/STT/TTS, delayed Canvas approval and exact durable effect, complete Note → Canvas → Study and Studio save/export/reopen journeys, all-action text/STT operation evidence. Existing historical unit/device evidence retains only its originally documented scope.

Phone deferral does not block independent coding or publication. It also does not waive the issue's physical acceptance or authorize closure. Issue 62 remains open until remaining acceptance is demonstrated or a specific exception is explicitly accepted by the owner.

## Phone testing resumed

The owner connected the phone and authorized the physical phase. ADB identifies model `23124RA7EO`, Android 15, physical display 1080×2400, density 440, system font scale 1.0. The installed **before** APK hashes to `d50b39ae055a3978162bbac88dc46db0bc06a722cb3be5c4d2c444333b8666aa`; it is the earlier checkpoint25 binary, not this redesign. Actual before captures now cover Home, Workspace, Devices, Settings, Activity, Studio and Study. Replacement installation of05 succeeded and was verified against on-device APK SHA256; current-binary native tests remain pending. Original rotation/display settings are saved locally for restoration; portrait rotation is temporarily fixed during comparisons. The owner initially offered actual speech help; the later unsuccessful attempt and deferral are recorded below.

The fresh04 host visual review found clipped Russian mode labels at 200%; the repaired mode picker uses full-width options at large font/narrow widths, with retained callbacks and new text-layout/touch-target assertions. The correction passed07 and the actual phone Appearance page at English360dp/200% showed fully visible options; Russian physical coverage remains part of the repaired-binary gate.


## Physical checkpoint05 and UI repairs

Installed main APK SHA256: `c3dc50c3cc3db13aa6cbdf9a63fd4ddfbbe9ad5dc5ea81f4c40fde9155a0d011`; test APK: `9e88cd1df3382cb4505f03fe522db9f2ddb99afb925f372ba64e5394b5e53667`. Replacement install preserved existing data and grants. Production source05 stamp: `758a4cb0ebf1c2f04172a35411e156bcc0a6f0af7ac3e0fdc4878d17380cd294`.

- Actual fixed-root navigation worked across Home, Workspace, Devices and Settings. At 360dp/200% the four roots, Orb and bell had identical bounds across all four captures. English text and controls were reviewed without clipping/overlap. Settings scrolling reached Appearance, Language, Privacy and Connections.
- Bell Activities to Notifications and back retained the selected Completed filter. Closing the bell returned to the working Settings page. Office Tools changed the contextual carousel while the Library body remained visible.
- Physical defects reproduced: notification mojibake; duplicate normal-layout Back; stale global paused status on an empty Home conversation; contradictory stopped/completed activity wording; duplicate informational Polish row; Russian200% footer expansion caused by the legacy long Power label. Source08 repairs these together; no repaired-binary acceptance is claimed yet.
- Language selections changed live to Russian, Latvian and Polish, then English. Russian footer failure was retained in `phone-language-ru-360-200`; the compact footer fix bounds its profile slot and hint, uses dedicated compact labels, and preserves full accessibility descriptions. Other locale captures are diagnostic evidence, not a completed locale matrix.
- Human speech attempt **FAIL / NOT_ESTABLISHED**: owner reported a hang and no requested navigation. The phone displayed a local speech resource-busy rejection; Activity showed a partial recognition fragment, not a verified requested phrase. No successful route or audible reply was established. Owner then went to sleep and requested UI focus. No further voice/audio tests or paid provider calls are authorized by that deferral. Power was restored after the temporary Local attempt.
- Five passive resource samples overlapped the human speech attempt; they are explicitly **NOT_IDLE** and cannot support an idle-efficiency claim.

Evidence is under the local raw directory above (`phone-checkpoint-05-install.json`, `phone-*-360-200`, `checkpoint-08-integrated-stages.json`). Display/font/rotation overrides remain temporary while physical coverage continues and must be restored before leaving the phone. Original app appearance is Light/Mint/Cool/Halo forms; original UI language English and mode Power.


## Further physical UI checks on05

- Theme Cancel restored exactly Light/Mint/Cool/Halo forms after editing Sage/Paper/Mist. Apply saved Sage/Paper/Mist and leaving/reopening Appearance retained those checks. Dark mode rendered correctly with its independent existing Blue/Cool/Halo forms profile; Light restored afterward. A process restart is still needed to qualify cold persistence. Test theme remains temporarily applied pending restoration.
- Home hardware-key burst defect reproduced: requested `QA62-display-draft` became `A2dslydatQ`; cleared own draft then `abcde` became `cea`, and separate key injections became `cdeb`. Same `abcde` command reached Library search intact; Note rename/body also arrived exactly.09 adds synchronous ephemeral IME echo with canonical revision acknowledgements, external-change handling and unchanged exact-revision submission admission. This is not yet physical proof of the repair.
- Normal-font landscape with keyboard retained composer/navigation. At200%, Gboard's font-change notice reduced the viewport severely, clipping controls and drawing under status.09 makes exceptionally short footer space scrollable with original hit sizes; simultaneous visibility is physically impossible at that height. Reachability remains to be verified on the repaired APK.
- Library landscape controls and rows share one scrollable list; an observed scroll exposed actual rows. Initial helper aspect-ratio filtering was a QA-helper limitation, not an app defect.
- Created only `QA62-interface-note`, renamed it, entered exact `QA62-note-body-check`, left and reopened it; title/body and saved status remained. No existing note was edited. Singular word-count copy (`1 words`) has a staged four-locale plural correction awaiting the final polish batch.
- The central full action sheet opened and Office/Studio suite actions changed context without launching speech. Its Material-owned dismissal/drag descriptions inherited system Russian despite English app content;09 includes a narrowly scoped resource wrapper with Activity-owner preservation tests. Other Material surfaces retain their original behavior until separately verified.


## Disconnection and remaining restoration

ADB reported no device when09 replacement installation was attempted; `adb devices -l` confirmed an empty list.09 was **NOT_INSTALLED**, and no new native group has run. The last confirmed installed APK remains05. No automatic reconnection polling or audio prompts follow the disconnection.

Font scale1.0 and density reset were completed before disconnection. Rotation remains temporarily locked to portrait; the Light theme remains Sage/Paper/Mist. Restore original automatic rotation and Light/Mint/Cool/Halo forms when the phone reconnects. English and Power were already restored. A guarded local `restore-phone-display.ps1` is prepared but has not executed; appearance restoration must use its existing UI owner. Existing data is preserved. Only the QA62 note and a new Canvas linking that note were created during these manual journeys; the Canvas still has its default title and has not been exported or used in Study.

[Initial physical Settings before/after pair](issue62/2026-10-25/README.md) is checked into the repository with byte hashes and installed05 provenance. It is not relabeled as final-source evidence.


The owner confirmed the phone is **unavailable for now** after the reconnection request. Continue independent coding/publication; do not poll or request voice help. Current-binary native/responsive/theme/lifecycle/performance checks and original phone-settings restoration remain pending. The source/host gate cannot close those items.


Static physical XML geometry was additionally checked using actual clickable/selected ancestors, rather than icon glyph bounds: the four fixed roots and five carousel targets have zero positive-area overlap in Settings and Note captures. The fifth item and right rail share an edge; the carousel scroll region has a22px gap from the rails. [Recorded bounds](issue62/2026-10-25/initial-physical-hit-bounds.json) are installed05 geometry only, not an executed satellite selection or final-binary acceptance claim.


## Coding delivery, 2026-10-26

[Machine-readable gate and artifact manifest](issue62/2026-10-25/delivery-gate.json) records the full12 regression and the sole13 setup-scroll delta separately. Delivery main APK SHA256: `2c6e0fa27e4ddc3b7dac8f977e466ed9500a9def4c66f314d1675a23a3f1647d` (291,844,079bytes); test APK: `9e88cd1df3382cb4505f03fe522db9f2ddb99afb925f372ba64e5394b5e53667` (1,949,934bytes). All179 changed source byte hashes matched the13 source snapshot after compilation.

A temporary read-only/headless Android17/API37 emulator replacement-installed12 and both installed bases matched SHA256. Its actual optional setup flow reached9/9 with capabilities, provider key, voice downloads and accessory setup skipped. It correctly showed availability limitations. Moving from scrolled provider setup to voice setup exposed inherited mid-scroll;13 keys only scroll state by stage, preserving form/draft composition. The repair compiled but was not rechecked. No redesigned main-root journey, current native group, voice or provider operation is claimed from this emulator work.

The owner then requested **code for now; phone tests later**. Further test work stopped and the temporary emulator was closed. The physical phone remains unavailable; no final APK installation, restoration or acceptance is inferred. [Remaining device phase](issue62/2026-10-25/PENDING-DEVICE.md) is queued. Issue62 remains open, and the subsequent issue batch is authorized after62 is finished.

## 2026-10-26 continuation: remaining implementation completed

The historical checkpoints above retain their original scope. The [2026-10-26 completion](ISSUE-62-IMPLEMENTATION-COMPLETION-2026-10-26.md) finishes remaining actual source gaps against current main, with coding/source review before verification, complete2194 app and391 cognition passes, both APK assembly checks, localized copy and fresh host renders. The [new gate](issue62/2026-10-26/completion-gate.json) records exact provenance and every failure/recovery. No final device installation or physical/voice/provider proof is inferred; #62 remains open for applicable acceptance, with code implementation completed.
