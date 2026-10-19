# halo Screen — design + integration handoff (chapter 19)
Brand Book v3.0 track · repo pinned `3f217ccb` · 2026-10-19 · follows `../USER_FACING.md` and the light layer.

> **Chat is where you talk to halo. Screen is where halo works for you.**

Design status: designed (SC01–SC11). Implementation status: **new, not built**. Nothing here removes or renames Home, Chat, the composer, attachments, approvals or onboarding.

## 1. Navigation decision (needs sign-off)
**Chosen:** A Chat | Screen switch at the top of the Home tab. Home (Chat) stays the default and unchanged; the 5 tabs stay. On tablets ≥ 840dp landscape, Home can show Chat and Screen side by side.

Why:
- No 6th tab: 6 labelled items don't fit 360dp at 48dp+ targets with readable labels
- Matches the mental model: Chat is where you talk to halo, Screen is where halo works for you — two faces of the same Home
- Zero change to existing Home routes, composer, attachments, approvals or onboarding
- Voice reaches it from anywhere: “Open Screen”

Rejected:
- **6th bottom tab:** crowds 360dp, shrinks every target, breaks the finished nav contract
- **Replace Activity or Notebook tab:** removes core destinations the brief says to keep
- **Screen inside Settings or Advanced:** the brief requires a consumer surface
- **Floating button over Home:** competes with the composer and the live task pill

Other ways in:
- Voice: “Open Screen”, “Go full screen”
- Routine detail: “Show latest result on Screen”
- Home next-step card (first time only): “New: your Screen”
- Launcher shortcut (long-press app icon): “Screen”
- Mini halo: “Open Screen”

Route: `screen (proposed child of home; Back → Home/Chat). Full screen = same route, immersive mode. No external deep link in v1.`
⚠ DESIGN DECISION — needs engineering + product sign-off before wiring: Chat | Screen switch inside the Home tab.

## 2. Screens (see `boards/`, `screens/`, `motion/`)
| ID | Screen | States |
|---|---|---|
| SC01 | Entry + first run | home-switch, intro, starter-ready |
| SC02 | Dashboard (phone) | top, scrolled, private, offline |
| SC03 | Customize | editing, resize, removed |
| SC04 | Widget picker | picker, preview |
| SC05 | Card detail | detail, article |
| SC06 | Routine result card | not-set-up, ready, updated, failed |
| SC07 | Interests | feed, no-source, topics |
| SC08 | Ask halo to change Screen | listening, preview, applied, unsupported |
| SC09 | Full screen / ambient | phone, phone-land, tablet-land, tablet-port, night |
| SC10 | Screen settings & privacy | settings, reset |
| SC11 | Card states | states |

Interaction tables (input → event → result → prerequisite) are on each board and in `spec/package_screen.json`.

## 3. Rules this design locks
1. **The user is in control and halo assists.** halo proposes a layout change and shows a summary. Small reversible changes can apply straight away, with Undo every time. There is always a manual path, including Move up/down for people who can't drag.
2. **Publish and update, never spam.** A routine updates *its* card in place (stable id `automation:<routineId>`). The card glows once and shows "Updated 07:02 · 3 new". Past runs live in Activity, not as extra cards.
3. **Hiding a card never deletes or disables its source.** The toast says so ("…The automation keeps running").
4. **No fake data.** If there's no weather provider, no reading source or no Google account, the card says what it needs (Set up / Connect / Choose a source). A failed run keeps the last good summary and is labelled "Last update failed".
5. **Freshness is always visible:** Updated · Stale · Offline · cached · Updating. When one card fails, the rest of Screen keeps working.
6. **Private stays private.** Email, calendar and tasks are marked PERSONAL/PRIVATE. In full screen, "Hide private details" (on by default) blurs them until the phone is unlocked.
7. **Interests are opt-in.** halo only uses topics you add. "Suggest topics from my notes" is off by default and asks before adding anything. halo never takes topics from chats.
8. **Permissions are asked only when needed:** calendar when you add the calendar card, location only if you choose it for weather (a city works too). No new permissions at app launch.
9. **Full screen is the same layout with the chrome hidden.** Exit and voice come back with a tap. "Stay on while charging" and "Dim at night" are user settings. The layout shifts slightly against burn-in. It makes no lock-screen, AOD or always-on-top promises.
10. **Quiet and lifecycle-aware.** No feed animation. All motion and refresh pause when the app is backgrounded, the screen is off or battery saver is on. Every animation has a still version.
11. **Nothing technical on Screen.** Mock feeds, the layout JSON editor and the publish tester live only in Advanced › Developer tools.

## 4. Responsive rules
| Width | Grid | Priority |
|---|---|---|
| Compact phone < 400dp | 2 columns · 10dp gap · cards span 1–2 · nav pill | Now → Needs attention → For you → Continue |
| Wide phone 400–599dp | 2 columns · larger type · 412dp reference | same order |
| Tablet portrait 600–839dp | 3 columns · nav rail · clock spans 3 | Now row, then 3-up |
| Tablet landscape ≥ 840dp | 4 columns · rail · optional Chat | Screen side by side | clock 2×2, cards fill by priority |
| Phone landscape | 4 columns compact | clock 2 wide |
| Full screen | same layout, +40% type, chrome hidden, 24dp safe margins | private cards masked when 'Hide private' is on |
| 200% text | cards grow taller, never truncate titles to < 2 lines; compact cards become standard | — |

Section order: **Now → Needs attention → For you → Continue.** Pinned means a card keeps its place. Freshness is shown separately from importance.

## 5. Real vs. not-yet (from today's source)
| Card | Status | Source | Note |
|---|---|---|---|
| Clock & date | **REAL** | device clock | works offline |
| Calendar | **REAL** | CalendarContract via CalendarTools.kt | READ_CALENDAR asked when added |
| Continue (Notebook) | **REAL** | Notebook store | — |
| halo finished | **REAL** | Activity runs (plain words) | — |
| Next up (tasks) | **PARTIAL** | halo tasks + follow-ups (DailyAssistantProjector) | complete/edit only where APIs allow |
| Routine results | **NEEDS HOOK** | AutomationRuntime / RoutineExecutor → new ScreenItemStore.publish | the v1 end-to-end target |
| Email summary | **NEEDS ACCOUNT** | GoogleMailReader (Gmail API) inside a routine | Google account connection required |
| Weather | **NEEDS PROVIDER** | no weather provider in source | honest Set up card until one exists |
| Interests / articles | **NEEDS PROVIDER** | no feed/search provider (single-page fetch only) | saved reading fallback |

## 6. Engineering contract (`contract/`)
- `ScreenContract.kt`: `ScreenItem`, `ScreenItemState`, `ScreenSensitivity`, `ScreenAction` ids, `ScreenLayout`, the idempotent `ScreenItemStore.publish/markFailed`, and `LayoutProposal` (halo proposes; the registry validates).
- `widget_registry.json`: 8 widget types with sizes, source, permissions, offline behaviour, sensitivity and real status.
- `layout_example.json`: a persisted layout, including the full-screen privacy and dim settings.
- Path: **Task / Routine / Notebook / Provider → ScreenItemStore → Widget registry → Responsive renderer.** Cards work without any model running.
- Secrets stay in the existing secure credential store, never in layouts.

## 7. Alpha slice
| | |
|---|---|
| **Ready to build now** | SC01 entry + starters · SC02 dashboard · SC03 customize (incl. Move up/down) · SC04 picker · SC05 detail · SC10 settings · SC11 states · clock, calendar, notebook, halo finished, tasks cards · full screen phone/tablet (SC09) with keep-on-while-charging + private mask |
| **Build with one new hook** | SC06 routine result card: publish from AutomationRuntime to a local ScreenItemStore (idempotent by routine id) |
| **Design ready, waits on providers** | weather card, interests/articles, email summary (Google account) — ship as honest Set up cards |
| **Deferred (roadmap)** | SC08 full natural-language composition beyond simple move/add/remove/resize · time-based profiles · system overlay · lock-screen/AOD-adjacent · multi-device |

## 8. Motion
- **Card update in place:** ScreenItemStore emits new version for the same id → soft accent ring glows once 1.2 s. Reduced motion: “Updated” chip changes only. Asset: `halo_fx_card_update (new)`
- **Starter layout appears:** first layout chosen → cards rise in sequence 80 ms apart. Reduced motion: instant. Asset: `CSS/Compose`
- **Customize enter/exit:** Customize / Done → cards gain outline + handles 200 ms. Reduced motion: instant. Asset: `Compose`
- **Voice request:** mic open → listening aura + ripple. Reduced motion: static. Asset: `halo_fx_voice_wave`
- **Full screen:** enter immersive → chrome fades 300 ms, type scales. Reduced motion: no aura motion. Asset: `Compose`
- **Burn-in shift:** every 5 min in full screen → whole layout moves ≤ 6dp. Reduced motion: same (not decorative). Asset: `Compose`
- **Lifecycle:** backgrounded / screen off / battery saver → all animation paused, refresh paused. Reduced motion: —. Asset: `lifecycle-aware`

## 9. Voice
"Open Screen" · "Read my morning summary" · "What's next on my calendar?" · "Add a weather card" · "Make the clock bigger" · "Hide this from my screen" · "Go full screen".
Every voice request shows listening → change preview → applied (Undo) or unsupported (with the closest manual option). Stop works at any point. Read aloud uses the shared reading bar, so speech never plays twice.

## 10. Languages
Following your standing instruction, all copy is **English only**. Every string has a key (`strings/strings_screen.json`, `values/strings_package_screen.xml`), with empty slots ready for LV/RU. Cards grow taller at 200% text instead of truncating titles below 2 lines.

## Acceptance (from the brief, mapped)
- [ ] Screen is reachable from Home (Chat | Screen) without opening Advanced
- [ ] Home, Chat, voice, attachments, nav and onboarding are unchanged
- [ ] A starter layout works with no sign-in, and external cards show honest Set up states
- [ ] The phone and old tablet frames match `screens/` in portrait and landscape
- [ ] Layout changes survive a restart, and manual editing works with no model running
- [ ] A routine result publishes and updates in place with a timestamp, and a failure keeps the last good result
- [ ] Hide never deletes the source, and Undo works after remove, move and apply
- [ ] Full screen enters and exits predictably and private details are masked
- [ ] TalkBack order, Move up/down, 200% text and reduced motion all work
- [ ] Opening Screen doesn't slow Chat, speech or inference (needs engineering measurement, not visual sign-off)
