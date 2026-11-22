# halo 9.5 · Instrument lists for developers

Generated from one source (ins.js). Levels: E = Easy, S = Standard, P = Pro. Hard stop = must show the confirm sheet before acting.

## halo studio (22)

| Instrument | Key | Group | What it does | Parameters / ranges | Levels | States |
|---|---|---|---|---|---|---|
| Move | V | Select | Moves the selected layer or selection. | Snap to edges on/off; nudge 1 px (arrows), 10 px (⇧ arrows) | SP | idle · dragging · snapping |
| Rectangle / ellipse select | M | Select | Draws a selection box or oval. | Add ⇧ · Subtract ⌥ · Feather 0–200 px | P | drawing · active (marching dashes) |
| Lasso | L | Select | Freehand or polygon selection. | Mode freehand / polygon · Feather 0–200 px | P | drawing · closed |
| Magic wand | W | Select | Selects similar colour by click. | Tolerance 0–100 · Contiguous on/off · Sample all layers | P | — |
| Select subject | — | Select | halo finds the main subject and selects it. | Edge refine 0–10 | SP | working (mint) · done |
| Brush | B | Paint | Paints with the current preset. | Size 1–1000 px · Opacity 0–100 % · Flow 0–100 % · Smooth 0–100 % · Pressure → size / opacity | ESP | idle · painting · stabilising |
| Pencil | N | Paint | Hard-edged line, no smoothing of edges. | Size 1–200 px · Opacity | SP | — |
| Eraser | E | Paint | Removes paint on the current layer. | Size · Hardness 0–100 % · Opacity · Uses brush preset | ESP | — |
| Smudge / blend | R | Paint | Pushes and blends existing paint. | Strength 0–100 % · Wet mix on/off | SP | — |
| Fill | G | Paint | Fills an area with colour or gradient. | Tolerance 0–100 · Gradient linear / radial · Gap close 0–20 px | SP | — |
| Eyedropper | I | Colour | Picks a colour from the picture. | Sample 1 px / 3×3 / 5×5 · Current layer / all | ESP | picking (loupe) |
| Colour wheel & triangle | — | Colour | Choose hue on the ring, tone in the triangle. | HSV + HEX + RGB fields; recent swatches (8) | SP | — |
| Shapes | U | Draw | Lines, rectangles, ellipses, polygons as editable vector shapes. | Stroke 0–100 px · Fill on/off · Corner 0–200 px · Sides 3–12 | SP | editing handles |
| Text | T | Draw | Adds editable text on its own layer. | Font · Size 6–400 pt · Weight · Align · Line height | SP | editing caret |
| Transform | Ctrl T | Edit | Scale, rotate, skew, warp the layer or selection. | Keep proportions (⇧ to free) · Pivot · Warp grid 3×3 | P | handles shown · applying |
| Crop | C | Image | Crops the whole picture. | Ratio free / 1:1 / 4:5 / 16:9 / A4 · Straighten ±45° | ESP | — |
| Layers panel | F7 | Layers | New, duplicate, group, merge, mask, lock, hide. | Opacity 0–100 % · Blend: normal, multiply, screen, overlay, soft light, colour | SP | selected · hidden · locked |
| Symmetry | — | Paint | Mirrors strokes across one or more axes. | Vertical / horizontal / radial 2–12 | P | guides shown |
| Reference image | — | View | Pins a picture in a floating window to look at. | Opacity · always on top | P | — |
| History | Ctrl Alt Z | Edit | List of the last 100 steps; tap one to go back. | 100 steps · snapshots | P | — |
| Quick palette | long-press | Colour | Round palette at the brush: 8 colours, 6 brushes. | Customise slots | SP | open · closing |
| Fix the light (halo) | — | Image | halo suggests light and colour; result goes on a new layer. | Strength 0–100 % | ESP | working · done · undo |

## halo notes (20)

| Instrument | Key | Group | What it does | Parameters / ranges | Levels | States |
|---|---|---|---|---|---|---|
| Paragraph styles | Ctrl Alt 1–4 | Home | Body, Title, Heading 1–3, Quote, List. | Edit a style updates every use | ESP | current style highlighted |
| Font & size | — | Home | Font family and size for selected text. | Size 8–72 pt; user fonts + Google Fonts | SP | mixed (—) |
| Bold / italic / underline | Ctrl B / I / U | Home | Text emphasis. | — | ESP | on · off · mixed |
| Highlight | Ctrl ⇧ H | Home | Marks text with a colour. | 6 highlight colours (none are state colours) | SP | — |
| Lists | Ctrl ⇧ 7 / 8 | Home | Bulleted and numbered lists, indent with Tab. | Bullet style · numbering 1. / a. / i. | ESP | — |
| Align & spacing | Ctrl L / E / R / J | Home | Left, centre, right, justify; line spacing. | Spacing 1.0–3.0 | SP | — |
| Insert picture | — | Insert | Adds a photo from album, scan or files. | Wrap inline / square / behind; size handles | SP | placing |
| Table | — | Insert | Inserts a table; Tab moves cell to cell. | 1–20 × 1–50; header row on/off | SP | — |
| Link | Ctrl K | Insert | Links text to a web page or a memory page. | URL or [[page]] | SP | — |
| Header, footer, page numbers | — | Insert | Repeating top/bottom text and numbers. | Position · start at | P | — |
| Page setup | — | Layout | Page size, margins, orientation, columns. | A4 / Letter / A5 · margins 0.5–5 cm · 1–3 columns | P | — |
| Comments | Ctrl Alt M | Review | Notes in the margin with initials; reply, resolve. | — | SP | open · resolved |
| Track changes | Ctrl ⇧ E | Review | Shows insertions (green) and deletions (struck). | Accept / Reject one or all | P | on · off |
| Spelling & easier words | F7 | Review | Spelling, grammar and plainer alternatives. | Language per document | ESP | checking · n found |
| Find & replace | Ctrl F / H | Edit | Find text; replace one or all. | Match case · whole word | SP | — |
| Outline | — | View | Headings list; drag to reorder sections. | — | P | — |
| Read aloud | Ctrl ⇧ Space | Tools | Reads from the caret with a lilac highlight. | Speed 0.5–2× · voice | ESP | reading · paused |
| Dictate | Ctrl ⇧ D | Tools | Type by speaking; says “full stop”, “new line”. | Language | ESP | listening (mint) |
| Templates | — | File | Letter, CV, recipe card, meeting notes, invitation. | — | ESP | — |
| Export | Ctrl ⇧ S | File | DOCX, ODT, PDF, plain text; print. | Page range · include comments | SP | exporting · done |

## halo sheets (16)

| Instrument | Key | Group | What it does | Parameters / ranges | Levels | States |
|---|---|---|---|---|---|---|
| Cell select & range | arrows / ⇧ | Grid | Select a cell, range, row or column. | Ctrl A all · Ctrl Space column · ⇧ Space row | ESP | active cell outline (sand) |
| Formula bar | F2 | Grid | Shows and edits the formula of the active cell. | Function hints; colour-matched ranges | SP | editing · error |
| Ask in words | Ctrl Space | Grid | Writes a formula from a sentence, explains it. | Preview before insert | ESP | listening · suggestion |
| Fill down / series | Ctrl D | Edit | Copies or continues a pattern down or across. | Copy / series / dates | SP | — |
| Number format | Ctrl ⇧ 1–5 | Format | Number, currency, date, time, percent. | Decimals 0–6 · currency symbol | SP | — |
| Sort | — | Data | Sort rows by one or more columns. | A→Z / Z→A · keep header | ESP | — |
| Filter | Ctrl ⇧ L | Data | Show only rows that match. | By value, text, number range, colour | SP | active (icon tinted) |
| Colour rules | — | Format | Colours cells by a rule. | Above / below / between / top n / duplicates | P | — |
| Charts | — | Insert | Bar, line, pie, area from the selection. | Title · axes · legend · colours | SP | floating · selected |
| Freeze rows & columns | — | View | Keeps headers visible while scrolling. | Rows 0–5 · columns 0–3 | P | — |
| Add up (button) | Alt = | Easy | Adds a total row or column. | — | ESP | — |
| Explain this sheet | — | Tools | Plain-language summary of what the sheet does. | — | ESP | working · done |
| Check entries | — | Data | Drop-down lists and allowed values. | List / number range / date | P | invalid (Apricot outline) |
| Remove duplicates ⚠︎ hard stop | — | Data | Removes repeated rows (asks first). | Columns to compare | P | — |
| Sheet tabs | Ctrl PgUp/PgDn | Grid | Add, rename, colour, reorder sheets. | — | SP | — |
| Import / export | — | File | XLSX, CSV, ODS in; XLSX, CSV, PDF out. | Separator · encoding | SP | — |

## halo canvas (15)

| Instrument | Key | Group | What it does | Parameters / ranges | Levels | States |
|---|---|---|---|---|---|---|
| Select | V | Edit | Select, move, multi-select by box. | Snap to grid 8 · guides | ESP | selected (canvas blue outline) |
| Hand / pan | Space + drag | View | Moves around the board. | — | ESP | — |
| Sticky note | N | Insert | Coloured note; type straight away. | 6 colours · auto-size text | ESP | editing |
| Shape | R / O | Insert | Rectangle, ellipse, diamond, arrow block. | Fill · outline 0–12 · corner 0–60 | SP | — |
| Text | T | Insert | Free text block. | Sora / Atkinson · 12–96 | SP | — |
| Connector | L | Insert | Line that sticks to shapes. | Straight / elbow / curved · arrow ends | SP | attached (dot) · loose |
| Pen & highlighter | P | Insert | Freehand drawing. | Width 1–24 · smoothing | SP | — |
| Picture & file | — | Insert | Adds photos, PDFs (first page), links as cards. | — | SP | — |
| Frame | F | Insert | A page of the board; group and present. | Presets: free, A4, 16:9, phone | SP | — |
| Align & distribute | Alt A | Arrange | Lines up selected objects. | Left / centre / right / top / middle / bottom | P | — |
| Group & lock | Ctrl G / Ctrl ⇧ L | Arrange | Groups objects; locks them in place. | — | P | locked (padlock) |
| Properties panel | — | Arrange | Edits fill, outline, text, size, position. | Shows only shared properties for multi-select | P | — |
| Minimap | M | View | Overview and jump. | — | P | — |
| Present | F5 | View | Shows frames one by one, full screen. | — | SP | presenting |
| Comments | C | Review | Pin a comment to a spot. | — | SP | open · resolved |

## halo scan (12)

| Instrument | Key | Group | What it does | Parameters / ranges | Levels | States |
|---|---|---|---|---|---|---|
| Capture | Space | Capture | Takes a page; auto-capture when steady. | Auto on/off · flash | ESP | finding edges · hold still · captured |
| Import photo | — | Capture | Uses an existing photo. | — | SP | — |
| Edges | — | Page | Four corner handles, found automatically. | Magnifier loupe while dragging | SP | auto · adjusted |
| Rotate & reorder | R | Page | Turns pages; drag strip to reorder. | 90° steps | SP | — |
| Filter | — | Page | Original, Clean paper, Grey, Black & white. | Brightness · contrast | SP | — |
| Read it to me | — | Text | Reads the found text aloud. | Speed · voice | ESP | reading |
| Found text | — | Text | Text recognised on the phone when possible. | Languages · copy | SP | reading · ready |
| Translate | — | Text | Translation over the real words. | Target language | ESP | — |
| Fill a form | — | Text | Finds fields; halo suggests answers you confirm. | — | P | suggested · confirmed |
| Sign | — | Text | Adds your saved signature. | Size · place | SP | — |
| Magnifier | — | View | Live lens up to 6×. | Zoom 1–6× · high contrast · freeze | ESP | — |
| Save PDF | Ctrl S | File | One PDF with selectable text. | Size small / normal / best | ESP | saving · done |

## halo record (12)

| Instrument | Key | Group | What it does | Parameters / ranges | Levels | States |
|---|---|---|---|---|---|---|
| Record / pause | Space | Transport | Starts and pauses recording. | Quality voice / music · on this phone | ESP | recording (mint dot + timer) · paused |
| Play / scrub | Space · J K L | Transport | Plays; drag the waveform to move. | Back / forward 10 s | ESP | playing |
| Speed | < > | Transport | Playback speed. | 0.5–2× in 0.25 steps | SP | — |
| Marker | M | Markers | Named marker at the current time. | Name · colour (tool tint) | SP | — |
| Chapters | — | Markers | halo splits the recording into topics. | Rename · merge | SP | working · ready |
| Transcript | — | Text | Words with times; tap to play from there. | Languages · show times | ESP | live · final |
| Speakers | — | Text | Who said what, by initials. | Rename speakers | SP | — |
| Find in transcript | Ctrl F | Text | Finds words, jumps the audio. | — | SP | — |
| In short | — | Text | Summary with To do and Decided. | Length short / normal | ESP | working · ready |
| Trim | — | Edit | Cuts the start or end. | Handles on waveform | P | — |
| Send to planner | — | Share | To-dos become reminders. | — | SP | — |
| Export | — | File | Audio, text, subtitles. | M4A / WAV · TXT / DOCX · SRT | SP | — |

## halo planner (11)

| Instrument | Key | Group | What it does | Parameters / ranges | Levels | States |
|---|---|---|---|---|---|---|
| Views | 1 / 2 / 3 / 4 | View | Day, Week, Month, Agenda. | Weekends on/off | ESP | — |
| Today / back / next | T · ← → | Go | Moves through time. | — | ESP | — |
| New event | Ctrl N | Edit | Title, time, place, repeat, reminder. | Repeat daily / weekly / monthly / custom | ESP | draft · saved |
| Add by talking | Ctrl Space | Edit | Sentence → check card → event. | Shows how it understood each part | ESP | listening · check |
| Drag to move / resize | — | Edit | Drag an event; pull its edge to change length. | Snap 15 min | SP | dragging |
| Leave-by time | — | Event | Travel time from where you are. | Walk / transit / car | SP | — |
| Calendars | — | View | Me, Family space, others; colour and show/hide. | — | SP | — |
| Tasks | — | Tasks | Checklist with dates; ticks turn mint. | Due date · repeat | ESP | open · done |
| Mini month | — | View | Jump to any date. | — | P | — |
| Search | Ctrl F | Go | Finds events by words. | — | SP | — |
| Import / export | — | File | Standard calendar files (.ics). | — | P | — |

## halo study (10)

| Instrument | Key | Group | What it does | Parameters / ranges | Levels | States |
|---|---|---|---|---|---|---|
| Course outline | — | Course | Lessons with done / now. | Reorder | SP | done · now · later |
| Lesson view | — | Learn | Look step: text, pictures, audio. | Size Pictures / Simple / Full | ESP | — |
| Flashcards | Space flip | Learn | Front, back, hear it. | Again / Hard / Good / Easy | ESP | front · back |
| Quiz | — | Learn | Choose, type, match, order. | Number of questions 5–30 | SP | answering · checked |
| Hint | H | Help | Lilac hint, slower audio, an example. | — | ESP | — |
| Hear it | — | Help | Plays the word or sentence. | Speed 0.5–1× | ESP | — |
| Say it | — | Try | Speak and get gentle feedback. | — | SP | listening |
| Make cards | — | Course | From notes, a scan or a recording. | Keep / skip each suggestion | SP | — |
| Dynamic cards | — | Learn | Answer and lesson cards (see board 85). | — | ESP | — |
| Import / export | — | File | Cards as CSV. | — | P | — |

## halo memory (9)

| Instrument | Key | Group | What it does | Parameters / ranges | Levels | States |
|---|---|---|---|---|---|---|
| Page | Ctrl N | Write | A page with text, photos, voice notes. | — | ESP | — |
| Link | [[ | Write | Links to another page; creates it if new. | — | SP | — |
| Tag | # | Write | Groups pages without folders. | — | SP | — |
| Backlinks | — | View | “Linked here from” at the bottom. | — | SP | — |
| Link map | — | View | Pages as dots, links as lines. | Depth 1–3 | P | — |
| Timeline | — | View | Pages by the date they are about. | — | SP | — |
| Search | Ctrl F | Find | Words, tags, dates. | — | ESP | — |
| Ask before using | — | Privacy | halo asks before using a page in an answer. | Always / ask / never per page | ESP | — |
| Export | — | File | Page or all pages as text / PDF. | — | P | — |

## halo album (11)

| Instrument | Key | Group | What it does | Parameters / ranges | Levels | States |
|---|---|---|---|---|---|---|
| Library sidebar | — | Browse | All, Favourites, People, Places, Albums, Stories, Deleted. | — | SP | — |
| Search by memory | Ctrl F | Browse | “Gran’s birthday last summer”. | On this phone | ESP | searching · n found |
| Grid zoom | + / − | View | Days / months / years; 2–8 per row. | — | SP | — |
| Select | tap / ⇧ | Browse | Ticks; drag to select many. | — | SP | n selected |
| Adjust | — | Edit | Light, colour, warmth, straighten. | −100 to +100 · straighten ±45° | SP | editing |
| Crop | C | Edit | Crop and ratio. | Free / 1:1 / 4:5 / 16:9 | ESP | — |
| Compare | hold | Edit | Shows the original while held. | — | SP | — |
| Revert | — | Edit | Back to the original at any time. | — | SP | — |
| Make a story | — | Share | Best photos, title, soft music. | Length 15–60 s | ESP | working · ready |
| Info | I | View | Date, place, people, camera. | — | P | — |
| Share ⚠︎ hard stop | — | Share | Always asks first (hard stop). | — | ESP | asking · sent |

## halo vault (9)

| Instrument | Key | Group | What it does | Parameters / ranges | Levels | States |
|---|---|---|---|---|---|---|
| Unlock | — | Lock | Fingerprint or PIN; locks after 1 min away. | Timeout 30 s–5 min | ESP | locked · open |
| Scan a paper | — | Add | Photo → halo fills What / Bought / Ends / Remind. | Shows where each value was read | ESP | reading · check |
| Import file | — | Add | PDFs and photos from files. | — | SP | — |
| Folders | — | Browse | Home papers, Insurance, Warranties, IDs, Taxes. | Rename · colour | SP | — |
| List / grid | — | View | Columns Name, Type, Ends, Remind. | Sort any column | SP | — |
| Preview & details | Space | View | See the paper and its facts. | — | SP | — |
| Reminder | — | Edit | Remind before a paper ends. | 1 week / 1 month / custom | ESP | — |
| Search | Ctrl F | Browse | Names and text inside papers. | — | SP | — |
| Share a copy ⚠︎ hard stop | — | Share | Hard stop: asks first; shares a copy only. | Expires after 1–7 days | P | asking · sent |
