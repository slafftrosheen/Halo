# Halo Zone 5 — batch 3 evidence

Implementation base: `af56749a590531b7786850832a62c1387c3aa9a9`. Branch: `codex/halo-zone5-batch3`. Scope: Home, the existing HaloScreen alias, Workspace Library/Tools, and their instrument entry/return paths. Full instrument/editor migrations remain roadmap batch 4. Original assets/specification are the supplied Zone 5 pack; the user's reactive theme requirement supersedes fixed-mint source prose.

## Comparison with the supplied documents

| Reference | Previous behavior | Batch 3 presentation |
|---|---|---|
| Approved Home / R003 / roadmap 3.1–2 | Attach/Tools and a surface picker remained in the hosted composer; New conversation/History followed recovery actions | Hosted composer contains its editor and Send/Stop only. Attach/New chat/History lead the page action set. Tools/mode/recovery remain in All actions. The same HomeViewModel, conversation draft and voice owners are retained. |
| Approved HaloScreen / E001 / roadmap 3.3 | Customize/Settings led the action list; Widgets/Appearance required another edit step | Widgets/Arrange/Appearance lead the list and open the existing edit draft. Preview and Apply/Cancel remain explicit. The existing stale-base store transaction and Back cancellation remain authoritative. |
| Approved Workspace Tools / R006 / E002 / roadmap 3.4–6 | Library stayed visible while an invisible Office/Studio suite changed its orbit | Library/Tools are explicit, saveable local tabs. Four grouped rows open Study/Memory/Studio/Office directly. Tools has no instrument satellites or Library lease. |
| R006 Library | Tool-launcher actions were promoted ahead of creating/importing saved work | New note/Import/Organize are the defaults. Visible selection uses existing IDs, guarded batch operations and Trash confirmation. Selection actions open independently of list scroll position. |
| Shared contracts / theme follow-up | Mixed legacy glyphs and fixed presentation assumptions | Native Zone 5 semantic tool/type/action assets, existing brand fonts and Material palette roles. Names/icons identify tools without depending on a fixed color. Long labels and rows may grow. |

## Canonical owners and protected boundaries

Home continues through `HomeViewModel`, its revisioned composer buffer, existing context/attachment admission, Room conversation store and CognitiveSession. The feed-follow owner already follows new content at the tail, releases on deliberate history browsing, restores conversation positions and follows after explicit submission/return; retain its regressions. Saved response cards use committed identities and receipts, never an animation as proof of saving.

New chat/continuing a different writer are protected host session transitions. `HomeViewModel.newConversation/openConversation` stop the owned input/request and switch through the existing conversation transaction. The existing `SavedChatTools` contract deliberately does not switch the writer during an executing tool: typed saved-chat list/search/get/open provide bounded, versioned review and a human Continue control. This batch does not invent a callable UI callback that cancels its own CognitiveSession. The Android picker/permission window remains a protected human host interaction; managed-source/context operations retain their existing canonical tools and source guards.

Workspace uses the existing `NotebookViewModel`, `NotebookLibrarySession`, organization/batch owners, `WorkspaceUseCases` and immutable object routes. Library-only typed tools remain `halo_library_view/set_view`, organization/organize and batch/apply_batch with existing snapshot, source, confirmation and NEVER_REPLAY constraints. The Library lease detaches while Tools or covered Library sheets are visible. Tools advertises navigation discovery rather than unavailable Library mutations. Each instrument's meaningful operations remain with its registered existing owner; the row itself is navigation, not proof of editing/exporting.

HaloScreen retains `LocalScreenStore`, its layout/version guard, existing layout proposal/preview and `ScreenToolExecutor` contracts (`halo_screen_state`, preview/apply/settings/undo/presets). UI visibility grants no source read, calendar permission, microphone or provider authority. Calendar permission/loading/failure, unavailable weather, saved-work loading/empty and privacy masking remain distinct. Supported Office text/CSV/image/PDF bounds are unchanged; no native PDF editing or DOCX/XLSX round-trip claim is added.

## Verification

Initial characterization: five new tests ran and failed for the expected duplicate hosted Attach, missing hosted Preview identity, missing Tools tab and old Library action order. The Workspace fixture uses an actual NavHost/foreground entry and isolated canonical Room owners, including the existing Library lease.

Final host disposition: **475/475 latest per-suite results PASS**, zero errors/skips. This combines the broad confirmation with the final whole-class Library rerun, rather than claiming a single green broad invocation. Production code and APK did not change between those runs.

The initial broad gate ran 473 checks: 469 passed and four old-contract fixtures still expected duplicate composer controls or invisible Workspace suites. Those assertions were migrated to the explicit tabs/direct tool rows while retaining draft width, exact submission, search order, immutable IDs, scrolling and stale-action refusal. The broad confirmation ran 475 checks: 473 passed; the two new Library render selectors matched both the tab and collection filter. Scoping those selectors to `Role.Tab` and matching the isolated host's background/content roles to AppShell produced the final **11/11 Library PASS** (`BUILD SUCCESSFUL`, exit 0). APK assembly passed in the broad gate and remained up to date in the final gate.

Fresh whole-branch review found two material issues: enabled actions announced disabled reasons, and local selection buttons stayed enabled after a cached Library query failed. Both were reproduced as RED regressions, fixed, and passed in the broad confirmation. No review finding is deferred. Visual inspection also led to full-width stacked tabs and privacy status at large text, plus the documented 14sp supporting tool copy.

See [latest results and exact source/APK hashes](artifacts/HALO-ZONE5-BATCH3-2026-10-30/final-summary.json), [broad confirmation](artifacts/HALO-ZONE5-BATCH3-2026-10-30/confirmation-checkpoint-summary.json), [final Library run](artifacts/HALO-ZONE5-BATCH3-2026-10-30/library-confirmation-summary.json), and the [reproducible host command](artifacts/HALO-ZONE5-BATCH3-2026-10-30/verify-host.ps1).

| Layer | Disposition |
|---|---|
| Supplied document/mockup comparison and owner/source review | Completed for this batch |
| Host UI/navigation, lifecycle, source guards and palette checks | PASS; latest per-suite results as described above |
| Registered-feature/ToolOperability audit and Android APK assembly | PASS |
| Final native phone install/journeys | NOT_RUN; user previously deferred device testing |
| Physical touch, TalkBack, actual spoken recognition, permitted providers | NOT_RUN |

| Workflow | Host/synthetic | Typed device | Spoken device | Power/provider | Durable readback |
|---|---|---|---|---|---|
| Home draft/attachment/new/history and live-follow | PASS; component/owner tests, protected transitions as above | NOT_RUN | NOT_RUN | NOT_RUN | Existing conversation/source owners |
| Library selection/organization → Tools/instrument → Back | PASS; canonical Library mutations and entry/callback/return fixtures | NOT_RUN | NOT_RUN | NOT_RUN | Isolated Room owner readback |
| Screen preview → Cancel or Apply → stale refusal | PASS; UI chrome, typed executor and store transactions | NOT_RUN | NOT_RUN | NOT_RUN | Existing LocalScreenStore tests |

Resource integrity: all manifest-listed **187 vectors + 26 motion files** parse and byte-match the supplied pack; EN/RU/LV have identical 30-key Zone 5 string sets. Changed Kotlin has no fixed RGB colors. [Integrity record](artifacts/HALO-ZONE5-BATCH3-2026-10-30/resource-integrity.json).

Built debug APK: `com.aistudio.braintool.xkqzps`, 291,398,860 bytes. `app/build/outputs/apk/debug/app-debug.apk` and `releases/halo-alpha-debug.apk` match SHA-256 `c1421b439e7fc65ae9c940fc235ccf21780e84d7c644d5df76c004a3623c7b93`.

## Inspected native-renderer captures

These are Robolectric native-renderer Compose captures, not phone screenshots. Home/Tools/Screen use production components and the shared zone with synthetic content. Library uses the real screen and isolated canonical Room projection, showing its working area with AppShell's backdrop/content roles. Normal and 200% text, EN/RU/LV, reduced/static orb, and non-default light/dark palettes were inspected. Large Library rows remain reachable by scrolling; bounded object titles may ellipsize and search/filter strips retain their existing horizontal scrolling.

| Surface | Captures |
|---|---|
| Home | [Dark Blue](artifacts/HALO-ZONE5-BATCH3-2026-10-30/home-dark-blue.png), [Light Rose](artifacts/HALO-ZONE5-BATCH3-2026-10-30/home-light-rose.png), [Russian 200%](artifacts/HALO-ZONE5-BATCH3-2026-10-30/home-ru-200.png) |
| Workspace Tools | [Dark Sage](artifacts/HALO-ZONE5-BATCH3-2026-10-30/tools-dark-sage.png), [Latvian 200%](artifacts/HALO-ZONE5-BATCH3-2026-10-30/tools-lv-200.png) |
| Workspace Library | [Dark Blue](artifacts/HALO-ZONE5-BATCH3-2026-10-30/library-dark-blue.png), [Latvian 200%](artifacts/HALO-ZONE5-BATCH3-2026-10-30/library-lv-200.png), [Scrolled items](artifacts/HALO-ZONE5-BATCH3-2026-10-30/library-lv-200-items.png) |
| HaloScreen | [Dark Blue](artifacts/HALO-ZONE5-BATCH3-2026-10-30/screen-dark-blue.png), [Light Rose Preview](artifacts/HALO-ZONE5-BATCH3-2026-10-30/screen-light-rose-preview.png), [Russian 200% Preview](artifacts/HALO-ZONE5-BATCH3-2026-10-30/screen-ru-200-preview.png) |

The phone's last observed temporary theme was DARK/ROSE/WARM/STILL. Restore DARK/MINT/WARM/STILL when device work resumes; light CORAL/COOL/HALO was left unchanged during that earlier check. No device polling or remote/provider request is part of this host-only batch.
