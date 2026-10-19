# halo Android — BrandBook 3.0 / DevKit 2.0 parity ledger

**Audit date:** 2026-10-19  
**Authority:** `docs/15-brand/v4/3dcc6a14-f443-49ca-abd1-7595eb6d370b.pdf`, `IMPLEMENTATION.md`, `USER_FACING.md`, `tokens.json`, `product/A/spec/halo_designs.csv`, `halo_routes.csv`, and package boards.  
**Not an acceptance certificate.** Source inspection and changes do **not** prove pixel parity. This ledger tracks work separately from device screenshots.

## Baseline and scope

The latest kit supersedes BrandBook 2.3 / DevKit 1.4. Its product matrix contains **95 design IDs**, **56 existing routes**, and one **proposed** halo Screen route, grouped into packages A, B, C, D, E, F, G and SCREEN. Compare Compose against the kit's 360dp, 412dp, and 200%-type screen exports, not an older Round 1 mockup. Motion must follow real events, never a timer standing in for device state. One primary highlighted action per screen.

Existing app assets include the brand wordmark, Sora/Atkinson fonts, the ring artwork and the DevKit's dark/light Lotties, including the 2.0 additions. **Asset presence is not evidence that every state is correctly wired.**

## Changes integrated on `main` in this pass

| IDs | Source | Change | Verification |
|---|---|---|---|
| H01 | `ui/screens/home/HomeScreen.kt` | Pin brand/Updates header outside the scrollable feed; preserve separately anchored composer; reduce empty vertical padding | Source reviewed; screenshot pending |
| H01 | `ui/screens/home/HomeBrandHeader.kt` | Resize on-page presence so conversation is visible on narrow screens, without changing the global hero orb | Source reviewed; screenshot pending |
| H02 | `ui/screens/home/HomeScreen.kt`, `HomeComposer.kt` | 200%-type attachment semantics and mode-label truncation; keyboard Send obeys the visible action's enabled/review/Stop state | Source reviewed; interaction test pending |
| A01/A02 | `ui/screens/activity/ActivityScreen.kt` | Hide raw timeline key/value details, delegated model/provider IDs, and speech-engine metadata from the user-facing journal; original runtime evidence is preserved | Source reviewed; regression/device testing pending |
| repository | `.gitignore` | Remove eight checked-in root device logs and prevent newly created local logcat files from re-entering Git | Remote tree confirmed |

Commits: `1806f699`, `ecb2f50`, `ea35e8c`.

## Remaining acceptance work (do not auto-mark done)

| Sequence | Package / targets | What to check against the latest kit |
|---|---|---|
| 1 | A shared surfaces: NAV01–NAV04 | Floating 64dp nav pill at 12dp margins, rail at ≥600dp, 360/412 widths, editor/mini/kids hiding, global modal Back and 200% type |
| 2 | B Home: H01–H07 | Earlier message pagination and reuse, edit draft, scrolling position, pinned composer at IME, Attach/Tools/mode and recovery/approval sheets, one action glow |
| 3 | B Activity + notifications: A01/A02, N01/N02 | Compact 3-tab journal, real execution truth, no IDs/logs/models in user view, notifications privacy, unconfirmed external hand-off |
| 4 | B Settings + native assistant: ST01/ST02/ST04, AS01–AS06 | One Settings list; Advanced last; developer switch opt-in; technical routes gated on deep links; readable check-up, assistant overlay and honest role state |
| 5 | C Notebook/Canvas/Memory/Study: NB01–NB08, S01–S05 | Hub/library, note editor, dashed AI draft until Keep, canvas gestures and object state, memory Forget confirmation, lesson steps and practice |
| 6 | D Kids and voice/reading: K01–K05, V-series | ≥56dp tap targets, 72dp Kids mic, no adult jargon, real parent gate, speech/typing/read-aloud on all relevant content |
| 7 | E/F/G advanced product flows | Model roles and provider setup, cloud opt-in, device Ready ≠ Connected, routines and applications, 24 theme combinations, localizations, backup, performance and privacy |
| 8 | SCREEN | **Do not silently ship the proposed Chat | Screen entry.** Product sign-off is explicitly pending in DevKit 2.0 |

**Known open product decisions from `IMPLEMENTATION.md`:** halo Screen entry, AS06 lock-screen surface, missing provider dependencies for weather/articles, and Polish UI marked “coming soon.” None of those should be faked in user-facing UI.

## Pixel-parity / functional sign-off matrix

For each design ID in `halo_designs.csv`:
1. Capture dark and light on an actual device at **360dp and 412dp**; align against `product/<package>/screens/<ID>_<state>_<dark|light>.png`. Audit 412dp margins instead of stretching 360dp proportions.
2. Repeat at **200% system text** with TalkBack and edge-to-edge insets. All controls must stay reachable; no text clipping or occluded composer.
3. Open/close IME and bottom sheets; rotate and resume; test system Back from each state and deep link.
4. Execute each input/event/result row from package `spec/package_*.json`. Only journal-confirmed outcomes get a success state. No permission/cloud/payment/destructive action may be nudged by the guide.
5. Verify reduced motion falls back to matching still artwork, and Lottie durations follow actual runtime events (Stop and barge-in cancel prior effects).
6. Review EN/LV/RU localized layouts, long labels, font fallback, 24 appearance combinations and light/dark contrast.
7. Attach a matching **device screenshot**, pixel diff and interaction evidence to the design row before changing `implementation_status` from unverified to verified.

**Test/build policy for this pass:** deliberately none. No GitHub Actions, Gradle or emulator tests were run. Device acceptance and pixel-diff verification are still required. Do not infer “pixel-perfect” from code review.
