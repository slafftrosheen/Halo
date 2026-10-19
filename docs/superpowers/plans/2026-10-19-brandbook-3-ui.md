# Brand Book 3.0 user UI implementation plan

**Goal:** Implement the supplied final user interface in the existing Android app.

**Source:** `docs/15-brand/v3/USER_FACING.md`, then package documents A, B, C, D and SCREEN, their frames and machine-readable contracts. The finished user contract overrides older tab and developer-visibility rules in Package A. No alternate visual direction.

**Baseline:** `4f3c333ef9cbfac14ad4a955e6acff53b5812ce6`, main, pulled on 2026-10-19. Existing Notebook search and LiteRT embedding edits are outside this change.

**Execution:** Inline coding. User explicitly requested no testing. No tests, device runs or visual acceptance claims. Resource generation is part of implementation. The later explicit checkpoint request authorizes a debug APK build.

## 1. Settings and the technical boundary

- [x] Replace Everyday/Advanced tabs with halo, Privacy, Everyday, Connections and Help groups.
- [x] Make Advanced the final Settings destination; put the version in About halo.
- [x] Keep user search free of developer destinations; show an Advanced hint on empty results.
- [x] Keep Model roles, Custom servers and Technical report available inside Advanced.
- [x] Require confirmation before enabling developer tools; retain the existing default-off preference.
- [x] Gate lab, diagnostic and sandbox navigation, including restored destinations.
- [x] Separate user Check-up from diagnostic tools and create a report containing fixed status fields only.
- [x] Add ST04 reviewed local dictation and actual shared reading playback, with “Did you hear halo?” before a user-confirmed voice state.
- [ ] Match remaining ST01/ST02 visual details, status projection and Lilac Lab treatment to supplied frames.

Files: SettingsScreen.kt, SettingsViewModel.kt, DeveloperDestinations.kt, CheckUpScreen.kt and AppShell.kt. Copy lives in ui.tsv and generated resources.

## 2. Home, Activity and assistant — Package B

- [ ] Compare existing Home to H01–H07; keep the composer pinned, mode labels Automatic/Ask/Plan/Act and one next action.
- [x] Wire state aura, native edge, staged receipts, global task pill, approval breathing, reduced motion and Stop suppression in source.
- [x] Replace journal step technical descriptions and reflection panels with plain action descriptions, preserving result truth.
- [x] Replace AS05 technical status copy with human states; retain detailed integration information behind the developer gate.
- [ ] Keep notification hand-offs explicitly unconfirmed.

## 3. Notebook, writing, Canvas, Memory and Study — Package C

- [ ] Apply NB01–NB08 and S01–S05 to existing flows without overwriting the user's Notebook search changes.
- [x] Add the focus dock, put unsaved drafts below the note, retain stale-note protection and Undo, and wire apply/forget effects. Dictation uses the existing reviewed preview flow.
- [x] Require a phone/cloud choice for each new adult Study lesson and enforce it in the tutor gateway; retain existing memory review and deletion/restore owners.

## 4. Kids and voice accessibility — Package D

- [ ] Apply warm Kids colors, 56dp targets, 72dp mic, 18sp body and at most three choices.
- [x] Add the branded grown-up explanation and lock/unlock effects around the real Android credential gate; retain existing protected Kids routes and hidden navigation.
- [ ] Bind reading highlights to actual playback timing and retain typed and silent fallbacks.

## 5. halo Screen — SCREEN package

- [x] Add the supplied ScreenContract, local item/layout persistence and the Home Chat/Screen switch.
- [ ] Implement starter, dashboard, editing, routines, privacy and adaptive full-screen layouts from SC01–SC11.
- [x] Publish actual routine completion summaries to stable cards; retain last successful summaries on failure. Wire device clock, calendar, Notebook, Activity and daily attention; keep provider-dependent cards honest.

## 6. Remaining coverage

- [ ] Reconcile Intelligence, voice, devices and automation against available final designs before coding them. Packages E–G are listed but not supplied as folders.
- [x] Add EN/RU/LV catalogue entries for the new primary controls and regenerate resources. Dynamic user/source content remains unchanged. Polish is not added.
- [ ] Leave route/device sign-off open until separately authorized verification occurs.

## Source integration and cleanup, 2026-10-19

- Imported 410 supplied runtime assets; generated native icon resources and preserved the reference source paths in a manifest.
- Moved the two root reference packs into `docs/15-brand/v3` and `docs/15-brand/reference-v2`.
- Renamed runtime artwork staging to `app/src/main/assets/halo/artwork`; updated import and older generation scripts.
- Added `docs/15-brand/ASSET_LOCATIONS.md` with the canonical paths and motion owners.
- Added a user Apps & services destination; raw MCP setup now lives at `advanced/servers`.
- Source review identified and corrected private card-title leakage, ambiguous routine bindings and receipt effects after Stop. Receipt effects now require the current interaction identity.
- No tests, builds, device runs or visual acceptance performed, as instructed. Unchecked visual/state coverage items remain open; source wiring is not pixel-perfect sign-off.

## APK checkpoint

- User requested a checkpoint build after the coding pass.
- `:app:assembleDebug` succeeded (5m 48s, 447 tasks, 14 executed).
- The compiler exposed missing imports and a Compose animation scope issue; these were corrected before the successful build.
- Preserved APK: `releases/checkpoints/halo-brandbook3-2026-10-19-debug.apk`; adjacent JSON contains the SHA-256 and build identity.
- No tests, installation, device checks or pixel-matching acceptance. Existing unfinished visual coverage remains open.
