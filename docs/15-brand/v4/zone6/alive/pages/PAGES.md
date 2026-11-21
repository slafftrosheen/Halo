# halo · the alive layer on every page and tool — pack 7.3 · boards 39–46

Baseline repo `98aa2fb`. Extends 7.2 (`alive/ALIVE.md`). Design + reference code; **not compiled, not built or tested on a phone.**

## The same five things on every surface
| Thing | Where it lives | Rule |
|---|---|---|
| **Edit this page** | More › Edit this page, or long-press the page title | Live preview, dock = Undo · Apply/Done, logged in “Put back how it was” |
| **Motion** | follows Full · Gentle · Still | one motion per change (7.2 rules) |
| **Show me** | Help › Show me (any page) | lilac numbered tags from each control’s own label; “Read all” |
| **Watch, then try** | first 3 uses of a gesture on that page | spotlight + hand + Again · Do it for me · Got it |
| **Easy view** | global (Make it yours › Text & size, or first-day setup) | fewer, bigger, named buttons; nothing removed — the rest is in More |
Plus **tours** in Help & check-up: Home and the dock · Talking to halo · HaloStudio · HaloNotes · HaloCanvas · Learn (1–2 min, on the real screen).

## Page by page (board 39 is the map)
### Main
- **Home** (b40): Edit Home = up to 4 starts (drag, remove, add: Call Anna, Timer, Weather, Read my brief, Draw…), greeting line Day + weather / Just the day / Off. Easy = the 4 starts as big buttons. Tags: 5. Hand: hold ring, swipe dock.
- **Workspace**: swap a tool into the dock slot (7.2 Dock & buttons). Show me names each tool by what it does (“Make pictures & posters”, “Write notes & lists”…). Easy = tools as 4 big tiles.
- **Activity**: group by day / by app. Easy = one big card per item; Apricot only for “Waiting for you”; buttons name the action (“Look and send”).
- **Places**: Arrange = reorder + hide (eye). Hidden places stay in search and in “ask halo”. Easy = first 4 as big buttons.
### Places pages
- **Library** (b41): List / Big pictures, sort Newest · Name · Tool, search you can say. Easy = Big pictures.
- **HaloMemory**: one-time lilac explainer (“halo suggests, you decide”); every line a plain sentence + one big button Forget / Remember.
- **Widgets & sources**: Add a widget = preview on your real wallpaper in your look; Size Small / Wide / Tall; motion switch; Match my look.
- **Devices**: Add a device = 3 numbered, pictured steps, each read aloud; “Find it” filled.
- **Settings** (b42): search box that accepts speech (“bigger text” → Text size, Easy view with Go). Easy = 6 buttons: Text size, Read aloud, Volume, Who helps me, Night, All settings.
- **Help & check-up**: “Show me around” tours with seen/replay; tours run on the real page (step N of M, Back · Next).
### Tools
- **HaloStudio** (b43): Show me (artboard, Add, Draw, Layers, More); first drag/pinch demo on the real artboard; Easy = Draw · Words · Picture · Undo (84dp) + bigger artboard; Your Studio = 4 favourite tools in the dock, artboard Plain/Dots/Grid, left-handed, snap bounce by motion level, “Name tools while I draw”.
- **HaloNotes** (b44): ring caption “Dictate”; hold-to-dictate demo; Easy = 21sp text, 50dp check rows, read-back with lilac highlight + Slower; Your notes = paper Plain/Lined/Grid, letters Easy-read (Atkinson)/Book/Round, spacing Normal/Roomy/Extra, dictate + read-back switches. Never “block”, “markdown”, “rich text”.
- **HaloCanvas** (b45): drag-to-link demo (dotted lilac path); **board as a list** (cards in order, links read “linked to …”) — default in Easy view and with TalkBack; Your boards = card colours By kind/By me/One colour (images pink, notes apricot, lessons mint, ideas lilac), links Curved/Straight/Dotted, snap to grid, open as list.
- **HaloStudy** (b45): How you learn = size Pictures/Simple/Full (7.1), reading highlight Mint/Lilac/Sun/Underline, easy-read spacing, practice reminder 18:00 (only when cards are due), reading voice.
- **HaloMemory tool** = the Places page above.
### Everywhere else
- **Assistant island** (b46): follows Easy view → 23sp answer, read aloud with lilac word highlight, 64dp buttons naming the action.
- **mini halo**: bubble Small/Medium/Large, left/right, hide during full-screen video and games. Lowercase.
- **Kids**: child picks one of 4 gentle skies (Stars, Sea, Garden, Moon; half speed, never flashing), tap = hear, tap again = pick. Grown-up (phone lock) sets: allowed skies, motion (Gentle max), Learn size, hand demos, read-aloud. No tips or stuck detector in Kids.

## Show me tag sources
Tags read the control’s existing `contentDescription` / label, rewritten as “what it does” strings in `strings_pages.xml` (`showme_*`). If a control has no description, it gets no tag — and that is a bug to fix in the control, not in the guide.

## Acceptance (adds to 7.2)
- Every page has Edit this page, Show me and an Easy layout; every tool has its own make-it-yours.
- Every gesture-only action has a non-gesture alternative (Canvas connect mode / list view, Studio Add sheet, Notes Insert).
- Hidden Places remain reachable by search and by asking halo.
- Tours point at real controls and can be left at any step.
- Kids: no tips, no stuck detector; customisation behind the phone lock except the sky picker.
