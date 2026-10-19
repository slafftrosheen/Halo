# halo · Package B — Home, tasks, notifications · native assistant · Settings
Brand Book v3.0 track · chapter 15 · repo pinned `3f217ccb` · 2026-10-19
Covers: **H01–H07, A01–A02, N01–N02**, the **native assistant AS01–AS06** (replaces C04) and the finished **Settings ST01, ST02, ST04**. N03 Notification settings ships with Package F.
The finished, user-facing contract is in `../USER_FACING.md`: everything technical lives in Advanced only.
Also defines the **2027 light layer** (aura, edge light, glass, live task pill, one-step-ahead suggestions), which every later package inherits.
Design status: designed. Implementation status: **not verified on a device**. QA signs off each screen against a real device screenshot.

## What's in the folder
| Folder | Contents |
|---|---|
| `motion/` | **Motion films, 1080×1920, 30 fps:** assistant (dark + light), Home ask → done, Settings simple by default, stop & recover, the halo light, plus a 68-second reel and posters |
| `screens/` | 216 PNGs at 2× (720×1560). `<ID>_<state>_<dark|light>.png`, plus `_w412` (412dp), `_fs200` (200% text) and accent × tone variants of Home |
| `boards/` | Book chapter 15: 11 screen boards (all states, interaction table, next action per state), 2 journey pages, variants, motion binding |
| `source/` | Editable HTML/CSS source. `shot.html?s=H07&st=request&t=light&acc=lilac&tone=warm&w=412&fs=2` renders any frame. `prototype.html` = clickable prototype with 7 journeys |
| `lottie/` | 5 new animations (+ `halo_fx_assist_invoke`, screen-edge sweep) × dark/light + still SVGs for reduced motion |
| `compose/HaloTaskFx.kt` | Receipt → animation mapping, plus a Stop-safe queue |
| `compose/HaloLightFx.kt` | `haloAura`, `haloScreenEdge` (INVOKE/PULSE/ATTENTION), `lightFor(phase)`, live task model |
| `strings/` | `strings_b.json` (378 keys, English final, lv/ru/pl empty and ready) + `values/strings_package_b.xml` |
| `spec/package_b.json` | Interactions, states → next action, motion rows, journeys (machine-readable) |

## Rules this package locks
1. **The composer never scrolls away.** It sits above the nav pill and rides above the keyboard. Attach + Tools + mode live in its bottom row. At 200% text, Attach/Tools become icons (TalkBack labels stay).
2. **Modes use the app's real labels:** Automatic · Ask · Plan · Act (`homeModeLabel`). Each says what it does in one line. Act is the only one that can use apps, and it still asks.
3. **Approval sheet = a global layer.** It names the action ("Send 6 photos to Anna in Messages?"). The approve button is the verb ("Send photos"), and the decline is specific ("Don't send"). Closing the sheet leaves the request waiting in Activity. Apricot breathes on it. It never rotates.
4. **Three result types, drawn differently:**
   - Confirmed: check and "Confirmed".
   - Unconfirmed, e.g. a reply handed to another app: dashed ring and "Delivery not confirmed".
   - Failed: error orb with the reason, and no celebration.
   A success animation runs only for `HomeToolReceiptKind.CONFIRMED`.
5. **Stop cancels everything.** Pending checks or blooms are dropped (`HaloFxQueue.stop()`). The stopped card lists what already happened.
6. **Cloud is never preselected.** The long-PDF choice shows two equal buttons. Offline shows "Answer on this phone" as a real fallback.
7. **One glow per screen.** It is the next-step card or the retry action, never the approval sheet (Apricot already says "needs you").
8. **No technical details on user screens.** A run shows "What halo did" (apps used, where it ran, how long). IDs and logs exist only in Advanced › Technical report.
9. **Notifications:** "Private · last 24 hours". The digest is "Local summary · no AI · no cloud". A reply is sent only after confirming, then it shows "Reply handed to Messages · Delivery not confirmed".

## Copy notes for engineering
Source strings that use capital Halo should be lowercase: `Ask Halo…` → `Ask halo…`, `How Halo helps` → `How halo helps`, `Allow notification access to see permitted notifications in Halo` → `…in halo`.

## Motion binding
See `boards/B15-motion.png` / `spec/package_b.json → motion`. New assets:
| Asset | Markers | Use |
|---|---|---|
| `halo_fx_task_step` | pending · running · done | step dot; `done` only on a confirmed step |
| `halo_fx_approval_breathe` | needs_you | approval sheet, Activity "Waiting for you" |
| `halo_fx_receipt` | confirmed · unconfirmed | result row / sheet header |
| `halo_fx_stop_cancel` | stop | global Stop, barge-in |

## Acceptance (per screen)
- [ ] Dark + light match `screens/` at 360 and 412dp
- [ ] 200% text: nothing clipped, composer pinned, chips scroll
- [ ] Every interaction row fires the listed event; Back goes where the board says
- [ ] Motion only on the listed real event; reduced motion shows the still
- [ ] All strings come from resources (no hard-coded text)
- [ ] Device screenshot attached to the route row in `halo_routes.csv`

## Settings, finished (ST01 · ST02 · ST04)
- **ST01 Settings is one list:** a status card at the top ("Everything's working", or one fix). Groups: halo · Privacy · Everyday · Connections · Help. **Advanced** is the last row. Search covers user settings only.
- **ST02 Advanced:** Model roles · Custom servers (MCP) · Technical report · **Developer tools switch** (with a confirm). Labs & diagnostics appear only here, with the Lilac "Lab" tag.
- **ST04 Check-up:** a user-friendly System check. It offers one fix at a time, and speaking only counts as working after "Did you hear halo?" → Yes. The shareable report lives in Advanced.

## 2027 light layer (applies to every package)
| Part | Rule |
|---|---|
| Aura | Soft accent light at the top of the screen. Breathes: idle 4.8 s · thinking 2.6 s · acting 2.4 s · listening 1.8 s. Apricot = needs you, faint red = error, one bloom on a confirmed result |
| Edge light | The phone gets a halo only while halo listens as assistant or actually acts. One 0.9 s sweep up both sides on invoke, then a slow pulse. Never spins |
| Live task pill | A running task hugs the nav on every tab: orb · title · real progress · Stop. Tap → run detail. Apricot when it needs you |
| One step ahead | Up to 3 next actions above the composer and a ghost suggestion in the field. Nothing is sent until you tap. Never cloud, payments, permissions or deleting |
| Glass | Nav, composer, sheets and the assistant card are frosted over the aura. No live blur below Android 12 or when thermal ≥ MODERATE (solid surfaces, same layout) |
| Real events only | Mic open → listening. Audio playing → speaking. Acting → edge. CONFIRMED → bloom. Stop collapses all light in 333 ms |

## Native Android assistant (AS01–AS06)
The overlay lives in `HaloVoiceInteractionSession` (`AssistantSurface`). It's a floating glass card over whatever app you're in, and the screen edge lights up.
- **AS01 Ask & answer:** invoke → listening (live words) → understanding → answer / speaking. Chips: "In · Maps" and "Current screen included ✕".
- **AS02 Act in the current app:** uses the app's own action ("Start navigation"). Sensitive actions get the Apricot edge, the consequence in plain words, and a verb button. Done = "Maps confirmed".
- **AS03 Voice request from another app:** the app's question is quoted, with "The app is asking you". Options are big and can be spoken.
- **AS04 Problems:** screen not shared, mic busy, failed ("Nothing was changed"), and the hand-off to halo.
- **AS05 Settings (`android_assistant`):** active / selected but not active / not selected / needs repair. No technical status on this page: it lives in Advanced › Assistant diagnostics (developer tools).
- **AS06 Entry points:** the Quick Settings tile "Talk to halo". The **lock-screen frame is a proposal** and needs engineering to confirm how keyguard is handled.

Copy fixes from source: "Hello, I'm Halo" → "Hello, I'm halo"; "Open Halo" → "Open halo"; "…continues in Halo." → "…in halo."; "Ask Halo…" → "Ask halo…"; "How Halo helps" → "How halo helps".
