# halo 9.5 · Instruments, motion, guidance, articles, Your day, dynamic cards (boards 77–87)

Design and UX, with reference code for motion and cards. Continues the 9.0 Pro toolkit (sections 18–31). Nothing here has been built or tested on a phone or tablet.

## 32. What this round adds
- Full **instrument lists** for all 11 tools (147 instruments) — boards 77–78, `dev/INSTRUMENTS_9.5.md`, `dev/halo-instruments.json`, `dev/halo-instruments.csv`.
- A template for building **one instrument** properly — board 79.
- **Motion inside the tools** with code — board 80, `dev/HaloToolMotion.kt`, `source/tool-motion.html`, `films/halo-tool-motion.mp4`.
- **More guidance** — first tours, "Where is…?", name tags, Show me, guided tasks — board 81.
- **Articles bring their pictures** — gallery strip, all-pictures grid, viewer with credit — board 82.
- **Your day in halo** as a real report — words first, then cards — board 83.
- **Dynamic cards** for answers, Kids and Study, plus the rules to build them — boards 84–87, `dev/DynamicCards.kt`, `dev/halo-card-schema.json`, `source/dynamic-cards.html`, `films/halo-dynamic-cards.mp4`.

## 33. Instrument lists (boards 77–78)
Every instrument has: **id** (`tool.name`), **name** (EN / RU, lowercase in sentences), **icon** (halo icon set, 24 dp, 1.75 stroke), **shortcut**, **group** (which menu / panel), **what it does** (one sentence), **parameters and ranges**, **levels** (E / S / P), **states**, and a **hard-stop** flag.

| Tool | Instruments | Main groups |
|---|---|---|
| halo studio | 22 | Select · Paint · Colour · Draw · Edit · Image · Layers · View |
| halo notes | 20 | Home · Insert · Layout · Review · View · Tools · File |
| halo sheets | 16 | Grid · Edit · Format · Data · Insert · View · Tools · File |
| halo canvas | 15 | Edit · View · Insert · Arrange · Review |
| halo scan | 12 | Capture · Page · Text · View · File |
| halo record | 12 | Transport · Markers · Text · Edit · Share · File |
| halo planner | 11 | View · Go · Edit · Event · Tasks · File |
| halo study | 10 | Course · Learn · Help · Try · File |
| halo memory | 9 | Write · View · Find · Privacy · File |
| halo album | 11 | Browse · View · Edit · Share |
| halo vault | 9 | Lock · Add · Browse · View · Edit · Share |

Levels: an instrument marked E is in all three levels. Standard shows the instruments that fit in one tool row plus sheets; Pro shows everything. Full rows are in `dev/INSTRUMENTS_9.5.md`.

## 34. Building one instrument (board 79)
Spec fields as above, plus six **states** that every instrument draws: Idle · Hover · Selected (tool tint + 3 dp edge) · Working (mint) · Disabled (35 %) · Keyboard focus (lilac ring).
**Name tag:** name + shortcut + one line, after 500 ms hover or long-press, never under the finger. Easy view always shows names.
**Done means:** matches the list · six states · ranges enforced · read-aloud label EN + RU · works by touch, pen, mouse, keys · one undo step per action · motion from tokens only · appears in the right levels · hard stops show the confirm sheet.

## 35. Motion inside the tools (board 80)
| Moment | Spec | Compose |
|---|---|---|
| Menu opens | 160 ms enter, drop 4 dp + fade | `menuEnter()` |
| Panel folds | 240 ms standard, width → 0, work area grows | `panelEnter() / panelExit()` |
| Tool switch | selected indicator on soft spring + haptic tick | `animateToolIndicator()` |
| Easy ↔ Pro | 360 ms cross-fade of controls; document fixed | `LevelCrossfade()` |
| Sheet rises | 360 ms enter, scrim 40 % | `ModalBottomSheet` |
| Undo | 240 ms mint outline on the changed area + toast "Undone: …" for 3 s | `UNDO_TOAST_MS` |
| Saved chip | 160 ms Saving… → mint Saved | `AnimatedContent` |
| Page / frame | 360 ms, 24 dp slide + fade, from the side the page lives on | `pageEnter(forward)` |

Gentle and Still follow 9.0: Gentle ×1.3, moves ≤ 4 dp, no scale; Still = 150 ms fades only (enforced in `HaloMotion.dur`). The CSS preview respects `prefers-reduced-motion`.

## 36. Guidance (board 81)
- **First tour:** 5 steps per tool, shown once: title bar, menus, the main instrument, panels, Ask halo. Spotlight + one lilac card with one sentence and Next. Skip any time; replay from Help.
- **Where is…? (Ctrl / or long-press the ring):** type or say a word; results show the menu path and shortcut, then *Show me* and *Watch, then try*.
- **Name tags** on every instrument (see 34).
- **Show me:** spotlight + arrow on the real control; halo waits until you do it, then says "Got it" in mint.
- **Guided tasks:** e.g. "Make your first table" — 4 checked steps, resumable.
- **Gentle nudges:** after 3 tries at the same thing, one lilac hint. Can be turned off.
Rules: guidance is always lilac, sits beside the work, one sentence per step, read aloud available, same in all tools.

## 37. Articles with their pictures (board 82)
- Article view: source + time + reading time · hero picture with "1 / 9" · headline · **In short · by halo** (marked as halo's summary) · **Pictures from the article** strip with "See all".
- **Viewer:** black background, swipe, pinch / double-tap zoom, dots, caption (15 sp) and credit ("Photo: name / source · from the article") always visible; Save keeps the credit and link.
- **All pictures:** a 3-column grid in article order.
- **Home · Your sources:** cards show the lead picture and a small strip of the others.
- **Rules:** only images that are in the article, in order, with credit; illustrations labelled; nothing generated is passed off as a photo; small images first on mobile data; a setting hides pictures; disturbing images blurred with "Show"; sharing to a person is a hard stop; "Your day" shows no news unless turned on.

## 38. Your day in halo (board 83)
- **When:** ready at 21:00, or ask "How was my day?". Tabs: Today · Yesterday · This week.
- **Words first:** 2–3 plain sentences: what you did, what went well, what still needs you. Mint words for done, Apricot for "needs you". No scores, no streaks, nothing red.
- **Cards underneath (only when there is something to say):** Hour by hour (timeline) · Where you went (if places are on) · Photos you took · Focus & screen (if on) · Needs you · Tomorrow. Each card: "Why am I seeing this?" and "Hide this card".
- **Actions:** Read it to me · Keep (saves to memory). Share asks first.
- **Your choice:** Plans and ticks, Messages that need you, Photos — on. Where you went, Focus and screen time, News — off by default.
- **Privacy line:** "Made on this phone · only you see it".

## 39. Dynamic cards (boards 84–86)
**Twelve answer cards:** Fact · Steps · Compare · Timeline · Place · Pictures · Quick check · Flip · Chart · Checklist · Word · Plan (also Label it and Explain back for Study).
**Rules:** plain answer first; at most 3 cards; every card has a title, one body, a source, Read aloud and "+" (Keep, Study, Planner, Kids); tap to open full size; swipe between cards; if halo is not sure, the card says so and offers to check.
**Kids (board 85):** white cards, radius 26, a picture always, one sentence, 16–24 sp, 52 dp buttons. Types: Did you know? (with a sound), Try it (pictures as answers), Count with me, Tiny story, My cards shelf. A right answer lights a star and says why; a wrong tap says "Let's look again" — no red, no buzzer, no points. Topics and sources set by a grown-up; no ads, links or search.
**Study (board 86):** cards inside the five steps — Look: timeline, label it; Try: worked example (steps reveal one at a time), quick check, lilac help; Keep: explain back ("say it your way"), then keep cards into spaced repeats. Any answer card → "+ Study". Three sizes: Pictures, Simple, Full.

## 40. Building cards (board 87)
- **Chooser:** single number → Fact · how-to → Steps · options → Compare · dates → Timeline · where → Place · looks like → Pictures · check yourself → Quick check · a term → Word / Flip · numbers over time → Chart · things to tick → Checklist · something to schedule → Plan · parts of a picture → Label it.
- **Anatomy:** type line (icon + caps) · title Sora 17 sp, ≤ 2 lines · body · source · actions; radius 22, padding 14, targets ≥ 48 dp.
- **Motion:** cards appear 360 ms after the words, 60 ms apart; Fact numbers count up once; Flip 420 ms (Still: cross-fade); Quick check right = mint fill 240 ms, try again = lilac outline; Steps tick like widgets.
- **Data:** `dev/halo-card-schema.json`; Compose shell in `dev/DynamicCards.kt` (one `HaloCard` + a body per type).
- **Test each type with:** long text, no picture, offline, Still motion, largest text size, Russian strings, Kids size, screen reader.

## 41. Acceptance
1. Every instrument in the app exists in the list with the same name, icon, shortcut and levels.
2. Tool motion uses only tokens; Still removes slides and scale.
3. Every tool has the first tour, Where is…?, name tags and Show me.
4. Article galleries contain only the article's images, with credit.
5. Your day starts with plain sentences; off-by-default items stay off until the person turns them on.
6. No card uses red for a wrong answer; Kids cards always have a picture and read-aloud.

## Still open
- Confirm the 9.0 Pro decision (tool menus at the top on big screens).
- Rights check for showing article images in a gallery, per source — owner / legal decision.
- Kids-safe sources for cards and "What is this?" — owner decision.
- Everything is unverified on device.
