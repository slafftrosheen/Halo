# Halo Screen workspace implementation plan

User authorization: implement each issue completely before executing builds/tests,
then verify, push main and close only the delivered scope. Execute inline;
independent review is read-only. Start: main `3a301bb7d3ea1aaffdd8f511d8e6863bf42d9632`.

## Design and boundaries

Extend the existing ScreenLayout, LocalScreenStore, ScreenLayoutRequests and
native cards. Keep Chat | Screen and all source/voice/approval owners. Use the
current BrandBook v4/DevKit 2.0, bundled fonts and Open Ring. No external image,
font, arbitrary markup, executable layout, new provider or cloud host.

Store bounded typed presentation fields alongside the existing card/config
records. A 12-column canonical grid maps to the available 2/3/4 columns; enlarged
text reflows to one readable column without overwriting saved placement.
Collision resolution is deterministic and never drops a card. Row heights grow
for measured content, preserving two-dimensional minimum sizing without clipping.
Drag movement/resize commit to a draft on release; labelled controls and keyboard
equivalents provide every operation without dragging. View mode has quiet cards,
compact controls, a clock focal point and existing shared orb discovery.

Editing previews changes locally, with explicit Apply/Cancel and conflict checks
against the saved base. Configuration recreation retains the draft; no edit
automatically publishes. Named presets are bounded local layout snapshots with
explicit preview/apply and source references. Restore defaults retains source data.
Fonts are System/Sora/Atkinson with the existing Cyrillic fallback. Backgrounds
are bounded theme-derived colours; translucent surfaces keep an opaque privacy
mask and readable contrast. Per-card title/value/clock emphasis never defeats OS
font scaling. Proposals remain bounded native changes, individually reviewable,
with explicit Apply/Undo; research remains the existing sourced search handoff.

Ambient remains in-app. Exit stays reachable, Stop keeps the #46 owner, and quick
note delegates to HomeViewModel/Notebook. Privacy masks sensitive cards/details
while locked/backgrounded and by default in ambient; sensitive preview surfaces
follow the same policy. Scoped secure-window, brightness and keep-awake changes
restore on exit/disposal/background. Charging, low battery, saver and thermal
state bound keep-awake/dimming. Burn-in shifting is small, lifecycle scoped and
independent of decorative motion. No AOD/overlay/hotword promise.

## Work

- [x] Add typed grid/style/appearance fields and bounded codec, preserving all
  legacy sizes, config, archive/pin flags, item IDs and existing source records.
  Extend the canonical store with persisted named presets and safe draft Apply.
- [x] Add deterministic responsive placement/collision/move/resize logic and
  a measured native grid. Add drag/resize handles, accessible labels/actions,
  keyboard movement and equivalent linear controls; never clip 200% content.
- [x] Integrate a retained edit preview with Apply/Cancel/Undo, per-widget
  typography, layout background/font/surface choices, presets and restore defaults.
  Preserve source/card actions; quiet the viewing toolbar and keep the orb usable.
- [x] Extend bounded proposals with per-change review and preview. Preserve
  canonical research/Notebook/Calendar/routine owners and honest missing sources.
- [x] Add quick note through the existing HomeViewModel owner, returning to
  Screen; implement ambient privacy, scoped window/power safeguards and explicit exit.
- [x] Complete EN/LV/RU resources and source-first regression/interaction/native
  layout fixtures for migration, persistence, collisions, draft conflicts,
  accessible sizing, privacy, ambient policy and owner handoffs.
- [x] Complete independent review before running the serial build/test gate.
  Fix actual failures/findings, reverify changed behavior, install/readback exact
  APK on the connected phone and record named checks with settings/data restored.
- [ ] Publish source and evidence to main, verify clean 0/0, update #37/#43 with
  exact qualified versus unqualified acceptance, close delivered scope and continue.

## Verification receipt

Final combined gate: 128 app cases plus 13 runtime cases, zero failures/errors/skips,
24 native renders. Named Samsung observations and qualification limits are recorded
in docs/testing/SCREEN-WORKSPACE-037-VERIFICATION-2026-10-23.md. Phone findings required
canonical Home note-result observation, resolved-input projection reconciliation and
native refusal terminalization; execution and approval retain their existing owners.
The publication checkbox is completed by the remote receipt recorded in issue #37.
