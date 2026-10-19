# halo Learning — Kids + Study · UX/engineering handoff

learning-1.0 · DevKit v1.3 · BrandBook v2.2 · contract v1 (2026-10-18)  
Baseline inspected: github.com/slafftrosheen/Halo @ 97c82273d1fe274a05f824d3b256361b4da0b506 · DevKit v1.2 · BrandBook v2.1

Picsart owns the visual/interaction design (this folder + BrandBook ch.12). Codex owns Android implementation, learning orchestration, data and providers. Implement against the **screen IDs, events and states below**, not against the look of a static comp. Copy in comps is illustrative and localisable.

Open `prototype/index.html` through a local server (`python3 -m http.server` in this folder) to click through journeys A–D with each screen's contract next to it.

## 1. Shared contract

| Stage | Label | Meaning |
|---|---|---|
| `START` | Start | Pick what to learn |
| `DISCOVER` | Discover | What do you already know? |
| `EXPLAIN` | Explain | One idea at a time |
| `EXPLORE` | Explore | Follow a branch deeper |
| `PRACTICE` | Practice | Try it yourself |
| `REFLECT` | Reflect | What I learned, what's next |
| `CONTINUE` | Continue | Pick up later where you left |

Codex owns the LearningStage enum. Today the app has StudyLearningPhase EXPLORE/TRY/REVIEW/COMPLETE; proposed mapping: START+DISCOVER→(new), EXPLAIN+EXPLORE→EXPLORE, PRACTICE→TRY, REFLECT→REVIEW, CONTINUE→COMPLETE/resume. The UI only reads `stage` + `nextAction`; it never infers stage from the transcript.

### Rules every screen follows
- One recommended action at a time (Intent moves, never multiplies).
- Motion is never the only signal: every cue has text, speech and a TalkBack label.
- Ring shows the REAL runtime state; no listening animation while the mic is closed.
- Stop cancels TTS, generation, capture and pending FX in the same frame; no late success after cancel.
- Intent never on cloud opt-in, purchase, new permission or destructive action.
- Bloom only after a CHECKED independent success; never for time spent.
- Kids touch targets ≥ 56dp, mic 72dp; adults ≥ 48dp; max 3 choices per kids screen.
- Reduced motion: static SVG of the same state, no movement.

## 2. Existing → proposed screen map

| ID | Proposed | Today in the app (source) | Capability |
|---|---|---|---|
| **K00** | Kids home | KidsModeScreen + KidsHomeChoices: Signal Face, Ask me anything / Learn / Create, Stop/options. | native |
| **K10** | Explore start | Kids LEARN entry: topic field + voice, Maths/Reading chips, up to two resumable sessions. | native (speech-to-text model must be installed; else typed + tiles) |
| **K11** | Discover — what do you know? | Not present: lesson starts with a tutor explanation. | native |
| **K12** | Learning focus | KidsLesson: latest tutor turn, read-along/TTS, answer draft, attempt, question, next, finish, completion bloom. | native (tutor: local model; narration needs a TTS voice) |
| **K13** | Topic map | Not present. | native |
| **K14** | What I learned | Lesson completed + bloom. | native |
| **H20** | Homework source | Study home has file import + local extraction review; camera still source exists in host-android. | native camera + local OCR; cloud vision is provider-dependent and needs consent |
| **H21** | Check the exercises | Study extraction review entry (adult). | native extraction; quality depends on the installed vision model |
| **H22** | One exercise | KidsLesson / StudyScreen attempt + guided advance. | native |
| **H23** | Why it works | Feedback inside lesson transcript. | native |
| **C30** | What shall we make? | KidsCreationPanel: idea field + Write a story / Invent a character. | native text; drawing generation provider-dependent |
| **C31** | Shape the idea | Not present (one-shot text generation). | native |
| **C32** | Make it | Assistant text generation in home.submitText; result below. | text native; images provider-dependent |
| **C33** | Keep, change or discard | Not present (auto-shown result). | native |
| **C34** | My project | Not present. | native; export/share depends on Android share + grown-up PIN (verify) |
| **S40** | Study home | StudyHomeScreen: continue sessions, review due, new lesson Goal → Material → Style → Options, Notebook picker, file import. | native |
| **S41** | Guided start | 4-step wizard Goal → Material → Style → Options. | native |
| **S42** | Active study | StudyScreen: tutor turns, composer, attempt, guided advance, citations, reader, cards, save summary, menus. | native |
| **S43** | Lesson details | Details sheet tabs: Material, Languages, Setup, Schedule, Progress. | native |
| **S44** | Review & keep | Focused review: check/reveal/skip/source. | native |
| **G90** | Global layer | Kids header stop/options, My tools sheet, Full Halo exit. | native |

## 3. Interaction specification per screen

Format: user action → halo event → new view state. `Stop` (G90) is available everywhere and cancels TTS, generation, capture and pending FX in the same frame.

### K00 · Kids home  
*kids · stage START*

**Hierarchy:** Halo Ring hero (face idle glyph) · greeting · 2 big tiles Explore / Create · Resume tile when a lesson or project is open · voice dock.

**Entry:** App opens in Kids mode; Return from any Kids flow (Back / Done); Stop from any screen returns here only if the user taps Home

| User action | halo event | New view |
|---|---|---|
| Tap Explore / say 'let's learn' | `learning.intent(explore)` | K10 |
| Tap Create / say 'let's make' | `create.intent` | C30 |
| Tap Resume | `learning.resume(id) | create.resume(id)` | K12 | H22 | C32 |
| Tap mic, speak a question | `voice.open → transcript` | K10 with topic | K12 (direct answer lesson) |
| Long-press lock (grown-up) | `gate.request` | G90 grown-up |

**Components:** `CMP-RING`, `CMP-CHOICE`, `CMP-DOCK`, `CMP-WHERE`, `CMP-GROWNUP`  
**Real states to design for:** idle, listening, thinking, speaking, offline, resume available

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| screen shown, runtime idle | `halo_kids_orb_idle` | Ring 160dp · 4 s breath loop | app background / Stop | kids/orb_svg idle | Intent on mic only when there is no Resume; otherwise on Resume tile |
| mic opened (real audio focus granted) | `HaloRipple + halo_kids_orb_listening` | Ring + dock mic | mic closed / Stop / barge-in | static listening svg + 'Listening' caption | — |

**Accessibility:** Tiles: 'Explore. Learn something new.' / 'Create. Make a story, character or project.' · Mic: 'Talk to halo' ⇄ 'Stop listening' · Focus order: Ring status → Resume → Explore → Create → mic → grown-up

**Capability:** native

### K10 · Explore start  
*kids · stage START*

**Hierarchy:** Prompt 'What do you want to find out?' · 4 starting tiles: Speak · Homework photo · My notes · Keep going · recent topics row.

**Entry:** K00 Explore; Voice question from K00

| User action | halo event | New view |
|---|---|---|
| Speak a topic | `voice.transcript → learning.start(topic)` | K11 |
| Homework photo | `homework.start` | H20 |
| My notes (Notebook / file) | `source.pick(notebook|file)` | K11 with source |
| Keep going | `learning.resume` | K12 |
| Back | `nav.back` | K00 |

**Components:** `CMP-DOCK`, `CMP-CHOICE`, `CMP-WHERE`, `CMP-INTENT`  
**Real states to design for:** idle, listening, transcribing, offline (speech unavailable → tiles + type)

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| screen shown | `trace lap → pulse on mic` | Dock mic 72dp | mic opened / tile tapped | static accent outline | Mic |

**Accessibility:** No typing needed; Type is a secondary icon button 'Type instead' · Recent topic chips read as 'Continue black holes, step 3 of 7'

**Capability:** native (speech-to-text model must be installed; else typed + tiles)

### K11 · Discover — what do you know?  
*kids · stage DISCOVER*

**Hierarchy:** One friendly question in the centre · Ring listening cue · 3 tap answers (I know a lot / A little / Not yet) · 'I don't know' is always valid.

**Entry:** K10 topic chosen

| User action | halo event | New view |
|---|---|---|
| Answer by voice or tap | `learning.priorKnowledge(level, text)` | K12 |
| Skip | `learning.priorKnowledge(unknown)` | K12 |
| Back | `nav.back` | K10 |

**Components:** `CMP-RING`, `CMP-FOCUS`, `CMP-CHOICE`, `CMP-DOCK`, `CMP-PATH`  
**Real states to design for:** speaking question, waiting for answer, listening, thinking

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| halo finished speaking the question (TTS onDone) | `trace lap → pulse on mic` | Dock mic | user taps any answer / mic opens | static outline | Mic |
| learning.priorKnowledge accepted | `halo_learn_open` | Ring → focus card 900 ms | Stop / back | cross-fade 150 ms | — |

**Accessibility:** Question is spoken AND shown; tiles have picture + word · No 'test' or 'quiz' words anywhere

**Capability:** native

### K12 · Learning focus  
*kids · stage EXPLAIN · PRACTICE*

**Hierarchy:** Journey path (icons) · ONE focus card (idea + picture, or one question) · read-along captions · contextual row: Simpler · Example · Hint · Show me more · dock.

**Entry:** K11; K13 branch chosen; Resume

| User action | halo event | New view |
|---|---|---|
| Speak / tap 'My idea' | `attempt.submit` | K12 (feedback) |
| Simpler / Example / Hint | `tutor.request(kind)` | K12 |
| Show me more | `learning.branches` | K13 |
| Done for now | `learning.reflect` | K14 |
| Stop | `runtime.stop` | K12 stopped |

**Components:** `CMP-PATH`, `CMP-FOCUS`, `CMP-CAPTION`, `CMP-LADDER`, `CMP-DOCK`, `CMP-INTENT`, `CMP-SOURCE`  
**Real states to design for:** speaking, waiting, listening, thinking, checked: correct, checked: not yet, stopped, offline

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| question asked (TTS done) | `Intent on mic` | Dock mic | mic opens | outline | Mic |
| no input for 12 s after question AND mic closed | `Intent moves to Hint` | Hint button | any input | outline | Hint |
| attempt checked correct by tutor | `halo_learn_insight then bloom_kids (only first independent success per idea)` | Focus card / Ring | Stop cancels unplayed bloom | SUCCESS glyph + 'You worked it out' | Next (one lap) |
| attempt checked not yet | `halo_learn_retry` | Ring 900 ms | — | static ring + caption | Another way |

**Accessibility:** Every button speaks its result: 'Hint: a small nudge' · Captions on by default in Kids · Text scales to 200% without hiding the focus card

**Capability:** native (tutor: local model; narration needs a TTS voice)

### K13 · Topic map  
*kids · stage EXPLORE*

**Hierarchy:** Topic ring in the middle · 2-3 branches as picture tiles · 'Something else? Just ask' · back-to-main pill.

**Entry:** K12 Show me more

| User action | halo event | New view |
|---|---|---|
| Pick a branch | `learning.branch(id)` | K12 |
| Ask another way | `voice.transcript → learning.branch(custom)` | K12 |
| Back to the main topic | `learning.branch(main)` | K12 |

**Components:** `CMP-RING`, `CMP-CHOICE`, `CMP-DOCK`, `CMP-PATH`  
**Real states to design for:** thinking (branches loading → shimmer), ready, listening

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| branches returned | `halo_learn_branch: grow` | Map 600 ms | Stop / back | static map | none — the child chooses |
| branch tapped | `halo_learn_branch: choose` | chosen branch 400 ms | — | highlight | — |

**Accessibility:** Branches read as a list: 'Three ideas to explore: gravity, event horizon, stars'

**Capability:** native

### K14 · What I learned  
*kids · stage REFLECT · CONTINUE*

**Hierarchy:** 'I learned' bubbles (child's own words) · 'Still wondering' · next idea · effort progress · Save to my notes · Keep going later.

**Entry:** K12 Done for now; H23 last exercise

| User action | halo event | New view |
|---|---|---|
| Save to my notes | `notebook.save(summary)` | K14 saved |
| Keep going later | `learning.pause` | K00 (Resume tile) |
| Explore next idea | `learning.branch(next)` | K12 |

**Components:** `CMP-PROGRESS`, `CMP-RING`, `CMP-DOCK`, `CMP-PATH`  
**Real states to design for:** summary ready, saving, saved, save failed

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| notebook.save success | `remember` | Save button → Notebook icon 700 ms | — | check glyph | Keep going later |

**Accessibility:** Progress described in words: 'You tried 4 steps and explained 2 ideas'

**Capability:** native

### H20 · Homework source  
*kids · study · stage START*

**Hierarchy:** Camera viewfinder with page framing · Gallery · File · My notes · where-it-runs chip ('Reading stays on this phone').

**Entry:** K10 Homework photo; S40 'Homework'; voice 'help with exercise three'

| User action | halo event | New view |
|---|---|---|
| Capture | `capture.still → extraction.start` | H21 |
| Pick gallery/file/note | `source.pick` | H21 |
| Camera permission missing | `permission.request (system dialog)` | H20 permission state |

**Components:** `CMP-WHERE`, `CMP-DOCK`, `CMP-CONSENT`  
**Real states to design for:** camera permission missing, searching page, page held still, capturing, reading (local), needs cloud vision → consent

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| frame analyser finds page quad | `halo_learn_frame: searching → locked` | viewfinder corners | camera paused / Stop | static corners + 'Hold still' | Capture (Intent) |
| capture.still | `tool_camera: start` | shutter 400 ms | — | flash-free still | — |

**Accessibility:** 'Page found. Tap capture or say take photo.' · Never auto-captures without a visible countdown

**Capability:** native camera + local OCR; cloud vision is provider-dependent and needs consent

### H21 · Check the exercises  
*kids · study · stage START*

**Hierarchy:** Photo with outlined exercise boxes · confidence legend · list 'I found 4 exercises' · fix a box · choose one exercise.

**Entry:** H20 extraction done

| User action | halo event | New view |
|---|---|---|
| Pick exercise | `homework.select(n)` | H22 |
| Fix a box / text | `extraction.correct` | H21 |
| Retake | `nav.back` | H20 |

**Components:** `CMP-EXTRACT`, `CMP-PROVIDER`, `CMP-DOCK`  
**Real states to design for:** reading (shimmer), all sure, some unsure, nothing found, cancelled

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| extraction result | `box outlines draw (Compose 300 ms each, staggered)` | photo overlay | Stop | static outlines | Unsure box first, else exercise 1 |

**Accessibility:** Each box: 'Exercise 3, sure' / 'Exercise 4, please check — I could not read the last number'

**Capability:** native extraction; quality depends on the installed vision model

### H22 · One exercise  
*kids · study · stage PRACTICE*

**Hierarchy:** The exercise (cropped from the photo) · halo's guiding question · 'My thinking' (speak or tap) · hint ladder · Check my thinking.

**Entry:** H21 select; H23 next; Resume

| User action | halo event | New view |
|---|---|---|
| Speak thinking | `attempt.submit` | H22 feedback → H23 |
| Climb ladder | `tutor.request(rung)` | H22 |
| Full solution | `confirm → tutor.request(SOLUTION)` | H23 (worked) |
| Stop | `runtime.stop` | H22 stopped |

**Components:** `CMP-FOCUS`, `CMP-LADDER`, `CMP-DOCK`, `CMP-INTENT`, `CMP-SOURCE`, `CMP-CAPTION`  
**Real states to design for:** asking what was covered in class, listening, thinking, hint shown, checking, stopped

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| question asked | `Intent on mic` | Dock | input | outline | Mic |
| 12 s hesitation | `Intent → Hint rung` | ladder | input | outline | Hint |
| checking attempt | `halo_kids_orb_thinking + shimmer on feedback slot` | Ring + slot | Stop (no late result) | static | — |

**Accessibility:** Ladder rungs read with position: 'Hint, step 2 of 5' · Full solution asks: 'Show every step? You can still try first.'

**Capability:** native

### H23 · Why it works  
*kids · study · stage PRACTICE · REFLECT*

**Hierarchy:** Feedback explaining WHY (child's step highlighted) · optional worked method (collapsed) · next exercise card · Save checkpoint.

**Entry:** H22 checked

| User action | halo event | New view |
|---|---|---|
| Next exercise | `homework.next` | H22 |
| Try again | `homework.retry` | H22 |
| Save checkpoint | `learning.checkpoint` | H23 saved |
| All done | `learning.reflect` | K14 / S44 |

**Components:** `CMP-FOCUS`, `CMP-PROGRESS`, `CMP-INTENT`  
**Real states to design for:** correct reasoning, correct answer, reasoning missing, not yet, solution shown

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| checked correct reasoning | `halo_learn_insight → bloom_kids (once)` | card | — | check glyph | Next exercise (one lap) |
| not yet | `halo_learn_retry` | Ring | — | static | Try again |
| homework.next | `halo_learn_advance` | card 700 ms | — | slide | Mic |

**Accessibility:** Never 'Wrong!'; always the reason + what to try

**Capability:** native

### C30 · What shall we make?  
*kids · stage START*

**Hierarchy:** 3 picture tiles: Story · Character · Project (Drawing tile only when an image provider is set up) · 'Or just tell me your idea'.

**Entry:** K00 Create

| User action | halo event | New view |
|---|---|---|
| Pick tile / speak idea | `create.start(kind, idea)` | C31 |
| Resume project | `create.resume` | C32 |

**Components:** `CMP-CHOICE`, `CMP-DOCK`, `CMP-WHERE`  
**Real states to design for:** idle, listening, image provider not set (Drawing hidden → 'Draw it yourself in Canvas')

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| screen shown | `Intent on mic` | Dock | input | outline | Mic |

**Accessibility:** Max 3 tiles; 4th activity behind 'More ideas'

**Capability:** native text; drawing generation provider-dependent

### C31 · Shape the idea  
*kids · stage DISCOVER*

**Hierarchy:** Child's idea as an editable card · 1-2 inspiring questions ('Who is the hero?') · 'That's it, let's make it'.

**Entry:** C30

| User action | halo event | New view |
|---|---|---|
| Answer questions | `create.refine` | C31 |
| Let's make it | `create.draft` | C32 |

**Components:** `CMP-FOCUS`, `CMP-DOCK`, `CMP-INTENT`  
**Real states to design for:** speaking question, listening, idea updated

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| idea updated from speech | `canvas_arrange (idea card settles)` | card 600 ms | — | fade | Let's make it |

**Accessibility:** Idea card editable by voice: 'Change the dragon to a fox'

**Capability:** native

### C32 · Make it  
*kids · stage EXPLAIN · PRACTICE*

**Hierarchy:** Workspace (story chapter / character sheet / project cards) in Canvas · generation stage shown honestly · Ask halo · Undo.

**Entry:** C31; C34 resume

| User action | halo event | New view |
|---|---|---|
| Draft arrives | `create.draft.ready` | C33 |
| Edit | `canvas.edit` | C32 |
| Generation unsupported | `provider.unavailable` | C32 alternative |

**Components:** `CMP-DRAFT`, `CMP-WHERE`, `CMP-PROVIDER`, `CMP-DOCK`  
**Real states to design for:** drafting (shimmer), draft ready, provider not set, cloud consent needed, cancelled

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| draft text streaming | `shimmer → draft_drawon` | draft card | Stop (draft discarded, nothing saved) | static dashed border | — |

**Accessibility:** 'halo is writing a draft. It is not saved until you keep it.'

**Capability:** text native; images provider-dependent

### C33 · Keep, change or discard  
*kids · stage REFLECT*

**Hierarchy:** Draft (dashed, 'Draft' tag) beside/below the child's work · Keep · Change… · Discard (confirm).

**Entry:** C32 draft ready

| User action | halo event | New view |
|---|---|---|
| Keep | `create.keep(draft)` | C34 |
| Change | `voice → create.change` | C32 |
| Discard | `confirm → create.discard` | C32 |

**Components:** `CMP-DRAFT`, `CMP-DOCK`  
**Real states to design for:** reviewing, confirm discard

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| Keep | `remember` | draft → work 700 ms | — | check | — |

**Accessibility:** Three buttons, never swipe-only; Discard asks 'Throw away this draft?'

**Capability:** native

### C34 · My project  
*kids · stage CONTINUE*

**Hierarchy:** Saved project card · where it lives (Notebook / Canvas) · next creative step · Show a grown-up (share, gated).

**Entry:** C33 Keep

| User action | halo event | New view |
|---|---|---|
| Keep making | `create.resume` | C32 |
| Show a grown-up | `gate.request → share` | G90 |
| Home | `nav.home` | K00 |

**Components:** `CMP-PROGRESS`, `CMP-GROWNUP`  
**Real states to design for:** saved, share needs grown-up

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| shown | `Intent on 'Keep making'` | button | — | outline | Keep making |

**Accessibility:** 'Saved in My notes, Stories'

**Capability:** native; export/share depends on Android share + grown-up PIN (verify)

### S40 · Study home  
*study · stage START*

**Hierarchy:** Voice 'What do you want to learn?' entry · Continue (1-3 studies with stage) · Due review count · Start from: conversation · file · note · homework photo.

**Entry:** Nav Study; From a conversation ('Study this')

| User action | halo event | New view |
|---|---|---|
| Speak/type goal | `learning.start` | S41 |
| Continue | `learning.resume` | S42 |
| Review | `review.start` | S44 |

**Components:** `CMP-DOCK`, `CMP-WHERE`, `CMP-PATH`, `CMP-INTENT`  
**Real states to design for:** empty, has studies, due reviews

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| due reviews exist and no study open | `Intent on Review` | card | — | outline | Review |

**Accessibility:** Study cards: 'Photosynthesis, Practice stage, 2 cards due'

**Capability:** native

### S41 · Guided start  
*study · stage START · DISCOVER*

**Hierarchy:** Goal (one line) · Material chips (or 'general knowledge') · Level · 'Start' · Advanced (languages, style, schedule) collapsed.

**Entry:** S40

| User action | halo event | New view |
|---|---|---|
| Start | `learning.start(config)` | S42 |
| Advanced | `sheet.open` | S43 |

**Components:** `CMP-SOURCE`, `CMP-WHERE`  
**Real states to design for:** material loading, PDF needs image AI, ready

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| Start | `halo_learn_open` | card | — | fade | — |

**Accessibility:** Advanced is a button, not a hidden swipe

**Capability:** native

### S42 · Active study  
*study · stage ALL*

**Hierarchy:** Stage breadcrumb · focus card with source chips · depth controls (Deeper · Example · Practice) · voice dock · details behind 'Lesson details'.

**Entry:** S41; S40 continue

| User action | halo event | New view |
|---|---|---|
| Branch deeper | `learning.branch` | S42 |
| Source chip | `source.open` | S43 |
| Practice | `learning.practice` | S42 practice |
| Save summary | `notebook.save` | S44 |

**Components:** `CMP-PATH`, `CMP-FOCUS`, `CMP-SOURCE`, `CMP-LADDER`, `CMP-DOCK`, `CMP-INTENT`  
**Real states to design for:** speaking, thinking, practice, checked, stopped, offline

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| as K12 (adult timing: hesitation 20 s) | `Intent / insight / bloom` | — | — | — | one |

**Accessibility:** Breadcrumb: 'Stage 3 of 7, Explain'

**Capability:** native

### S43 · Lesson details  
*study · stage —*

**Hierarchy:** Bottom sheet over the live lesson: Sources (provenance) · Language · Schedule · Progress — changes apply without leaving.

**Entry:** S42 Lesson details / source chip

| User action | halo event | New view |
|---|---|---|
| Close | `sheet.close` | S42 |

**Components:** `CMP-SOURCE`  
**Real states to design for:** read, editing

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| open | `native sheet` | sheet | — | — | — |

**Accessibility:** Sheet announces 'Lesson details, the lesson is paused'

**Capability:** native

### S44 · Review & keep  
*study · stage REFLECT · CONTINUE*

**Hierarchy:** Card practice (recall first) · material backlinks · Notebook / Canvas handoff · Continue lesson.

**Entry:** S42 save; S40 review

| User action | halo event | New view |
|---|---|---|
| Rate card | `review.rate` | S44 |
| Open note | `notebook.open` | Notebook |
| Continue | `learning.resume` | S42 |

**Components:** `CMP-PROGRESS`, `CMP-SOURCE`  
**Real states to design for:** recall, revealed, done

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| set finished | `bloom (adult, once)` | card | — | check | Continue lesson |

**Accessibility:** Recall before reveal; reveal is always available

**Capability:** native

### G90 · Global layer  
*global · stage —*

**Hierarchy:** Voice dock (mic ⇄ Stop) · captions · Help ('What can I do here?') · where-it-runs chip · grown-up lock · cloud consent sheet.

**Entry:** Every Kids/Study screen

| User action | halo event | New view |
|---|---|---|
| Stop | `runtime.stop: cancel TTS, generation, capture` | same screen, stopped |
| Help | `help.speak(screenId)` | same |
| Grown-up | `gate.pin` | Grown-up space |

**Components:** `CMP-DOCK`, `CMP-CAPTION`, `CMP-WHERE`, `CMP-CONSENT`, `CMP-GROWNUP`, `CMP-RING`  
**Real states to design for:** idle, listening, thinking, speaking, stopped, offline, cloud consent pending, provider unavailable, error

**Motion**

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| runtime.stop | `Ring → stopped (static), all FX cancelled same frame` | global | — | — | none |

**Accessibility:** Stop is reachable in one tap from everywhere and is the first dock control in focus order while halo speaks

**Capability:** native

## 4. Components

| ID | Component | Purpose | Asset / code | Status |
|---|---|---|---|---|
| `CMP-RING` | Halo Ring (kids / standard) | Presence bound to the REAL runtime state (idle, listening, capturing_voice, thinking, speaking, waiting, stopped, error, offline, success). | kids/lottie/halo_kids_orb_<state>_<theme>.json · orb/lottie/halo_orb_<state>_<theme>.json; static: kids/orb_svg, orb/svg | existing |
| `CMP-INTENT` | Intent indicator (Guide trace) | Marks the ONE recommended action: one lap on first appearance, then breath pulse. Moves, never multiplies. | Modifier.haloTrace(enabled, accent, lapOnAppear) · fx/lottie halo_fx_trace_pill|trace_pulse_pill | existing |
| `CMP-DOCK` | Voice dock | Persistent bottom dock: big mic (72dp kids / 56dp adult) ⇄ Stop, captions line, Type alternative, Help. Mic ripple only while mic is open. | HaloRipple(micOpen, level) · new layout | new |
| `CMP-CAPTION` | Caption strip | Live captions of what halo says and what it heard. Tap a word to hear it again (kids read-along). | Compose · kids read-along (v1.1) | existing+ |
| `CMP-FOCUS` | Focus card | The single live learning task: concept/question, optional illustration, 1-3 contextual actions. Never scrolls the task away. | HaloFocusFrame / StudyFocusFrame (existing) → restyled | existing+ |
| `CMP-PATH` | Journey path | 7 stages as an open-ring path; kids see icons only (Now / Next), adults see a breadcrumb. | new | new |
| `CMP-LADDER` | Hint ladder | 5 rungs; climbing is the learner's choice; shows where you are, never a score. | new · TutorStage | new |
| `CMP-SOURCE` | Source chip | Where an explanation comes from: note, file page, photo, general knowledge. Tap → source view (S43). | existing citations → chip | existing+ |
| `CMP-WHERE` | Where-it-runs chip | On this phone / Needs cloud OK / In the cloud / Offline / Provider unavailable. Required on voice, vision and generation screens (C09). | kit halo-language .h-where → Compose chip | existing+ |
| `CMP-CONSENT` | Cloud consent sheet | Explains what leaves the phone, who must agree (grown-up PIN for kids), local alternative, Not now. Never traced. | new · cloud_handoff plays only AFTER consent | new |
| `CMP-DRAFT` | Draft review | AI draft visually separate (dashed accent border, 'Draft' tag) from the child's work; Keep / Change / Discard; no auto-accept. | Modifier.haloDraftBorder + fx draft_drawon | existing+ |
| `CMP-PROGRESS` | Effort progress | Counts reasoning steps tried and ideas understood — never minutes, streaks or rank. Optional bloom on genuine progress. | fx bloom_kids · new card | new |
| `CMP-PROVIDER` | Provider-state message | Calm card for unsupported provider / offline / OCR unsure / cancelled, always with a usable alternative. | new | new |
| `CMP-CHOICE` | Big choice tile | Kids picture tile ≥ 56dp, icon + 1-2 words, max 3 per screen. | KidsChoice (existing) → restyled | existing+ |
| `CMP-EXTRACT` | Extraction overlay | Outlined exercise boxes on the photo with confidence: solid = sure, dashed + Apricot dot = please check. | new · Study extraction review data | new |
| `CMP-GROWNUP` | Grown-up gate | PIN-protected parent options, adult-mode exit, sharing, cloud consent. | existing Grown-up space | existing |

## 5. Hint ladder (C06)

| Rung | Label | Meaning | TutorStage |
|---|---|---|---|
| 1 | Remember | What did you do in class? | `ASK` |
| 2 | Hint | One nudge toward the method | `HINT` |
| 3 | Worked step | Halo does one step, you do the rest | `PARTIAL_SOLUTION` |
| 4 | Another way | The same idea explained differently | `ANALOGY (+EXPLAIN)` |
| 5 | Full solution | Every step with reasons — opt-in, with confirm | `SOLUTION` |

Brief order ≠ current TutorStage order (ASK, HINT, EXPLAIN, ANALOGY, CHECK_ATTEMPT, PARTIAL_SOLUTION, SOLUTION). UI shows rungs in the brief's order; CHECK_ATTEMPT is the 'Check my thinking' action, not a rung. The solution rung is never locked by points or time (C07) — it is opt-in and confirmed, as today.

## 6. Motion assets

New Lotties follow the existing naming, so the current loader (`assets/halo/fx/halo_fx_<asset>_<theme>.json`, static fallback `assets/halo/fx/static/halo_fx_<asset>_<theme>.svg`) picks them up unchanged.

| File | Meaning | Timing | Markers | Size (dark) |
|---|---|---|---|---|
| `halo_fx_learn_open_{dark,light}.json` | Ring opens into the focus card (start of a lesson / resume). | once 900 ms | `open` | 3.6 KB |
| `halo_fx_learn_frame_{dark,light}.json` | Camera corners find the page; settle when the page is held still; dashed when unsure. | markers | `searching`, `locked`, `unsure` | 11.4 KB |
| `halo_fx_learn_insight_{dark,light}.json` | Two ideas connect through the ring gap after a CHECKED correct explanation. | once 800 ms | `connect` | 3.5 KB |
| `halo_fx_learn_branch_{dark,light}.json` | Three branches grow from the topic ring; one is chosen. | markers | `grow`, `choose` | 5.7 KB |
| `halo_fx_learn_advance_{dark,light}.json` | Finished exercise tucks away, next slides in, progress dot fills. | once 700 ms | `advance` | 5.1 KB |
| `halo_fx_learn_retry_{dark,light}.json` | Ring sways once and re-centres: 'let's look again'. Never red, never a cross. | once 900 ms | `sway` | 1.8 KB |

Reused from DevKit v1.2 (unchanged):

- `bloom_kids` — independent success after a checked attempt (kids)
- `bloom` — independent success (adult)
- `remember` — saved to Notebook / checkpoint
- `draft_drawon` — AI draft appears in Create
- `canvas_arrange` — idea becomes cards on Canvas
- `cloud_handoff` — after consent, while cloud runs
- `tool_camera` — capture in progress
- `shimmer` — real loading / OCR / generating
- `download_ring` — model download
- `ripple` — mic actually open (HaloRipple)
- `trace_pill / trace_pulse_pill` — Intent indicator on the one next action

30 fps, shape layers only, no images or expressions, validated in lottie-web; gap of every ring at 45° upper right. Use native Compose transitions for simple moves (fades, slides, outline draw-on in H21).

## 7. Accessibility

- Kids touch targets ≥ 56 dp, primary mic 72 dp; adults ≥ 48 dp touch area (chips 40 dp visual + Material interactive size).
- Max 3 choices per kids screen; captions on by default in Kids; every control speaks its result.
- Motion is never the only signal; reduced motion → static SVG of the same state.
- Large text: at ≥130% the illustration folds away, ladders wrap, rows stack, body scrolls under a pinned dock (see `screens/*_fs1.5.png`).
- Focus order: status → current task → recommended action → other actions → dock; while halo speaks, Stop is first.

## 8. Gaps and open questions (annotated, not guessed)

- **PL strings:** The app ships values / values-lv / values-ru; there is no values-pl. Polish copy, speech and TTS availability must be verified per installed model.
- **LearningStage enum:** The 7-stage journey is new; today only StudyLearningPhase (4) exists. Codex defines the enum; UI binds to it.
- **Hint order:** Brief order ≠ current TutorStage order (ASK, HINT, EXPLAIN, ANALOGY, CHECK_ATTEMPT, PARTIAL_SOLUTION, SOLUTION). UI shows rungs in the brief's order; CHECK_ATTEMPT is the 'Check my thinking' action, not a rung. The solution rung is never locked by points or time (C07) — it is opt-in and confirmed, as today.
- **Kids image generation:** Only cloud image providers exist (WorkspaceImageService / AiImageProvider). Drawing tile is hidden unless a provider is configured AND a grown-up consented; otherwise 'Draw it yourself in Canvas'.
- **Camera page framing:** halo_learn_frame needs a page-quad signal from the analyser; without it, show static corners + 'Hold still', never fake a lock.
- **Kids sharing:** Grown-up gated share is designed; the existing share path for Kids was not verified in source.
- **Hesitation timing:** 12 s kids / 20 s adult are design defaults, to be tuned in testing; the trigger is 'no input after halo finished speaking', never a timer alone.
- **Visual audit:** Comps follow BrandBook v2.1 + DevKit v1.2 tokens; no device screenshots of the current Kids/Study screens were provided, so current-state notes come from source only.

## 9. Files

See `MANIFEST.json` for every file with its version and checksum.
