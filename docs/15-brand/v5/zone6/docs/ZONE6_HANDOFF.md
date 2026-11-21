# halo Zone 6 · "Calm Dock, alive" — design handoff

Version 6.0 · 20 Nov 2026 · Android first · baseline repo `slafftrosheen/Halo@257a013` (Zone 5 shell, Screen removed in local build).

Direction chosen by the owner: **A · Calm Dock** structure, with **C · Living Island** shape change and **B · Orbit** spring motion and Places tiles.

## 1. Rules that do not change
- Normal portrait: **top 75% = work only**, **bottom 25% = halo dock**, measured after system insets.
- One ring, classic thick open ring, gap at 1–2 o'clock. Breathes; rotates only for THINKING or a single refresh lap.
- Night Ink / Mint / Cream; Sora headings, Atkinson Hyperlegible Next body (Onest for Cyrillic). Accent follows Appearance.
- Android Back is Back. No duplicate Back in the dock.
- Targets ≥ 48dp adult (prefer 52), ≥ 56dp Kids, 8dp apart.
- Truthful states: Saved needs a write receipt; Done needs the owner's receipt; progress is measured.
- User screens never show IDs, models, endpoints or logs (Settings › Advanced only).

## 2. What changes (fixes the Zone 5 review)
| Zone 5 problem | Zone 6 answer |
|---|---|
| Ring has no word | Caption under the ring: Talk / Listening / Working / Waiting for you / Tap to stop |
| File · Edit · Add · View read as links | 3–4 big labelled buttons, one filled primary, rest in **More** |
| Navigation hidden (hold, swipe, "Menu") | Visible nav: **Home · Workspace · ring · Activity · Places** |
| "All actions" small text | **More** button in the action row (same action lease) |
| Empty quarter on Tools | Layer 1 always says where you are; layer 2 always has actions |
| Compact Settings looks like another app | Settings uses the same dock; only the keyboard gets the compact row |
| Generic Studio error | Error island names the operation, says the draft is safe, offers the matching fix |

## 3. Dock anatomy (390 × 788dp usable reference → dock 197dp)
1. **Place** (y 8, 44dp): tool icon tile + title + real state ("HaloStudio · ✓ Saved"). Optional single **next-step chip** (breathing trace) — hidden when it doesn't fit.
2. **Actions** (y 58, 52dp): one of — up to 4 labelled buttons · the Home composer (attach + field + send) · a segmented switch + 1 button (Library | Tools, Needs you | Running | Done).
3. **Nav** (bottom 4, 76dp): Home · Workspace · **ring 56dp + caption** · Activity (Apricot badge = needs-you count) · Places.

Tap ring = canonical voice action. Hold ring or tap Places = Places sheet. Hold never starts speech.

## 4. Places (Screen removed)
Sheet of big tiles that spring out of the ring, each with live status: **Home, Workspace, Activity, Devices, Settings, Widgets**. The **Widgets** page is the in-app hub for home-screen widgets and their sources (status per source: "Updated 9:38", "1 new", "Can't reach"; which widgets each feeds). Organiser / brief / clocks / source item pages stay reachable by tapping a widget.

## 5. The island (states)
In any non-idle state the dock surface lifts into a floating island (inset 10dp, radius 26dp, above the nav). Nav and ring stay put.
| State | Edge | Content | Ring caption |
|---|---|---|---|
| Listening | accent, screen edge breathes 2.4s | wave + live words + Stop | Listening |
| Working | accent + shimmer line | title, "Step 2 of 3 · …", step chips, Stop | Working |
| Needs you | **Apricot**, static edge | question + detail + [Not now] [**Send to Anna**] | Waiting for you |
| Done | accent; check blooms ×2 then still | "Added 3 events · Calendar · confirmed" + Undo | Talk |
| Error | soft red | "Couldn't export as JPEG · your draft is safe" + [Smaller size] [Export PNG] | Talk |

## 6. Pages
- **Home**: first look = 4 one-tap starts; dock composer; typing → compact row above IME (field + send + 48dp ring).
- **Workspace**: Library | Tools segmented in the dock + New / Recent. Tools = 5 big rows with tool colour and one-line purpose.
- **HaloStudio** Add · Draw · Layers · More (layer edit = 2 taps: Layers → layer). **HaloNotes** Format · Insert · Listen · More. **HaloStudy** Check · Hint · Explain · More (one step at a time). **HaloMemory** Add · Find · Sources · More; suggestions dashed until Remember / Not this. **HaloCanvas** Add card · Connect · Arrange · More.
- **Activity**: segmented Needs you (count) · Running · Done in the dock; Needs-you cards Apricot with action-named buttons.
- **Devices**: state in words ("Ready · tested 9:02", "Saved · not connected"); Add device · Test · More.
- **Settings**: same dock; Find a setting · Look · Help. Groups halo / You / Help; Advanced is the last row.
- **Kids**: warm #FFF3EA, ring mint→apricot 60dp, Type · Help 56dp, nav Home · ring · Learn, max 3 choices.
- **Large text ≥1.4**: dock becomes content-sized; 1 primary + All actions; nav Home · ring · Places; nothing shrinks.

## 7. Motion (see tokens/halo_z6_tokens.json)
Spring = dampingRatio 0.62, stiffness 380 (≈ cubic-bezier(.34,1.56,.64,1) 600ms). Buttons spring out of the ring with 40ms stagger; island morph 550ms; panels fade 280ms after 120ms; Places tiles spring from the ring. Haptics: ring tap context click, listening start, needs-you confirm once, done confirm, error reject. Reduced motion: no overshoot, 150ms fades, Lottie end frames, edge light static.

## 8. Lottie (lottie/, dark + light, shape layers only, validated in lottie-web)
| File | Size | Frames @30 | Markers | Use |
|---|---|---|---|---|
| halo_z6_dock_spring | 390×197 | 36 | out 0–18, settled | buttons spring out of the ring |
| halo_z6_island_lift | 390×197 | 30 | lift | dock → island (reference; Compose drives the real morph) |
| halo_z6_places_open | 374×460 | 40 | open | tiles spring from the ring |
| halo_z6_need_glow | 390×120 | 72 loop | — | Apricot island edge (reduce motion → static) |
| halo_z6_receipt_bloom | 96×96 | 60 | bloom | check + 2 blooms, then still |
| halo_z6_listen_wave | 120×40 | 30 loop | — | listening bars |
| halo_z6_widget_refresh | 48×48 | 36 | lap | one open-ring lap then settles (widgets, sources) |
| halo_z6_error_soften | 390×120 | 24 | — | soft red edge fades in, no shake |
Ring gap verified at 45° in rendered frames. Colours: tint with LottieDynamicProperties from the theme's accent at runtime.

## 9. Home-screen widgets (existing providers)
Clock 2×2 · Clock & weather 4×3 · Upcoming 4×2 · Tasks 4×2 · Brief 4×2 (Read aloud) · Headlines 4×3 · Alarms & timers 4×2 · **Your day in halo** (Dashboard) 4×4 · halo ask 2×2. Glass r28, Night Ink / Cream follow Appearance; small open ring = "ask halo about this" (starts halo with the widget as context). States: fresh ("Updated 9:38 · from 6 sources"), out of date (amber + Try again), not set up ("No weather source yet · Choose a source"), large text (essentials only), 2×1 = one fact. Official alerts = Apricot pill at the top of Your day.

## 10. Build order
1. Tokens + `HaloDock` presentation behind a flag; reuse action lease, ring owner, guide session.
2. Visible nav + Places sheet (+ Widgets page); keep hold-ring as shortcut.
3. Island states wired to real owners (voice, task runtime, approvals, receipts, Studio errors).
4. Keyboard compact row, large text, Kids.
5. Widget restyle (RemoteViews layouts per size/state) + Widgets page.
6. Motion polish: Lottie, haptics, reduced motion.

## 11. Acceptance
Measure 75/25 on device; ring caption matches state; no hidden-only navigation; one primary per dock; Apricot only for needs-you; Done only after receipt; TalkBack: ring has "Open Places" custom action, island changes announced politely; 200% text: no clipped labels; Kids targets ≥56dp; widgets show freshness and honest empty states.

## 12. Open decisions
- ~~Tablet nav~~ → resolved in 6.1: ≥600dp shows every place in the bar (§14).
- Widgets page name ("Widgets" vs "Widgets & sources").
- Your day widget: include news line by default or only alerts.

## 13. First-time guide (6.1)
Shown the first time a person opens a tool; four spotlight steps on the REAL controls (they stay tappable): 1 Where you are (place row) · 2 What you can do (action row; filled = best next step) · 3 Where you can go (nav; Places has the rest) · 4 Tap the ring and just talk (hold = Places; suggestion “What’s on today?”). Skip any time; dismissal persisted; replay in Help & check-up. Never starts the microphone. Paused during typing, listening, tasks, approvals. TalkBack reads the step and moves focus to the target. Kids: 2 steps, read aloud, 56dp buttons. Reuse Zone5 guide session (LocalZone5GuideSession) for suppression + persistence.

## 14. Bigger screens (6.1)
| Window | Dock | Actions | Nav |
|---|---|---|---|
| Phone portrait <600dp | 25%, 3 layers | up to 4 (+More) | Home · Workspace · ring · Activity · Places |
| Phone landscape / short (<480dp tall) | one row (compact rule) | place title + 3 + More | ring 44dp + Places |
| Foldable open ≈673×841 | 25%: place + 4 buttons on one row, nav row | 4 | all places in the bar |
| Tablet portrait 800×1280 | 25% (≈310dp): place + next, 6 buttons centred, nav | 6 | all places in the bar |
| Tablet landscape 1280×800 | 25%: place + 6 buttons on one row, nav | 6 | all places in the bar |
Rules: ring keeps one centred anchor; buttons are promoted out of More in a fixed order (never reshuffled); Places remains available as hold-the-ring.

## 15. Prototype (6.1)
`prototype/proto.html` — open in a desktop browser. Ring: listen → working → needs you → Send to Anna → done; hold ring / Places tab → Places sheet → any place; Workspace → Tools → any tool; HaloStudio → More shows the error island with its fix; Activity filters switch. Walkthrough film: films/halo-z6-prototype-walkthrough.mp4.

## 16. Inner pages (6.3) — boards 12–15
| Page | Dock: place · state | Filled action | Others |
|---|---|---|---|
| Appearance | Appearance · Dark · Mint · Natural | Done | Reset · Help |
| Voice & reading | Voice & reading · Aria · ready | Hear Aria | Test mic · Help |
| Privacy | Privacy · ✓ Private mode on | What left | Memory · Delete… |
| Brain | Brain · On this phone · ✓ works well | Test it | Online helper · More |
| Add a device | Add a device · pick what to connect | Help me choose | Find nearby |
| Pairing (island: Working) | Add glasses · step 2 of 3 | Cancel | — |
| Device | HeyCyan glasses · ✓ Ready · tested 9:02 | Test again | Play sound · More |
| Source | Weather · Riga · ✓ Updated 9:38 | Refresh now | Widgets · More |
| Chat history | History · 38 conversations | New chat | Search · Filter |
| Receipt | Added 3 events · ✓ Confirmed by Calendar | Open calendar | Undo · More |
| Memory item | A memory · kept by you | Edit | Source · Forget… |
| Lesson done | Photosynthesis · ✓ lesson done · saved | Next lesson | Review · More |
| Setup | Setting up halo · 3 short steps | Start | Later |
| Kids · Learn | Learn · 3 things to try | Type | Help |
| Kids · Make | Happy sun · drawing · saved | Undo | Done |
| Grown-up gate | Grown-ups only · uses your phone lock | Unlock | Go back |
Rules: destructive actions are never the filled button and always end with "…" + confirm; choices are shown (swatches, Play, fit badges); Kids' forgiving action is the filled one; the grown-up gate is the phone's own screen lock.

## 17. Final surfaces (7.0) — boards 22–26
Sheets from buttons, Canvas connect, Help & check-up, empty / offline / loading states, and the refreshed-repo features (account sign-in, online voices, full article text). Full spec: `final/FINAL_SURFACES.md`; reference Compose: `final/compose/HaloFinalSurfaces.kt`; 51 strings: `final/res/values/zone6_final_strings.xml`.
Open decisions closed as defaults: hub name “Widgets & sources”; Your day without a news line by default.
Still to fix in code: launcher entry on the Classic alias with the main activity kept enabled; app icon gap at 1–2 o'clock (currently ~2:30).

## 18. Learn — Study + Kids (7.1) — boards 27–32
One path (Choose · Look · Try · Help · Keep), three sizes chosen by the learner, never by age. Spec: `learn/LEARN.md`; model: `learn/compose/LearnPath.kt`; film: `films/halo-learn-one-path.mp4`. New learning colour: lilac #C9B8FF for almost & help; red never appears in Learn.


## 19. Make it yours, alive (7.2) — boards 33–38
See `alive/ALIVE.md`. Customise with a guide first (3 picture questions), then six plain-word tiles; nothing persists before Apply; 7-day “Put back how it was”.
Six live wallpapers (in-app + Android live wallpaper) follow the look, time of day and halo state (mint listening/working, Apricot needs-you only); draw only while visible, ≤30 fps, Still under battery saver / Remove animations.
Widgets animate only on real changes (flipper transitions, TextClock, AVDs, Android 12+ launch animation). Lilac = help: Show me tags, hand demos, one-time tips, stuck offer (never in Kids).
Day one asks only “How much help would you like?”; Easy / Standard / Compact views; Set up for someone else + Keep this layout (phone lock to unlock).


## 20. Every page and tool (7.3) — boards 39–46
See `alive/pages/PAGES.md`. Every surface gets the same five things: Edit this page (More or long-press the title), motion by level, Show me tags, Watch-then-try for gestures, an Easy layout; plus six tours in Help & check-up. Every gesture-only action keeps a non-gesture route (Canvas list view / connect mode, Studio Add, Notes Insert). Hidden Places stay reachable via search and halo.
