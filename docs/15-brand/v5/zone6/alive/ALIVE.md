# halo · Make it yours, alive — pack 7.2 · boards 33–38

Baseline repo `98aa2fb`. Builds on 6.4 `look/` (HaloLook, HaloPreset, HaloMotion) and the existing owners
`PalettePreferences`, `VisualPreferences` (lessMotion, guidanceAmount, guideLight), `nearbyScheme()` (widgets),
`KidsGrownUpGate`. **Design + reference code. Not compiled against the repo, not built or tested on a phone.**

Four layers, one rule each:

| Layer | Rule |
|---|---|
| Customise | A guide first (“Do it with me”), six plain-word tiles after. Live preview, Apply, Undo, “Put back how it was”. |
| Live wallpapers | Slow, built from the ring and the look’s colours. Same meaning as the app: mint = listening/working, Apricot = needs you. |
| Widget motion | One motion per real change. If nothing changed, the widget is still (clock excepted). |
| Guidance | Lilac `#C9B8FF` = help. Shows, never blocks. No age, no skill test — only “How much help would you like?”. |

## 1. Make it yours (board 33) — Places › Make it yours
Opening screen:
1. **Do it with me** (lilac card, top) — 3 questions, each with two pictured answers on real mini home screens:
   Q1 “Which feels more like you?” Calm night / Bright paper → look + wallpaper ·
   Q2 “How big should things be?” Comfortable / Bigger → text size + dock button size ·
   Q3 “How much should things move?” Lively / Calm / Still → `HaloMotion` FULL / GENTLE / STILL.
   Each card has “hear it” (TTS of name + one line). The whole phone previews the answer. Dock: Back · Read aloud · **This one**.
2. **Six tiles**: Look · Wallpaper · Widgets · Dock & buttons · Text & size · Motion & feel (each tile shows its current value in words).
3. **Put back how it was** — change log of the last 7 days in plain words (“Wallpaper: Paper → Aurora · Sat 10:12”); restore any one entry.

No jargon on level 1: never “accent”, “haptics”, “tone”. Those words appear only inside the tile, with a picture.

**Looks** = the six existing `HaloPreset`s, each now paired with a default live wallpaper:
Night Mint → Halo Drift · Paper Sage → Tide · Slate Blue → Constellation · Cool Lilac → Aurora · Coral Day → Day Arc · Warm Rose → Paper.

**Dock & buttons** (new): one swappable dock slot (Workspace default; Learn or Library). Home, the ring and Places are fixed so nobody gets lost.
Button size Regular (48dp) · Large (56dp) · Extra large (64dp). Labels Always · Selected only (existing 6.4 setting).
Tablets (≥600dp) keep all six places in the bar; the swappable slot does not apply there.

## 2. Live wallpapers (boards 34–35)
Six wallpapers, in-app background **and** Android live wallpaper (home + lock):

| Wallpaper | Built from | Reads | Loop |
|---|---|---|---|
| Halo Drift | open ring (gap at 45°) breathing ±3.5%, 4 soft colour fields | look accent | 90 s |
| Aurora | 4 ribbons (accent, lilac, blue, pink) + faint stars | look accent | 60 s |
| Tide | 5 wave layers, sun glow | time of day | 45 s |
| Day Arc | the ring travels a dotted arc as sun (accent) / moon (lilac), stars at night | time of day | real time |
| Constellation | 48 drifting points, links under 95dp | halo state | 70 s |
| Paper | light cream, grain, sage/blue/pink fields, ring shadow | look (light side) | 120 s |

Time of day: morning `#1a1760→#ff9f6b`, day `#1d3b8a→#b6dfe2`, evening `#2a1252→#f3c0d9`, night `#04021f→#120d4a` (smoothstep between keys).

**Reacts to halo** (toggle, default on):
- Listening → mint glow from the bottom + 4 ripples (2.5dp, 90dp/s) from the dock area.
- Working → mint glow; on Constellation the links pulse mint, bottom → top.
- Needs you → Apricot radial glow from the bottom edge, breathing 0.75–1.0 every 3 s. **Only** for needs-you; never decorative.
- Error → no wallpaper change (the island already says it).

**Calmer at night** (default on): after 22:00 and on the lock screen: 50% speed, 25% dim.
Picker: one big live preview, six thumbnails, “See it at” Morning · Day · Evening · Night, Reacts to halo, Calmer at night. Dock: Undo · **Apply**.

Engineering (`java/HaloLiveWallpaper.kt`): `WallpaperService` + `Choreographer` frame callback at ≤30 fps **only while `isVisible`**;
stops on `onVisibilityChanged(false)`, screen off, battery saver (`PowerManager.isPowerSaveMode`) → Still frame.
Motion level: FULL 1×, GENTLE 0.5× and no ripples (static glow), STILL = one frame redrawn on change only.
Android “Remove animations” (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`) always forces STILL.
halo state reaches the wallpaper through `HaloAmbientState` (a process-wide `StateFlow`; the service lives in the app process).
`res/xml/halo_wallpaper.xml` + manifest snippet in `manifest/`. Thumbnail = the Halo Drift end frame.

## 3. Widget motion (board 36)
| Widget | Change | Motion (Full) | How on Android |
|---|---|---|---|
| Tasks | task ticked | circle fills (spring), text strikes, row folds, next task rises | `ViewFlipper.setDisplayedChild` with `halo_row_out_up` / `halo_row_in_up`; strike = pre-rendered state |
| Your day | new item | list slides up one row; new item mint for 10 s | `AdapterViewFlipper` / collection update + in-animation |
| Ask halo | idle | ring breathes 5% every 4 s | `animated-vector` drawable (`halo_ring_breathe.xml`) — Full only |
| Ask halo | listening | live wave | assistant island owns the wave; widget swaps to the listening layout |
| Clock | every minute | mint ring fills over the minute | `TextClock` + `AnimatedVectorDrawable` 60 s, restarted by the minute tick |
| Learn | step done | ring fills one segment + one mint bloom | 5 pre-built ring states, `setImageViewResource` + layout animation |
| Weather / sources | new data | 300 ms crossfade, “updated” in mint 10 s | `ViewFlipper` fade in/out |
| Any | open app | Android 12+ launch animation from the widget | `android:id/background` on the widget root (already in 6.2 layouts) |

Rules: updates only on real events (no per-second `updateAppWidget`). GENTLE = 200 ms fades, no springs. STILL = instant swap (`setInAnimation(null)` equivalent: use the `_still` layout variants).
Files: `res/anim/halo_row_in_up.xml`, `halo_row_out_up.xml`, `halo_fade_in.xml`, `halo_fade_out.xml`, `res/xml/...` not needed; `java/HaloWidgetMotion.kt`.

## 4. Guidance (board 37) — lilac = help
1. **Show me** (Help › Show me, every screen): lilac numbered name tags on each control in the current screen, from the controls’ own content descriptions; “Read all” reads them in order. Tap a tag → that control’s one-line explanation. Dock: Stop showing · **Read all**.
2. **Watch, then try** (gestures only: hold the ring, swipe up the dock, drag a card): spotlight + a hand that demonstrates; card “Hold the ring to talk” with **Again · Do it for me · Got it**. Shown at most 3 times per gesture.
3. **Tip, once** — appears in the dock area at the moment it helps; never covers the work area’s main content; never repeats. After 2 “Got it” in a row tips get one line shorter; “No more tips” = level None.
4. **Looking for something?** — after 3 back-and-forths within 60 s without a change (Settings, Places, Make it yours), a lilac card offers **Tell halo** · **Show me around** · close. Never in Kids, never during a task, max once per 10 min.
TalkBack on → Show me becomes “Explore this screen” and tips are spoken; the hand demo is replaced by a spoken instruction.

## 5. Any age, any tech level (board 38)
- First day asks **one** question: “How much help would you like?” → Show me everything / A little help / I know my way (= `VisualPreferences.guidanceAmount` HIGH / MEDIUM / OFF). Pictures show the difference. No age, no skill quiz.
- **Views** (like Learn sizes): **Easy** (4 big buttons on Home — Talk to halo, My day, Messages, What’s this?; labels and read-aloud everywhere; 64dp dock buttons; GENTLE; Show me on) · **Standard** (the full Calm Dock) · **Compact** (labels on selected only, no tips, FULL motion).
- **Set up for someone else**: Easy view, Show me, Read aloud, Keep this layout, Motion — previewed on the phone, then Apply.
- **Keep this layout**: blocks changes to Make it yours / dock / widgets; unlock = the phone’s own screen lock (same rule as `KidsGrownUpGate`). Asking halo always works. Shown on Home as one quiet line (“Layout kept by Mark”).
- Kids keeps its own rules (pack 7.1): Pictures size, 56dp, no stuck-detector, no tips — the grown-up sees them instead.

## 6. Motion table
| Moment | Full | Gentle | Still |
|---|---|---|---|
| Preview a look | wallpaper crossfade 600 ms + widgets repaint | 300 ms fade | swap |
| Apply | one mint bloom from Apply | fade tint | none |
| Wallpaper idle | live (loops above) | 0.5× speed | one frame |
| Wallpaper reacts | ripples / glow 600 ms in | glow only | colour tint only |
| Show me tags | pop in 450 ms apart, 10dp rise | fade together | appear together |
| Hand demo | hand + spotlight, 2.4 s loop | same, no spotlight pulse | spoken + static arrow |
| Tip / stuck card | rises 24dp, 400 ms | fade | appear |
Film: `films/halo-make-it-yours-alive.mp4` (52 s).

## 7. Acceptance
- Every first-level label is a plain word; every choice in “Do it with me” has a picture and can be heard.
- Nothing persists before Apply; every change appears in “Put back how it was” for 7 days.
- Wallpaper never uses Apricot except for needs-you; reacts-to-halo can be switched off.
- Wallpaper draws only while visible; battery saver and Remove animations force Still.
- Widgets animate only on real changes; no per-second widget updates.
- Guidance never blocks: every tip/card has a close, nothing auto-plays sound, stuck card max once per 10 min.
- No question about age or skill anywhere. Keep this layout unlocks with the phone lock only.
- All controls stay in the bottom 25% dock; targets ≥ 48dp (Easy 64dp, Kids 56dp).
