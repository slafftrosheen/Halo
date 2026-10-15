# Round 2 screen comparison and implementation continuation

Date: 2026-10-15. Base: `4528f4900e43c18ec20912ad6fc87af7abd6f909` (HALO-DEVKIT-176).

Authority: `DevKit_v1.2/Implementation.md`, Brand Book v2.1 chapters 09-10, the current `round2/` boards, canonical theme values and supplied artwork. Round 2 supersedes historical boards.

The implementation notes below precede device verification. The subsequent build, 85 focused tests, 24-theme Language renders and literal device comparisons are recorded in [the installed-APK review](DEVICE-VISUAL-REVIEW-2026-10-15.md). Historical deferred-test notes below describe their original implementation stage.

## Screens

| Screen / reference | Implemented or retained in source | Still unfinished |
|---|---|---|
| Home presence / 05 | Responsive 160/192/224dp presence when there is no answer; compact status during work/conversation; Aura only at ready; microphone ripple reads `SpeechInputStatus.capturing` and its actual level. Suggestions are editable draft chips. | Rendered comparison of final composition at small/large widths. Continue/review now uses the actual primary Study item, with Aura suppressed while its Guide is shown. |
| Home reply / 05 | Rounded Listen/Stop reading, Copy and Save to Notebook actions. Listen uses the existing speech event owner and checks committed answer identity; it never submits another model request. | Persisted per-turn execution location and cloud mark. The selected provider is not used to guess past provenance. |
| Home acting / 05 | Edge pulse on the Home root during known phone-action tool IDs; recognized timer/search/open-app tools show their working marker only while `ExecutingTool` is present. Stop stays in the existing composer. Raw tool IDs removed from the default status copy. | Full overlay while another app is foreground, exact numbered plan, and success/failure marker transitions correlated to tool outcomes. Leaving ExecutingTool is not assumed to mean success. |
| Updates / 05 | Chronological notification cards with source icons and trailing ages; All/Can reply/halo segmented pill; search/digest controls disclosed from the header and a definite list ending. Existing access, digest and app-filter owners retained. | Internal lesson/memory/approval updates need an actual aggregated feed. The existing source is permitted Android notifications; no fake Needs you count or Clear-all action was added. |
| Activity / 10 | Existing Needs you/Running/Done projections and durable counts retained in a shared segmented pill; plain root heading and shared header improvements apply. | Full detail-by-detail layout parity and verification of action-plan presentation. |
| Approval / 10 | Generic Approve buttons now display the exact requested action; custom host labels remain intact. Apricot approval glyph/border, rounded surfaces and clearer decline copy. Confirm/Deny/Dismiss semantics are unchanged. | A numbered exact plan requires structured plan data from the owner; plain detail text is preserved rather than turned into invented steps. |
| Devices / 10 | Ready sessions gain larger glyphs and subtle accent-tinted surfaces; disconnected devices retain neutral styling. Warning ink has separate light/dark values. No signal strength or battery data is invented. Device groups precede phone capabilities; ready cards expose the existing sound-test and device-settings entry points. | Voice and sound card now shows the last recorded intended/actual voice route when present and opens the existing output settings. Direct reconnect and rendered multi-device hierarchy remain. |
| Pairing / 10 | Larger search presence; existing connecting/connected markers retained. New permission access is explicitly unguided. | Existing three-stage path retained. Completed pairing shows the actual saved device name and hides the stale discovery list. Rename and final failure-layout variants remain. Pairing FX was already bound before this continuation; the older migration remainder overstated that gap. |
| Intelligence / 08 | Conversation location/model summaries merged into one section with passive status chips. Short heading/body guidance on eligible on-device next steps only; model IDs stay behind Details and Change model is secondary. | Final balance of the summary, status chips and secondary tools on actual screens. |
| Providers / 08 | Location groups, canonical glyphs, neutral connection states and default label. Raw current-model ID moved into provider Details. Connection-check dates use the app locale. | Status card, connection-details disclosure, credential state, 12 dp fields and radio semantics now apply. Saving is a neutral primary action; no Guide draws attention to a new credential. Inline field-specific errors still require structured field validation. |
| Model library / 08 | Visible location filters, clearer provider labels and spacing; current eligibility and selection owners retained. | Final row density, friendly descriptions and installed-model flow presentation. Opening a location filter never selects an inference provider. |
| Model roles / 08 | Human routing labels, routing disclosure, role-option disclosure and real radio selection. Removed unconditional Configured label. | Full role-row composition; measured evidence remains available under its existing disclosure. |
| Model download / 08, 13 | Actual fractional progress in an accent ring; unknown total remains indeterminate; resumed bytes and sizes localized. File verification has its own state. | Installation Bloom now requires an observed active download followed by Installed and VERIFIED_ARTIFACT. Opening an existing installation does not celebrate. Final rendered review remains. |
| Settings / 11 | Language placed under Your companion. Appearance moved from the long settings page into a dedicated screen with System/Light/Dark and palette controls. Identity uses the supplied fixed-colour wordmark. | Remaining destination summaries and rendered review. Main rows now use tighter card insets, current location/language/appearance values, and three responsive nearby tiles; Appearance, Kids and nearby controls share Look and feel. |
| Appearance / 11, 13 | Existing light/dark palette persistence retained. Light palette/Dark palette labels distinguish palette editing from mode selection. Pill controls and one-shot canonical gradient sweep in Your halo preview; no logo recolouring. | Current screenshots across all 24 combinations and accessibility sizes. |
| Language / 11 | Existing searchable native-name picker retained; shared accessible header now applies. | No unsupported auto-detect-reply switch or app-wide date compliance claim. |
| Context/privacy and permissions / 11 | Existing real policy switches and sharing boundaries retained. Copy pass and shared styling apply. | Daily transfer ledger and precise seven-ability policy presentation; no fake zero-transfer report. |
| Voice / handoff section 14 | Existing capture/routing owners retained; copy pass and shared styling apply. | Finish inner-screen composition and bind any additional mic visuals only to actual capture state. |
| System check / 11 | Existing observation-based summary and explicit diagnostic actions retained. | One best actionable fix with a correctly gated Guide, rather than a decorative claim that checks ran. |
| Notebook / 09 | Existing four entry paths, saved continuation and recent items retained; shimmer skeleton is displayed only while the real library is loading. Four equal launchers use the supplied canonical icons. | Pending-memory Guide now counts decodable, non-secret MEMORY proposals from the durable pending queue and opens Suggestions. Final composition review remains. |
| Note editor / 09 | Existing Find/Replace, blocks and floating formatting bar retained; copy and shared heading changes apply. | Compact Find & replace bar, expandable replacement row, match-navigation menu and floating formatting surface now apply. Recovery, checked boxes and links use theme colors; links are underlined. Keyboard/safe-area inspection remains. |
| Canvas / 09 | Existing provisional draft review and dashed draw-on border retained; copy pass applies. | Successful organization and manual Arrange operations now supply pre-save positions for a staggered 900 ms glide. Connections use the same animated coordinates; new group frames settle once after arrival. Dragging and Undo clear arrangement motion. Rendered review remains. |
| Study / 09 | Existing source/material/learning flow retained. Highlighted words now pair the selected background with its matching foreground. | Material choices now sit directly on the page with 44 dp icon surfaces, current setup step appears in the header, learning chips have 48 dp targets, and the path uses circular state markers. Lesson Bloom follows an observed active-to-COMPLETED transition. Remaining rendered lesson composition and navigation review are deferred. |
| Kids / 06 | Existing Ask/Learn/Create home retained. Grown-up options and exit switch use Android device-credential authentication; cancellation does not unlock. No unlocked flag or app PIN is persisted. Reader highlights only current narration ranges, with 56dp pause/resume/stop controls. | A dedicated parental PIN/policy, daily time limits, parental cloud/camera rules, weekly summaries, adventure path and earned sticker collection. Device credentials are not claimed to be a complete parental policy. Word-by-word seeking remains unfinished. |
| Shared parts / 12 | 22sp bold header with a dedicated 48dp back control; titles/subtitles wrap. Existing card/sheet radii retained. Reply chips and palette controls are full-round. Loading skeleton supports still reduced-motion rendering. Unknown shared progress is static with reduced motion. | Complete toast Undo/Open and every field/banner variant, followed by actual rendering. Shared empty states now use a centered static Open Ring, centered bounded text and neutral optional actions. |

## Motion and themes

- `LocalHaloRingColors` supplies the exact Mint/Blue/Lilac/Coral gradients from the DevKit to FX and the Appearance preview. The same values apply across Natural/Warm/Cool and Light/Dark. Semantic attention/error colours remain separate. Wordmark and Kids artwork retain their own brand colours.
- FX observes both the app/system reduced-motion preference and foreground lifecycle. Still action glyph/Open Ring fallbacks replace staged effects under reduced motion.
- Newly wired: microphone ripple, known phone-action edge pulse, working phases for recognized tools, on-device next-step Guide, Appearance accent sweep, download ring and Notebook loading shimmer.
- Previously wired and retained: pairing phases and Canvas draft draw-on.
- Still pending: actual cold-start/summon events, correlated tool start/done/failure, durable memory-save moment, per-turn cloud handoff/provenance, shared-orb navigation transition and earned Kids celebration.
- One-shot and staged FX now use 108 generated Android vectors from the canonical static SVGs, covering both modes and four accents. Splash and Kids Bloom retain their fixed brand colors. Supplied paths, transforms, gradients, inherited opacity and dashed strokes are preserved by the authoring converter; rendered acceptance remains deferred.

## Copy and delivery state

- App-authored direct labels and registered helper labels use lowercase halo. Existing translation keys are retained for compatibility; replacement EN/LV/RU keys are generated together from `ui.tsv` (2,749 catalogue entries after this pass).
- The copy pass does not transform user content, model replies, notes, imported documents, provider output or tool results.
- Source changes are local and uncommitted. No build, test, render, installation or publication was performed for this continuation.
- The next implementation pass should start with per-turn provenance/internal Updates, the remaining device/settings compositions and structured approval-plan data. Current source must be built and rendered later before any compliance or device claim.


## Visual geometry continuation

This implementation pass follows the 05/08/10/11/12 boards more closely in source:

- Home: pill composer with direct image import and a 72dp mic/send control; Stop remains visible during active work. Draft editing remains available while sending is gated. Completed conversations omit the redundant presence card. Time-of-day greeting, actual location/mode chips and the saved Study Continue action replace excess status panels. The Study Guide and ready Aura are mutually exclusive.
- Settings: removed nested section insets, grouped Look and feel, current values alongside ordinary-sized labels, stacked values at larger font scales, and canonical language/chevron glyphs. Nearby uses three real Mini/floating/widget actions rather than three full-width text buttons.
- Updates and Activity: shared segmented surface with actual selected semantics and untruncated growing labels. Updates is chronological, keeps source ages near the heading and discloses search/digest controls from the header.
- Devices: header Add control, ready/saved device groups first and phone capabilities afterward; explicit Settings and the existing sound-test entry point on ready cards.
- Intelligence: short next-step headings/body, one primary action, passive model/connection chips and secondary Change model.
- Shared empty states: static Open Ring and bounded centered copy, with optional actions that do not add an unsolicited Guide.

No build, test or new screen rendering was run for this pass. Pixel alignment, actual layout at each size, image/keyboard behavior, speech ownership and all-theme visual acceptance remain unverified. Source implementation is not pixel-perfect sign-off.

### Model library, roles and privacy continuation

- Model library uses the shared segmented location control and pill search field; model cards lead with a canonical icon, name and provider. Location and the actual conversation default are passive status chips. Capability summaries remain visible, while tool protocol expands through Details. Setup/download requirements and ineligibility remain explicit; existing pick and setup callbacks are retained.
- Model roles use consistent icon headings, quieter job descriptions and selection-mode labels, and location chips. Protocol information lives beside the existing measured-details disclosure. Role binding and routing callbacks are retained.
- Context & privacy now uses section labels above 20 dp cards and matching 44 dp icon surfaces. Context switches have a single full-row Switch semantic target with a 64 dp minimum row; they still write the same stored policy. No transfer-ledger claims were introduced.
- This is source implementation only. Builds, tests and rendered theme comparisons remain deferred by request; no pixel-perfect signoff is implied.

## Broad implementation continuation

This continuation extends beyond the earlier two-screen batch:

- Notebook now receives the real pending MEMORY proposal count through its view model and existing dependency graph. Review opens Suggestions; no automatic memory approval or separate store was introduced.
- Canvas organization review groups sources into cards, puts raw identifiers behind Technical details and names the confirmation Tidy up the board. Its checklist option has a full-row checkbox target. Canvas More uses the reference's plain action rows and accent glyphs for the three halo actions.
- Successful saved arrangements emit a transient visual event containing original positions. UI glides cards and connections together, then settles newly created groups. Nothing about manual drag persistence or source ownership is changed. Reduced motion, Undo and manual dragging snap immediately.
- Study Material no longer nests all choices inside an extra card. Setup progress names the actual current step, material destinations use 44 dp icon surfaces, and the lesson path uses circular markers. Reader controls have 48 dp targets and locale-formatted speed labels. Completion motion only follows an observed active lesson becoming durably completed.
- Provider detail now leads with a compact status card; connection metadata is disclosed. Configuration enums are actual radio groups. Credential saving stays an explicit neutral action. Voice no longer guides the user into granting a new microphone permission; last-route labels are localized.
- System check separates status, descriptive text, diagnostics and action. Long all-caps actions no longer squeeze the body text. Blocked/setup states stay neutral, genuine failures use error colors, and the summary distinguishes availability from passed checks.
- Devices shows recorded voice-routing facts and links to output settings. Finished pairing shows the target's saved name and a Done action while suppressing the discovery list.
- Verified download completion now owns installation Bloom and a light haptic; existing installations do not replay it. On-device model cards use theme-aware selection colors and hide repository identifiers behind Details.
- Note Find & replace is a compact bar with a match-navigation menu, accessible large-text layout and full-width replacement editor. The floating formatting surface stays inside the existing IME-safe boundary. Recovery, link and checkbox colors follow the active theme.
- `scripts/brand/generate_static_fx.py` authors 108 reduced-motion vector resources and the Kotlin resource map directly from supplied static SVGs. No bitmap conversion or runtime dependency is added. Dashed paths use vector trim intervals; paths retain their Bezier geometry.
- Shared feedback uses explicit error foreground/background pairs. EN/LV/RU catalogue authoring now contains 2,749 entries.

No build, tests, app renders, installation, commit or push was performed. Pixel-perfect acceptance and all-theme visual verification remain open.
