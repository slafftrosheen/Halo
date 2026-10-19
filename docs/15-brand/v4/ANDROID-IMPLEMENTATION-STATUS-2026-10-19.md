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
| H01 | `ui/screens/home/HomeScreen.kt` | When new response content arrives while earlier history is open, offer a pinned **New reply ↓** action without jumping the feed unexpectedly | Source reviewed; live-stream/scroll test pending |
| H02/H03 | `ui/screens/home/HomeScreen.kt`, `HomeComposer.kt` | Display horizontally scrollable source chips above the anchored field; disable unavailable/review-blocked entry | Source reviewed; 200% text and IME tests pending |
| H03 | `ui/screens/home/ContextContinuityControls.kt`, `HomeScreen.kt` | Direct photo/image/Notebook entry points, source details, pinned Done, permission-denied image-picker/settings recovery | Source reviewed; system-picker/permission tests pending |
| H03 | `app/src/main/localization/ui.tsv`, `UiCatalogue.kt`, `res/values*/ui_strings.xml` | Translate new source-status, cloud/local disclosure, and camera-recovery wording in EN/RU/LV | Source reviewed; localization QA pending |
| H04 | `ui/screens/home/HomeScreen.kt` | Keep tool forms composed outside the scrolling feed; disable form execution while answer provider is unavailable | Source reviewed; command execution tests pending |
| N01 | `ui/screens/notifications/NotificationInboxScreen.kt` | User-initiated local notification digest Read aloud; wrap app/digest actions at enlarged font sizes | Source reviewed; privacy/read-aloud tests pending |
| N02 | `ui/screens/notifications/NotificationDetailScreen.kt` | Confirm reply only for the currently offered action fingerprint; stale replies cannot be sent and the draft is retained; missing notification has a safe Back action | Code-integrated; revocation/expiry and device tests pending |
| NB01 | `ui/screens/notebook/NotebookViewModel.kt`, `NotebookScreen.kt` | Undo last successfully trashed batch using the existing Workspace restore operation; partial failed restores remain retryable, Undo can be dismissed; sort and batch controls reflow at enlarged type | Code-integrated; restore failures, rotation and 200% text pending |
| NB02 | `ui/screens/notebook/NoteEditorScreen.kt` | Keep one Apply/Regenerate primary action, prevent stale Apply, wrap edit/review controls, remove unused legacy title UI | Code-integrated; real persistence, conflict and 200% text pending |
| NB05 | `ui/screens/notebook/CanvasEditorScreen.kt` | Keep and Discard remain explicit actions; sheet and inline draft controls stack at 200% text without writing draft nodes automatically | Code-integrated; real draft/motion and large-type screenshots pending |
| A01/A02 | `ui/screens/activity/ActivityScreen.kt` | Hide raw timeline key/value details, delegated model/provider IDs, and speech-engine metadata from the user-facing journal; original runtime evidence is preserved | Source reviewed; regression/device testing pending |
| repository | `.gitignore` | Remove eight checked-in root device logs and prevent newly created local logcat files from re-entering Git | Remote tree confirmed |

Commits (2026-10-19 batches): `1806f699`, `ecb2f50`, `ea35e8c`, `7bfd34d`, `4499709`, `eacd69a`, `e9ecc46`, `1260311`, `a63b327`, `e69d748`, `10ed69f`, `23c8d59`, `52f3a4b`, `0cd12bc`, `ed753d7`, `9a02b9a`.

## Remaining acceptance work (do not auto-mark done)

| Sequence | Package / targets | What to check against the latest kit |
|---|---|---|
| 1 | A shared surfaces: NAV01–NAV04 | Floating 64dp nav pill at 12dp margins, rail at ≥600dp, 360/412 widths, editor/mini/kids hiding, global modal Back and 200% type |
| 2 | B Home: H01–H07 | **Partial source implementation:** H01 new-reply latch, H02/H03 pinned composer/attachments and H04 tools. Still verify history scroll preservation, attachment permission/visual admission, forms' real command outcomes, recovery/approval and one action glow |
| 3 | B Activity + notifications: A01/A02, N01/N02 | A01/A02 technical-copy cleanup, N01 digest speaker and N02 reply-fingerprint guard are code-integrated. Verify real dispatch receipts, revocation mid-confirmation, privacy and TTS cloud consent |
| 4 | B Settings + native assistant: ST01/ST02/ST04, AS01–AS06 | One Settings list; Advanced last; developer switch opt-in; technical routes gated on deep links; readable check-up, assistant overlay and honest role state |
| 5 | C Notebook/Canvas/Memory/Study: NB01–NB08, S01–S05 | NB01 Trash Undo and large-text Library controls, NB02 accessible proposal review and NB05 Canvas draft controls are integrated. Verify on-device states; NB03/NB04/NB06–NB08 and S01–S05 still require detailed parity passes |
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

**Test/build policy for this pass:** deliberately none. No GitHub Actions, Gradle or emulator tests were run. Source-level checks here are limited to code inspection and commit verification. Device acceptance and pixel-diff verification are still required. Do not infer “pixel-perfect” from code review.
