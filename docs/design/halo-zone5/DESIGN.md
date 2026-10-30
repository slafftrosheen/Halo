# Halo Zone 5.0 — design contract

Version 5.0.0 · 30 October 2026 · Android first · approved visual direction, implementation proposed.

## Product and user outcome

Halo is a local-first cognitive runtime with an Android host. Its interface helps a person speak, understand the current work, open an instrument, and finish a task without learning the internal architecture. Preserve existing domain owners, object IDs, permissions and runtime boundaries. The visual language is the three user-approved mockups in `reference/mockups/`.

This pack supplies artwork, motion, presentation rules and a migration plan. It does not claim that the production app has been changed. All code references are pinned to `4a227e33eb8e6812ad30481b36e0ceb0f17f4cbd`; refresh only the implementation diff when starting work.

## Decisions that govern every page

1. The upper work area shows the current activity. The lower Halo Zone owns the orb and contextual tools. Normal portrait aims at approximately 75/25 after safe insets; measured controls, readable text, keyboard and accessibility take precedence over a percentage.
2. Global navigation is disclosed by holding the orb, swiping upward from the orb, or tapping the labelled Menu affordance. The sheet contains HaloScreen, Home, Workspace, Devices, Activities & notifications, and Settings. Local/Power editing lives in this sheet through the existing profile owner.
3. Context satellites belong only to the visible instrument. Each has its own icon and persistent label. There is no permanent Home/Workspace/Devices/Settings ring or side column.
4. The orb always uses the canonical voice/runtime interaction. Tap is stateful: talk, finish, stop, interrupt or review, as supplied by the existing owner. Navigation disclosure never starts speech.
5. Workspace separates Library (saved things) and Tools (instruments). Entry defaults to Library. Tools contains direct entries for Study, Memory, Halo Studio and Office Tools. An unopened Tools launcher has no contextual satellites.
6. The screen title, content and contextual actions must agree. Opening Studio must visibly open Studio; changing only an invisible suite value is forbidden.
7. Human and assistant actions share the existing domain authority. A prettier button must not create a second execution path, bypass consent, read a document just because it is visible, or claim unsupported capability.
8. Status is truthful. Ready requires READY. Saved requires the write receipt. Progress requires measured work. Empty requires a successful empty load. A completed animation is never proof of a completed operation.

## Visual language

Retain Night Ink, cream text and the mint open ring. Prefer typography, alignment and whitespace before adding borders or elevated surfaces. Use one grouping surface for a list; avoid wrapping every line in another card. The four tools have stable identifying accents, but names and symbols always carry meaning independently of color.

Headings use Sora; body and controls use Atkinson Hyperlegible Next. Use Onest where the existing Cyrillic typography policy requires it. The actual repository font files and their SIL Open Font Licenses are bundled in `fonts/`. Do not substitute a system font while judging final layout.

The default body is 16sp, supporting text and action labels 14sp, screen title 26sp and section title 20sp. A 12sp caption is reserved for secondary timestamps, never the only label for an action. User font scale is honored. Long translations can occupy two lines; the icon and label are one logical action.

Icons are 24-unit paths, rounded 1.8-unit strokes. A satellite normally shows a 24dp glyph in a 48dp visual surface, with a larger combined icon/label action target. Tool rows show a 40dp glyph with a name and one-line purpose. Android exports are native VectorDrawable XML; SVG masters are tintable. Use assets by semantic ID, not by the nearest visually convenient picture.

## Shared Halo Zone geometry

Measure against the usable window after system status/navigation insets. Keep the orb on a stable horizontal center and bottom anchor. A compact visual variant is allowed for the composer/keyboard; route changes should not make the orb jump.

| Element | Target |
|---|---|
| Visible satellites | Up to 3, never padded with unrelated actions |
| Satellite glyph | 24dp |
| Satellite circle | 48dp visual, combined icon/label target at least 48dp |
| Satellite group | Prefer 88dp width, 14sp label, at most 2 lines |
| Default orb | 72dp visual |
| Composer orb | 64dp visual, same dock anchor |
| Compact/IME orb | 48dp visual with a minimum 48dp target |
| Touch separation | At least 8dp between independent target bounds |
| Composer | At least 48dp height, growing for entered text |
| Zone minimum | 196dp without composer; 216dp with composer, subject to actual text measurement |
| Large text | Flat labelled controls/list projection from approximately 1.4 font scale or whenever measured bounds do not fit |

These minimums explain why 25% is a target, not a clipping instruction. On short windows, do not keep an oversized quarter plus keyboard. Use the compact zone, keep Back and the current primary action reachable, and expose the same secondary actions in a labelled list. An expanded temporary navigation sheet can cover part of the work area. Do not scale text down to reproduce the generated image exactly.

The ring remains open at approximately 1–2 o'clock. It is not a universal icon for unrelated tools. Retain its identity in idle, active and static fallback states; the ring must not rotate just to suggest activity. The current renderer only runs full Lottie at >=80dp. Add an explicit Zone 5 policy that supports the new shape-only compositions at 64/72dp, while the 48dp compact orb stays static. Do not change the legacy threshold globally. Motion restrictions always select static state; see `docs/ANDROID_INTEGRATION.md` and `docs/MOTION_HINTS_BADGES.md`.

## Interaction and navigation

The tap decision is made after gesture arbitration. A recognized long press/upward drag cancels the pending tap. A satellite drag cancels the pending click. Start the upward menu gesture on the orb, above Android's bottom system gesture region. Provide semantics for both the normal action and opening navigation.

The global sheet has a title, current destination, named rows and a single existing Local/Power control with its required explanation. The Menu affordance can carry a quiet attention marker. The orb continues to communicate its current task and does not become an ambiguous notification count.

The orbit's ordering is stable by semantic action ID and owner. Preserve carousel position within the current entry where sensible. Avoid rearranging controls when asynchronous state arrives. Disabled controls explain why; unavailable capability does not borrow an enabled-looking action or fabricate a tool. A full action list exposes the same action lease and enabled reasons as the orbit.

On drill-down pages, display the instrument/document identity and Back in the zone's utility band. Document content can carry a title in the work area. High-frequency editor commands remain immediately accessible through contextual controls; the orbit must not bury Save, Stop, Undo or the active tool's primary operation behind several turns.

## Screen language

**HaloScreen:** glanceable clock/date/weather, chosen widgets and continuation cards. Its actions are Widgets, Arrange and Appearance, with screen presentation settings in the full list. Editing mode clearly changes layout and exposes Apply/Cancel; previews do not silently persist.

**Home:** conventional conversation with the latest content followed while the user is at the end. Respect deliberate history browsing. One composer, one send action, one voice orb. Default satellites are Attach, New chat and History; answer mode and less frequent conversation controls remain available contextually. A result card is an actual object/action receipt, not a duplicate toolbar.

**Workspace:** Library/Tools is local navigation. Library presents type-aware rows, search, filters, collections and honest saved state. Tools presents four directly openable instrument rows with descriptions. Do not mix arbitrary launcher actions and file-selection actions in a single orbit. Canvases belong in Halo Studio; notes/documents belong in Office Tools while their existing IDs/routes remain compatible.

**Study and Kids:** one learning step at a time, visible progress only where it is real, a clear route back to the lesson and scoped voice ownership. Attachments keep their real source and revision. Show guidance and hints before solutions where the existing learning policy requires it. No point awards or completion celebration before the learning owner records them.

**Memory:** make stored facts, sources, pending suggestions and forgetting understandable. Review/edit/forget have distinct semantics and confirmation where appropriate. A system memory suggestion must not appear as a user-approved fact.

**Devices:** state text accompanies the device pictogram. Saved, scanning, connecting, ready, unavailable and permission denied are different states. A physical link is not READY. Device control remains behind real capability and consent checks. Technical identifiers stay in Technical details.

**Activities and notifications:** one destination with two clear subviews, deduplicated events and meaningful counts. Needs you, Running and Completed are filters in the contextual utility band. Successful background events do not need persistent intrusive banners.

**Settings:** capability groups retain Brain / Voice & Reading / Vision / Devices / Appearance / Privacy and actual existing destinations. Show outcome-oriented names first; provider/model implementation details are available where relevant to a choice. Advanced/developer routes remain gated and are not put in the main navigation.

The exhaustive route and nested-surface plan is in `docs/PAGE_IMPLEMENTATION_PLAN.md`, with machine-readable coverage under `source-audit/`.

## States and accessibility

Every page has a successful-content, loading, loaded-empty, recoverable-error, unavailable/offline, restricted, selection and keyboard state where applicable. Preserve usable content during non-destructive refresh. Permission refusal provides a contextual recovery route rather than repeated unsolicited prompts.

Use TalkBack labels for actions and meaningful state changes, merge icon/label semantics, keep headings navigable, and restore focus after sheets. Do not announce decorative animation frames. No essential action is gesture-only. Text contrast should meet 4.5:1, relevant icon/surface contrast 3:1; validate in light and dark themes and high-contrast modes. The color contrast report in `validation/` checks the authored token combinations, not the final Android composition.

## Scope and completion

This is a host UI migration. Keep the runtime, provider contracts, hardware protocol assumptions, persistent object identities and source-version guards. All interactive features retain meaningful typed and STT-final operability through existing owners. The pack does not authorize or imply paid media calls, publishing, hardware protocol promotion or remote infrastructure.

Completion requires the full route coverage matrix, a functioning global menu before fixed roots disappear, scoped satellites, all-state handling, accessible labels, meaningful assistant parity and focused native validation. Asset checks and desktop rendering are evidence for the pack only; they do not substitute for Android, physical touch, actual speech, provider or hardware acceptance.
