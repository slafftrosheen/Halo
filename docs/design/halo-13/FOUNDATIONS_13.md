# halo 13.0 · Batch 1 — Foundations

**Anyone can use it.** This batch covers plain words, three depths (Simple, Standard and Pro), dynamic UI on every screen, and style and wallpaper 13.
Checked against repo `slafftrosheen/Halo` at **99aac2f** (23 Nov 2026, 11:46). This is a design and developer spec, not a build. Nothing has been tested on a device.

Boards: 137 brief · 138 plain words · 139 three depths · 140 dynamic UI · 141 style & wallpaper · 142 icons, motion, files.

---

## 1. Why 13.0

Measured in `app/src/main/res/values` (English):

| Finding | Number |
|---|---|
| Strings in total | 8,299 |
| Strings that use a tech word (provider, model, runtime, endpoint, diagnostics, receipt, context, token, …) | **553 (6.7 %)** |
| Strings with "model" / "provider" | 239 / 88 |
| Strings longer than 140 characters | 112 |
| Onboarding stages before you can try anything (`SetupStage`) | 10 |

Examples from today's app: "Provider address could not be reached", "Runtime policy", "Endpoint", "Configure later / limited mode", "Choose or download a listening model".

Also new in the repo since 12.0 batch 5 (c9648b4 → 99aac2f):
- brushes on each stroke (`brushes.json`, `InkBrushPresets`, `sketch:v4`)
- `instruments-12.json`
- Studio: Bézier nodes, shape operations, DXF import, named projects
- adaptive Today exported to widgets and routines
- `LiveWorksheetCamera`, `StudyRehearsalSheet`, `KidsFoxLesson`

Still missing: Notes sketches stop at 64 strokes (`CanvasSketchCodec.MAX_STROKES`). That is fixed in batch 4.

## 2. Six rules for 13

1. **Try first, set up later.** halo works in the first minute. A permission is asked for only when its feature is used.
2. **Everyday words.** Simple and Standard screens use no word from the glossary. Pro screens may, and long-press always explains it.
3. **One main thing per screen.** Each screen has one large primary action. Everything else is one tap away under "Show more" and is never deleted.
4. **Three depths, your choice.** You can set the depth for the whole app or for each tool. halo may offer a change, but it never switches on its own.
5. **The screen fits the moment.** It changes at most twice each time you open it, and "Why?" always explains the change.
6. **Beautiful and calm.** The wallpaper follows the sky, the looks match it, and motion can be turned down. Colours keep their meaning: mint means done, apricot needs you, lilac means help, and red is only for errors.

## 3. Three depths — `ui/adaptive/v13/ExperienceLevel.kt`

| | Simple | Standard | Pro |
|---|---|---|---|
| Main actions visible | 1 large action + up to 3 big buttons | today's halo | everything, including menus and shortcuts |
| Words | glossary "plain" | glossary "plain" | tech words allowed, explained on long-press |
| Targets / body text | 56 dp / 17 sp | 48 dp / 15 sp | 40 dp / 14 sp (tablet and desktop) |
| Gestures | none hidden; each has a visible button | hints on first use | all, listed in Help → Shortcuts |

**First depth.** The first depth comes from the onboarding question "How much do you want to see?" (batch 2).
- Large text or TalkBack starts on Simple.
- Kids profiles are always Simple.

**Offers.** halo offers a change at most once every 14 days for each tool (`LevelAdvisor`).
- **Up:** after 7 or more days of use, with 12 or more "Show more" uses and 2 or fewer help opens in 14 days.
- **Down:** after 6 or more help opens in 14 days.
- **Undo three times in a week:** halo stops adapting that screen (`Pins.frozen`).

**Never removes anything.** At a lower depth, tools move behind "Show more". Search and voice still reach every tool.

Settings → **How much to show** lists every tool with its depth.

## 4. Dynamic UI everywhere — `ui/adaptive/v13/DynamicSurface.kt`

A screen is a `SurfaceSpec` made of:
- **anchors:** back, title, halo ring, dock and the one primary action
- **blocks**, each placed in a slot (TOP, MAIN, TOOLS, BOTTOM_SHEET, SIDE_PANEL) with a minimum depth
- **rules:** *when all of these signals hold → Promote / Reveal / Tuck / MakePrimary*

Signals: `DayPart`, `InputKind` (finger, pen, keyboard, voice, glasses), `FormFactor`, `Situation` (home, on the move, abroad, studying, working, winding down) and `ExperienceLevel`.

**Promises (enforced in `SurfaceResolver`):**
- Anchors are not blocks, so they cannot move.
- At most **2 rule changes** each time a screen opens.
- Pins win over any rule. A block the person hid never returns.
- There is exactly one primary action.
- Every applied rule carries a plain reason (string resource) for the **"Why does it look like this?"** sheet. The sheet offers **Keep it like this · Back to normal · Stop changing**.

`assets/adaptive/v13/surfaces-13.json` covers 9 screens (Today, Canvas, Studio, Notes, Planner, Translate, Scan, Study, Settings). For each it lists the blocks at every depth and the rules (for example, Notes + pen → sketch becomes the primary action; Translate + abroad → conversation first; Studio + keyboard → menus appear).
Batches 3–5 extend this file for each tool.

### Compose usage
```kotlin
val surface = remember(signals, pins) { SurfaceResolver.resolve(Surfaces.canvas, signals, pins) }
surface.primary?.let { PrimaryAction(it) }
surface.visible.forEach { Block(it) }
if (surface.tucked.isNotEmpty()) ShowMore(surface.tucked)
if (surface.applied.isNotEmpty()) WhyChip(surface.applied)
```

## 5. Plain words — `ui/plain/PlainWords.kt`, `assets/plain/glossary-13.json`

The glossary has 30 entries: the tech word, its plain English and plain Russian equivalents, and a one-line explanation in each language.

Writing rules:
1. Use everyday words on Simple and Standard screens.
2. Keep sentences to **22 words** at most (12 in Kids). Longer text goes to "Learn more".
3. Make buttons verbs that name the result: "Let halo hear me", not "Continue".
4. Write errors as **what happened → what halo did → what you can do**. Never lead with a code.
5. Don't blame the person: "isn't answering", not "invalid address".
6. Give numbers in units people know.
7. Russian: «вы» for adults, «ты» for kids. "halo" and tool names stay in Latin letters, and buddy is «бадди».

**"What's this?"** Long-press any word to see its glossary explanation. **Say it simpler** rewrites the sentence on the phone.

**Ratchet test** (`PlainWordsRatchetTest`). The build fails if the number of strings with tech words goes above **553**. Lower that baseline in the same pull request that fixes strings. String keys that start with `pro_`, `dev_`, `diag_` or `peripheral_` are exempt.

## 6. Style & wallpaper 13

### Sky — `wallpaper/v13/SkyTimeline.kt`
The three wallpaper periods (MORNING, DAY, NIGHT) become **six phases**: NIGHT, DAWN, MORNING, DAY, GOLDEN and DUSK, with a 0–1 blend between them.
- **Timing:** taken from today's sunrise and sunset. The weather source is used if the person allows it, otherwise a latitude table, otherwise Riga defaults (07:30 / 17:30). halo never asks for location just for the wallpaper.
- **Season:** follows the date, and flips in the southern hemisphere.
- **Weather layer:** clear, clouds, rain, snow or fog, drawn softly on top.
- **Motion** maps to `HaloLookMotion`:
  - FULL: slow drift
  - GENTLE: crossfade on unlock
  - STILL: changes only on unlock, with no animation

### Looks — `assets/looks/looks-13.json`, `ui/theme/v13/LookEngine.kt`
There are 8 looks: Calm night, Paper morning, Lively, World, **Match my wallpaper**, **Clear & big** (high contrast, large text, Still motion), Night garden and Sunrise. Each one extends `HaloLookPreset`.

**Match my wallpaper** works like this:
1. Take the seed colour: the average of the 10 % most saturated pixels in a 64 × 64 thumbnail.
2. Step the seed lighter (dark theme) or darker (light theme) in 12 % steps until it reaches **3 : 1** against the surface.
3. Pick the text colour on the accent so it reaches **4.5 : 1**.
4. Pull red-like seeds toward apricot, so the accent can never look like an error.

`MeaningColours` are locked: no look or wallpaper can change done, needs you, help or error.

Kids get a **Pick your sky** screen: four large sky cards, with no settings words.

## 7. Icons
There are 25 new icons. They follow the 24 px grid with a 1.75 stroke and round caps and joins, and have no fills. Each is supplied as `assets/halo/artwork/ic/halo_ic_<name>.svg` and `res/drawable/halo_ic_<name>.xml`, in the same format as the existing 140:

level_simple, level_standard, level_pro, plain_words, explain_this, adapts, one_action, why, pin_layout, reset_layout, wallpaper, sky_day, sky_night, season, weather_mood, look, text_size, contrast, calm_motion, tint, helper, show_more, show_less, tip, anchor.

## 8. Motion (Lottie)
All animations are in `res/raw/halo13_motion_<name>_{dark,light}.json`, at 256 × 256 and 30 fps, using Lottie v5.12.2 shape layers only (no images and no expressions).

| Name | Frames | Use |
|---|---|---|
| level_shift | 90 | the depth switch; the tiles go 1 → 4 → 9 |
| page_assembles | 75 | a made-for-you page builds; blocks rise in, then the lilac spark |
| explain_bloom | 60 | "What's this?"; the word underline grows and the bubble opens |
| sky_cycle | 120 | the wallpaper preview: dawn → dusk → night |
| tint_from_wallpaper | 75 | "Match my wallpaper": a drop tints the button, then the contrast tick |

Rules:
- Easing is 0.33 / 0.67. Nothing is faster than 200 ms.
- With Still motion, show the last frame.
- With Gentle motion, play once and don't loop.

## 9. Strings
`strings_foundations_13.xml` has 37 strings in EN and RU. These cover the depth question, offers, the "Why?" sheet, Show more and Show less, Look & wallpaper, the sky, motion and the meaning of colours.

## 10. QA before merge
- [ ] Each of the 9 surfaces renders at all 3 depths with exactly one primary action.
- [ ] Opening a screen never applies more than 2 rules. "Why?" lists them in plain words.
- [ ] Undo three times in a week freezes that screen. Kids profiles never get offers.
- [ ] The ratchet test passes at 553 or lower.
- [ ] The derived accent reaches 3 : 1, and its text reaches 4.5 : 1, on all 8 looks in dark and light.
- [ ] The sky phase is correct for Riga in June (sunrise 04:30) and in December (09:00). The wallpaper must not need location.
- [ ] Still motion: no Lottie ever loops.
- [ ] A native Russian speaker reviews the Russian strings.

## 11. Open decisions for the team
1. Is "Show more" always the last item on Simple, or a floating button?
2. Should the depth be stored per device or per profile (for example, a tablet on Pro and a phone on Simple)?
3. Can the "Say it simpler" rewrite run on every phone, or only where the on-device AI is good enough?
4. Should the weather layer in the wallpaper use the Planner weather source, or a separate one?
