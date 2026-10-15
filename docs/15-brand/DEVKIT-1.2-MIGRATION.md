# Developer Kit v1.2 migration — Batch 01

Authority: `DevKit_v1.2/Implementation.md`, `DevKit_v1.2/tokens.json` and Brand Book v2.1.
Round 2 supersedes Round 1 wherever they disagree.

## Integrated
- Shared component radii: cards 20dp, large/sheet 26dp, form controls 12dp.
- Bottom navigation: v1.2 floating 64dp pill, 12dp outer margins, 32dp radius, accent selection fill, active dot + heavier label; existing navigation routes unchanged. Surface uses compositor-safe transparency rather than mandatory GPU background blur.
- Home app-bar wordmark: supplied dark/light outlined PNG resources; fixed brand colors, no accent tint. Ready-state Aura is gated by real provider, review, voice and session status.
- 58 new FX Lottie JSON files in `app/src/main/assets/halo/fx`, new FX moment wrapper and reduced-motion-aware Compose primitives in `ui/fx/HaloFx.kt`. FX is opt-in; callers must supply real runtime states.
- Six missing v1.2 Android vector icons. Existing 134 icons, standard/kids orb Lotties and fonts already match the DevKit blobs; those identical files are not repackaged.
- Source `DevKit_v1.2` stays in-repository as canonical. `BrandAssets` remains temporarily for historical tooling until migration removes its remaining consumers.

## Round 2 · Activity and answer surfaces (this batch)
- Activity uses Needs you / Running / Done tabs, with truthful pending and review counts; the active tab is chosen from current durable evidence until a user makes a selection.
- Per-run cards show status with icon, state label and relevant error/attention color. Apricot marks outstanding user action, not progress.
- Grouped timeline events no longer report success when only start/request evidence exists. Added regression tests for pending and failure evidence.
- Activity timestamps follow the selected Compose app locale rather than a fixed device-system HH:mm pattern; added test cases for 12h/24h locales.
- Reply cards use lowercase brand voice and no longer label old replies with the currently selected provider.
- The full static PDF comparison uses owner supplied Brand Book v2.1: page 37 Home, 40 Activity, 42 reusable UI parts; no emulator/pixel-sign-off claimed.

## Round 2 · Home handoff and shared button shape
- Home uses the supplied lowercase wordmark without a redundant section label, with an actual Updates route button.
- An outstanding approval/clarification is shown as Apricot "needs you" with a working Review in Activity action; merely opening Activity does not grant approval.
- Outdated Home provider/brand caption no longer competes with the primary state; setup attention remains explicit.
- Shared actions now use full-round buttons; STOP is neutral outlined (red is reserved for failures). Inputs keep their separate 12dp field geometry.
- Floating navigation uses the DevKit 82% translucent surface and 18% selected tint, keeping the no-blur low-power fallback.
- All new Home and Activity copy added to generated EN/RU/LV resources using reviewed ui.tsv.

## Round 2 · Intelligence screen
- Chosen inference location is sourced from the selected provider; three provider-location cards are shown only on explicit "Change location", not permanently above core actions. This never silently changes provider or opts into cloud.
- Providers and Model library are direct first-class Intelligence destinations. Vision lab, Audio lab and System check remain under Advanced.
- Model identifier is shown only on user-opened Details; basic card presents selected/unselected state and the actual provider's display name.
- New labels reviewed for EN/RU/LV, with a unit-level projection test.

## Round 2 · Devices and BLE pairing (HALO-DEVKIT-138)
- Device sections now distinguish ready-to-use sessions from transport/handshake/sync stages and saved/disconnected devices. Camera/robot controls are gated by actual SessionState.READY, never BLE CONNECTED alone.
- Pairing DONE requires the selected device identity, explicit attempted identity and successfully saved identity, plus the host's READY state. Completion also checks the driver active device ID matches the selected target and that the device-level transport reports READY. A previous target's Ready signal cannot complete a newly selected pairing.
- Source of pairing-stage truth remains PairingViewModel, not an animation. FX uses device_connect connecting/connected markers and only shows connected after a real save; scan stops on screen background.
- Discovery hides even masked device identifiers until the user explicitly requests Technical names. No passcode is invented.
- Copy and states are localized EN/RU/LV; tests cover stale READY, stages, transport readiness and command gate.
- The reference is Brand Book v2.1 page 40 and DevKit v1.2 round2/10 plus the pairing detail notes from Implementation.md.

## Round 2 · Settings, privacy and System Check (HALO-DEVKIT-140)
- System Check is a direct Settings destination, separate from the Guided Setup wizard; no check or probe is triggered by opening Settings or this link.
- A System Check summary counts actual READY/PASSED evidence in current diagnostic sections. Its wording deliberately distinguishes availability from proof that a probe ran. Zero observations are reported as unknown, not as success.
- Diagnostic labs, operator panels and technical actions are behind a disclosure; Refresh and Copy Summary remain discoverable.
- Appearance mode selection uses full-round controls; the Settings identity reads lowercase halo. Privacy copy confirms that sharing controls never silently opt into cloud.
- New text goes through the EN/RU/LV localization catalogue. Tests verify the direct navigation route and evidence-based tally.

## Round 2 · Notebook / Canvas (HALO-DEVKIT-141/142)
- Notebook hub: 2×2, touch-safe entry surfaces for New note, Canvas, Import and Inbox. Actions remain wired to their original ViewModel handlers. Library filtering, long-press selection, collections, chat context, Study and Memory routes remain untouched.
- Canvas: direct Fit all cards control next to the truthful card/edge/zoom count, scrollable full-round dock controls, and a 48dp AI entry in the shared editor header.
- Board commands disable during loading, import, node creation, image processing, AI generation and export, preventing overlapping user edits while retaining the live board and drafts.
- Legacy uppercase assistant labels in Canvas tools and AI status replaced with reviewed EN/RU/LV lowercase halo labels. No action completion or media receipt is inferred from animation.
- Existing Canvas dock actions are retained (text, sketch, link, group, import, selection, AI); file and AI flows remain owned by the ViewModel.

## Round 2 · Study learning path (HALO-DEVKIT-143)
- The learning goal → optional material → support style → options flow remains intact; the learner's age, support style and source-policy choices are Saveable across activity recreation.
- Lessons without selected material explicitly disclose general-knowledge use. The start handler uses the same source-policy projection shown in UI; selected material retains the user's chosen source policy.
- Learning path says Complete only after actual completion; it does not present finished lessons as still pending review. A focused review remains review, preserving the user's active mode.
- Current-step marker uses the primary accent and rounded geometry; direct new-lesson action meets 48dp touch target. No Apricot is used for current progress.
- Brand text for Study and learning-stage guidance uses the reviewed lowercase halo EN/RU/LV catalogue. Regression tests cover source policy and completed path labels.

## Round 2 · Note editor and active Study lesson (HALO-DEVKIT-144)
- Shared Note editor halo sheet uses lowercase halo names, 56dp-safe action rows and a neutral high-contrast destructive affordance. Real confirmation remains required before moving a note to Trash.
- Active Study session continues to display actual learner/source conditions; partial source coverage uses neutral caution rather than an erroneous red failure status.
- Text for the Note editor's AI actions and Study source-coverage notice is updated in the EN/RU/LV catalogue; existing save, apply, discard, study and read-aloud handlers are unchanged.
- This is source-based migration only: no claims of compiled build, device visual sign-off or exact PDF screenshot parity.

## Round 2 · Canvas operation interlocks (HALO-DEVKIT-145/147)
- One readiness projection now gates toolbar actions, the shared header's AI entry, card actions, empty-canvas creation, organization, exports and source imports. Asynchronous ViewModel entry points recheck the same policy before starting; stale card menus close when an operation takes the board.
- The dock presents the actual THINKING state while AI is generating. Its orb obeys the application's reduced-motion setting rather than unconditionally suppressing motion.
- A unit test covers every blocking state and return-to-ready; the shared header has a disabled-action Compose regression test.

## Round 2 · Study copy and source labels (HALO-DEVKIT-146)
- Active-lesson mastery percentages, due/mastered review counts, local-attempt/help counts, the default subtitle and citation open-source action are localized via reviewed EN/RU/LV resources.
- Generated UiCatalogue and Android resource files were updated together; resource-format tests cover integer placeholders, percent escaping and locale fallbacks.
- Existing turn, practice, provider, voice and source-identity persistence code is unchanged.

## Round 2 · Notebook, Canvas, Study visual hierarchy (HALO-DEVKIT-148–150)
- Canvas action overflow is now one vertically scrolling sheet from the header, with halo actions first, view/export next, and secondary image/board tools afterward; every existing command remains wired to its actual handler.
- Study material setup is four touch-safe paths, reusing on-device text extraction, Notebook selection, file import and learner preferences. Four segmented progress steps replace the crowded chip/linear progress stack without bypassing the previous setup gate.
- Notebook hub uses the four equal launcher tiles, side-by-side recent items, and a direct link to a genuine saved lesson when available. No completion percentage or memory pending count is fabricated.
- Active Note editor gains a collapsed Find and Replace control and floating-pill formatting bar. The shared Tools button now reveals actual block move, duplicate and delete actions, and can be collapsed again.
- The material chooser limits advertised file types to supported visual/text documents; audio cannot currently be resolved directly as Study material.
- All user-facing additions are sourced from the reviewed EN/RU/LV catalogue; physical screenshots and local builds are not claimed by this source-only change.

## Not yet finished
- Compilation and physical-device acceptance remain separate from source commits; GitHub is used only as source storage and version control.
- Side-effect-free runtime verification, actual Android build and device visual tests still required.
- Tool and pairing animations have safe phase-clipped APIs (start/working/done and connecting/connected) but are not yet bound to runtime events. Edge pulse, cloud handoff, Guide and other FX must be bound to real events per the motion map. No claims that a decorative effect proves a tool/provider executed.
- Wordmark and UI copy on remaining screens; full Round 2 screen-by-screen overhaul, Kids 2.0, translations and legacy-reference retirement.

## Source asset audit

\`python scripts/brand/audit_devkit_v1_2.py\` checks 140 icons, 60 existing orb Lotties, 58 FX Lotties, source identity and phase markers against canonical v1.2 assets. This is not a compiled-build or device acceptance gate.

## Acceptance gates
```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```
Then verify phone screenshots in system/light/dark, all four accents, EN/LV/RU, 320dp and large text, reduced motion, safe-area navigation and background/foreground lifecycle.
