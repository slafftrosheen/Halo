# halo — Voice & Read everywhere · handoff

voice-read-1.0 · DevKit v1.4 · BrandBook v2.3 · baseline repo @97c82273, DevKit v1.3, BrandBook v2.2

**Goal:** every place that takes input (fields, tools, Canvas, Home, Notebook, Settings, Kids, Study) can be **spoken into**, and every piece of content can be **read aloud** — with one shared component, honest states and the existing privacy rules (local speech for dictation, consent for cloud voices). Prototype: `learning/prototype/index.html` journeys **E, F, G**.

## 1. Rules
- Every text field is a HaloTextField with a kind; nobody adds a raw OutlinedTextField.
- Speech never commits silently: TEXT/SHORT/NUMBER/CODE show a preview first; SEARCH applies instantly with undo.
- SECRET fields have no mic and are never read aloud or sent to any speech engine.
- Pressing a mic stops any reading (barge-in); starting to read stops listening. One audio owner at a time.
- Voice may approve reversible actions only. Send, call, delete, pay, share, new permission and cloud consent always need a tap.
- Local speech only for dictation (never OEM cloud recognition). Cloud voices for reading ask first (Send and read / Keep text private).
- Everything voice can do has a visible tap equivalent; everything read aloud is on screen.
- Reduced motion: mic and speaker show static states; word follow becomes an underline without the wash animation.

## 2. Field kinds — every input picks one

| Kind | Use | Behaviour | Voice | Read |
|---|---|---|---|---|
| `TEXT` | Free text (notes, cards, replies, prompts, routines) | Mic in the field. Speech becomes a PREVIEW chip: Use spoken text · Edit before using · Discard. Read aloud reads the draft. | on | on |
| `SEARCH` | Search & filter | Mic in the field. Spoken words go straight in and run the search (harmless, one-tap undo). | on | results |
| `SHORT` | Names, titles, labels | Mic in the field; single line; preview chip; auto-capitalised. | on | on |
| `NUMBER` | Numbers, durations, page numbers | Mic in the field; shows what was heard and the number it became: “twelve” → 12. | on | on |
| `CODE` | Pairing / verification codes | Mic in the field; digits spoken one by one, shown grouped; always confirmed before use. | on | masked by default |
| `URL_ID` | URLs, server addresses, IDs | No mic by default (speech is unreliable here). Paste, scan or pick; read aloud spells it out. | off | spelled |
| `SECRET` | API keys, passwords, tokens | No mic, no read aloud, never sent to speech. TalkBack says “hidden”. Lock glyph instead of mic. | never | never |
| `STRUCTURED` | JSON / schema / code | No dictation into raw syntax. Mic opens “Describe it” — halo drafts the structure, shown as a draft to review. | describe | summary |

## 3. Coverage map (from source)

Today: voice in 9 of these surfaces, read aloud in 5. Target: all of them through `HaloTextField` + `Read aloud`.

| Priority | Surface | File(s) | Fields | Voice today | Read today | Kind(s) | Read target |
|---|---|---|---|---|---|---|---|
| P1 | Home composer + answers | `ui/screens/home/HomeComposer.kt + HomeScreen.kt` | 2 | yes | no | TEXT | every halo answer |
| P1 | Mini halo | `ui/mini/MiniHaloScreen.kt` | 1 | yes | no | TEXT | answer |
| P1 | Note editor | `ui/screens/notebook/NoteEditorScreen.kt` | 8 | yes | yes | TEXT, SHORT | note, selection |
| P1 | Canvas | `ui/screens/notebook/CanvasEditorScreen.kt` | 11 | partly | partly | TEXT, SHORT | card, selection, whole canvas |
| P1 | Notebook home | `ui/screens/notebook/NotebookScreen.kt` | 2 | yes | no | SEARCH, SHORT | note previews |
| P1 | Quick capture | `ui/screens/notebook/InboxCaptureDialog.kt` | 1 | no | no | TEXT | captured item |
| P1 | Contextual capture | `capture/ContextualCaptureSheet.kt` | 3 | no | no | TEXT, SHORT | captured context |
| P1 | Tool forms (halo asks for input) | `interaction/InteractionSurfaceRenderer.kt` | 4 | no | no | per field | the question + plan |
| P1 | Extraction review | `study/ExtractionReviewSheet.kt` | 3 | no | no | TEXT | extracted text |
| P1 | Study home / setup | `ui/screens/study/StudyHomeScreen.kt` | 7 | partly | no | TEXT, SHORT, SEARCH | material preview |
| P1 | Study lesson | `ui/screens/study/StudyScreen.kt` | 10 | yes | yes | TEXT | tutor turns, sources |
| P1 | Kids | `ui/screens/kids/KidsModeScreen.kt + KidsHomeChoices.kt` | 4 | yes | yes | TEXT (kids dock) | everything, by default |
| P1 | Memory | `ui/screens/notebook/MemoryHubScreen.kt + PersonalMemorySection.kt` | 5 | no | no | TEXT, SEARCH | each memory |
| P1 | Notification detail | `ui/screens/notifications/NotificationDetailScreen.kt` | 1 | no | no | TEXT (reply) | notification |
| P1 | Settings search | `ui/screens/settings/SettingsScreen.kt` | 2 | partly | no | SEARCH | setting explanations |
| P2 | Conversations | `ui/screens/notebook/ConversationsScreen.kt` | 1 | no | no | SEARCH | conversation |
| P2 | Workspace object | `ui/screens/notebook/WorkspaceObjectDetailScreen.kt` | 1 | no | no | TEXT | object |
| P2 | Canvas organise | `ui/screens/notebook/CanvasOrganizationDialog.kt` | 2 | no | no | SHORT | — |
| P2 | Image prompt | `ui/workspace/WorkspaceImageDialog.kt` | 2 | no | no | TEXT | prompt |
| P2 | Object picker | `ui/workspace/WorkspaceObjectPicker.kt` | 1 | no | no | SEARCH | results |
| P2 | Notification inbox | `ui/screens/notifications/NotificationInboxScreen.kt` | 1 | no | no | SEARCH | list |
| P2 | Routine editor | `ui/screens/settings/EverydayRoutineEditor.kt` | 6 | no | no | TEXT, SHORT, NUMBER | routine summary |
| P2 | Skill starter | `ui/screens/settings/SkillStarterEditor.kt` | 2 | no | no | TEXT, SHORT | skill description |
| P2 | Speech lab | `ui/screens/settings/SpeechLabScreen.kt` | 1 | no | no | TEXT | test phrase |
| P2 | Speech output | `ui/screens/settings/SpeechOutputSettings.kt` | 2 | no | no | SECRET, TEXT | sample sentence |
| P2 | BuddyBot control | `ui/screens/devices/BuddyBotControlScreen.kt` | 2 | no | no | TEXT | status |
| P2 | Pairing | `ui/screens/discovery/PairingScreen.kt` | 1 | no | no | CODE | code (masked) |
| P2 | Setup | `ui/onboarding/HaloSetupScreen.kt` | 2 | no | no | SHORT, SECRET | each step |
| P3 | Sandboxes | `ui/screens/settings/PhoneControlSandboxScreen.kt + AutomationSandboxScreen.kt` | 2 | no | no | TEXT | result |
| P3 | Model & skill lists | `ui/screens/settings/ModelBrowserScreen.kt + OnDeviceModelsScreen.kt + ImageModelsScreen.kt + SkillsScreen.kt` | 5 | no | no | SEARCH | item details |
| P3 | Model picker | `ui/glass/components/HaloGlassModelPickerDialog.kt` | 1 | no | no | SEARCH | — |
| P3 | Model compare / tuning | `ui/screens/settings/InstalledModelComparisonSection.kt + ModelTuningSection.kt` | 2 | no | no | TEXT, NUMBER | results |
| P3 | Notification settings | `ui/screens/settings/NotificationSettingsScreen.kt` | 1 | partly | no | SHORT | — |
| P3 | PDF page jump | `ui/screens/notebook/CanvasPdfPageDialog.kt` | 1 | no | no | NUMBER | — |
| P3 | Integrations / MCP | `ui/screens/integrations/IntegrationsScreen.kt + McpServerScreen.kt` | 5 | no | no | URL_ID, SECRET, SHORT | spelled URL |
| P3 | Cloud provider keys | `ui/screens/settings/ProviderControlScreens.kt` | 2 | no | no | SECRET, URL_ID | never the key |
| P3 | Backup passwords | `ui/screens/settings/StorageBackupScreen.kt + BackupExportOptions.kt` | 2 | no | no | SECRET | never |
| P3 | Routine schema | `ui/screens/settings/RoutineSchemaEditor.kt` | 1 | no | no | STRUCTURED | summary |
| P3 | Diagnostics | `ui/screens/settings/WorkloadDiagnosticsPanel.kt` | 3 | no | no | NUMBER | report summary |

Total: 39 surfaces · 113 text fields.

## 4. Components

| ID | Component | Purpose | Status |
|---|---|---|---|
| `VR-FIELD` | HaloTextField (voice field) | The ONE text field: label, value, kind, trailing mic (or lock for SECRET), read button when there's text. Replaces direct OutlinedTextField use. Built on HaloVoiceTextActions. | new (wraps existing) |
| `VR-PREVIEW` | Spoken-text preview | What was heard, as an uncommitted chip under the field: Use spoken text · Edit before using · Discard. Low-confidence words underlined. | existing → restyled |
| `VR-READ` | Read aloud button | Speaker in a small open ring. Ring fills as reading progresses; tap again = pause; long-press = speed. | new |
| `VR-BAR` | Read-aloud bar | Pinned above the nav while reading: title, ⏮ sentence, ⏯, ⏭ sentence, speed, voice, Stop. Survives scrolling and screen changes within the same object. | new |
| `VR-HIGHLIGHT` | Word follow | The word being read gets an accent wash; the line scrolls into view. Off-screen → a ‘Back to reading’ pill. | existing (kids read-along) → everywhere |
| `VR-SHEET` | Long dictation sheet | Bottom sheet for long speech (notes, stories): live partial text, waveform ring, Pause, Done. Text lands as a preview. | new |
| `VR-CMD` | Voice commands | Per-surface spoken commands (Canvas: add card, connect, read this card; Home: ask; Settings: find). Shown in the ‘What can I say?’ sheet. | new |
| `VR-CONFIRM` | Spoken confirmation rule | Voice can fill, navigate and approve reversible actions. Irreversible or sensitive actions (send, call, delete, pay, share, new permission, cloud consent) always need a tap. | rule |
| `VR-STATUS` | Voice status chip | Local speech ready · Speech model missing · Mic permission needed · Cloud voice needs OK · Offline. Shown in the field’s helper line, never as a toast only. | new |
| `VR-SETTINGS` | Voice & reading settings | Dictation language, auto-punctuation, read speed, voice, follow words, auto-read answers, kids read-along. | existing screens → grouped |

## 5. Screens (interaction spec)

### V01 · Home: ask by voice, hear every answer
*app* — today: HomeComposer has a mic; answers are not read aloud from the answer itself.

**Hierarchy:** Answer card with Read aloud (open-ring speaker) · word follow while reading · read-aloud bar · composer: text + mic; long-press mic = long dictation sheet.

| User action | halo event | New view |
|---|---|---|
| Tap speaker on an answer | `tts.request(answerId)` | V01 reading |
| Tap mic | `voice.open(composer)` | V01 listening |
| Mic while reading | `tts.stop → voice.open (barge-in)` | V01 listening |

**States:** idle, listening, preview, reading, paused, speech model missing

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| mic tapped AND audio focus granted | `halo_fx_voice_field: open → listen` | mic 48dp | Stop / blur / app background | static ring + 'Listening' | — |
| tts playback started (provider event) | `halo_fx_read_aloud: start → reading` | speaker 40dp | tts.done / stop / barge-in | static speaker + progress ring | — |

**Accessibility:** Speaker: 'Read answer aloud' ⇄ 'Pause reading' · Reading follows TalkBack: when TalkBack speaks, halo's own reading pauses  
**Capability:** native (local STT; TTS = selected provider)

### V02 · Notebook: dictate and listen to notes
*app* — today: NoteEditor has HaloVoiceTextActions (voice + read) in a separate action row.

**Hierarchy:** Editor toolbar gets Mic + Read · read selection from the text-selection menu · long dictation sheet for long speech · preview chip inserts at the caret.

| User action | halo event | New view |
|---|---|---|
| Mic | `voice.open(note, caret)` | VR-SHEET |
| Use spoken text | `dictation.commit(caret)` | V02 inserted |
| Select text → Read | `tts.request(selection)` | V02 reading |

**States:** editing, long dictation, preview, inserted, reading selection

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| dictation.commit | `halo_fx_dictation_insert` | preview chip → caret 500 ms | — | fade | — |
| sheet open & mic open | `halo_fx_voice_wave: listen` | sheet ring 120dp | Done / Stop | static ring + live text | Done |

**Accessibility:** Inserted text is announced: 'Added 2 sentences'  
**Capability:** native

### V03 · Canvas: speak cards, hear the board
*app* — today: Canvas cards have some voice/read actions (5 voice, 3 read references) but not on every card or for the board.

**Hierarchy:** Card toolbar: Mic (dictate into card) + Read · board-level 'Read this canvas' (cards in reading order) · voice commands: 'add a card…', 'connect to…', 'read this card'.

| User action | halo event | New view |
|---|---|---|
| Say 'add a card about …' | `canvas.voiceCommand(add)` | V03 card added |
| Read this canvas | `tts.request(canvas, order=spatial)` | V03 reading |
| Card mic | `voice.open(card)` | V03 preview |

**States:** idle, command heard, card added (draft), reading card, reading board

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| canvas.voiceCommand(add) applied | `halo_fx_canvas_voice_add` | new card 800 ms | undo | fade-in | Keep (card is a draft until kept) |
| reading board, next card | `Compose: card outline highlight + auto-pan` | card | stop | outline only | — |

**Accessibility:** Reading order = top-left → bottom-right, then connectors · Every voice command has a visible button equivalent  
**Capability:** native; spoken commands use the local model

### V04 · Tools: hear the plan, answer by voice
*app* — today: Tool forms (InteractionSurfaceRenderer) and approval sheets are tap-only.

**Hierarchy:** Approval sheet ‘I’ll do exactly this’ gets Read aloud · tool-form fields are voice fields · voice answers fill fields · the confirm button still names the action.

| User action | halo event | New view |
|---|---|---|
| Read it to me | `tts.request(plan)` | V04 reading |
| Say the missing value | `voice → field (preview)` | V04 filled |
| Say 'do it' on a reversible action | `approval.voice(accepted)` | action runs |
| Say 'do it' on send/delete/pay/permission | `approval.voice(needsTap)` | V04 'Tap Send message to confirm' |

**States:** plan shown, reading plan, field listening, voice-approvable, tap required

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| tts.request(plan) | `halo_fx_read_aloud + step highlight (Compose)` | sheet | stop | static | the named action button |

**Accessibility:** Spoken plan reads the numbered steps exactly as shown · ‘Tap required’ is spoken AND shown  
**Capability:** native

### V05 · Settings & forms: field kinds
*app* — today: Settings, routines, integrations and provider keys use plain fields.

**Hierarchy:** Routine editor: TEXT/SHORT/NUMBER with mic · provider key: SECRET with lock, no mic, no read · MCP URL: URL_ID, read spells it · settings search: SEARCH with mic.

| User action | halo event | New view |
|---|---|---|
| Mic on NUMBER | `voice → number.normalize` | 'twelve' → 12 preview |
| Tap lock on SECRET | `info.speak('Keys are never sent to speech')` | same |

**States:** each kind idle, number heard, secret (no voice), url spelled

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| voice.open on any field | `halo_fx_voice_field` | trailing 40dp | blur | static | — |

**Accessibility:** SECRET: no mic, TalkBack reads 'API key, hidden, voice input off for secrets'  
**Capability:** native

### V06 · Read-aloud bar + follow
*app* — today: Read aloud is per-component with no shared transport.

**Hierarchy:** Bar above the nav: title · ⏮ ⏯ ⏭ by sentence · speed chip · voice chip · Stop. Off-screen word → ‘Back to reading’ pill.

| User action | halo event | New view |
|---|---|---|
| Pause/resume | `tts.pause/resume` | same |
| Next sentence | `tts.seek(+1)` | same |
| Stop | `tts.stop` | bar hides |

**States:** reading, paused, preparing voice, cloud voice needs OK, voice unavailable

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| tts playing | `progress ring on ⏯ (HaloProgressRing)` | bar | stop | static progress | — |

**Accessibility:** Bar is a media session too: lock-screen + headset buttons work  
**Capability:** native; media session = verify

### V07 · Voice states that need help
*app* — today: HaloVoiceTextActions already shows 'Install a local speech model in Voice settings.' and cloud voice confirmation.

**Hierarchy:** Helper line under the field explains the real reason + one fix: model missing → Get speech model; permission → Allow microphone; cloud voice → Send and read / Keep text private; offline → local still works.

| User action | halo event | New view |
|---|---|---|
| Get speech model | `nav.voiceSettings(models)` | Settings |
| Allow microphone | `permission.request` | system dialog |
| Send and read | `consent.cloudTts(yes)` | reading |
| Keep text private | `consent.cloudTts(no)` | idle |

**States:** model missing, permission missing, cloud voice consent, recognizer busy, nothing heard

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| — | `none (static state)` | — | — | — | the one fix |

**Accessibility:** Never toast-only; the helper line stays until resolved  
**Capability:** native

### V08 · Voice & reading settings
*app* — today: Spread over Speech output, Speech lab and Voice settings.

**Hierarchy:** One page: Dictation (language, punctuation, keep preview) · Reading (voice, speed, follow words, auto-read answers) · Kids (read-along, word tap) · Where it runs (local/cloud).

| User action | halo event | New view |
|---|---|---|
| Try voice | `tts.sample` | playing sample |

**States:** local voice, cloud voice selected

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| sample playing | `halo_fx_read_aloud` | row speaker | stop | static | — |

**Accessibility:** Speed slider has 0.75 / 1 / 1.25 / 1.5 stops  
**Capability:** native

### K15 · Read-along story
*kids* — today: Kids read-along exists in lessons (words light up; tap a word to hear it again).

**Hierarchy:** Story page (big type, picture) · words light up as halo reads · tap a word → it pops and is spoken · page turns at the end · ‘Read it myself’.

| User action | halo event | New view |
|---|---|---|
| Tap word | `tts.word(id)` | K15 word pop |
| Next page | `story.page(+1)` | K15 |
| Read it myself | `mode.echo` | K16 |

**States:** reading, paused, word pop, page turn

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| page finished reading | `halo_fx_kids_page_turn` | page 600 ms | stop | cross-fade | Next page |
| word tapped | `halo_fx_kids_word_pop` | word 500 ms | — | highlight | — |

**Accessibility:** Font size control; dyslexia-friendly spacing option  
**Capability:** native (TTS word timing needs a provider that reports word boundaries — else sentence-level)

### K16 · I read, halo listens
*kids* — today: Not present.

**Hierarchy:** One sentence big · child reads aloud · words they read get a soft wash · halo says what it heard and helps with ONE tricky word · no score.

| User action | halo event | New view |
|---|---|---|
| Read aloud | `voice.open(echo)` | K16 listening |
| Help with this word | `tts.word(id)` | K16 |
| Next sentence | `echo.next` | K16 |

**States:** ready, listening, heard, tricky word, nothing heard

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| mic open | `HaloRipple + word wash as recognised` | sentence | stop | static | — |
| sentence read | `halo_fx_learn_insight (once)` | sentence | — | check | Next sentence |

**Accessibility:** 'I heard: …' always shown; never 'wrong'  
**Capability:** native; word-level matching depends on the local STT model

### K17 · Pre-reader mode
*kids* — today: Not present.

**Hierarchy:** Every tile speaks its name when touched (first touch = hear, second = choose) · icons larger, words smaller · halo reads every screen on arrival.

| User action | halo event | New view |
|---|---|---|
| Touch tile once | `tts.label(tile)` | K17 tile highlighted |
| Touch again | `tile.choose` | destination |

**States:** screen announced, tile heard, chosen

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| first touch | `Compose: tile lifts + halo_fx_read_aloud mini` | tile | touch elsewhere | outline | same tile (second tap) |

**Accessibility:** Works with TalkBack off; when TalkBack is on, TalkBack's model wins  
**Capability:** native

### S45 · Listen mode
*study* — today: Study narration exists (narrationAvailable, onNarrationEvent).

**Hierarchy:** Lesson as chapters on a segmented ring · play/pause · ‘Ask about this’ by voice mid-way · questions are read and can be answered by voice · lock-screen controls.

| User action | halo event | New view |
|---|---|---|
| Ask by voice | `tts.pause → voice.open` | S45 asking |
| Answer question | `attempt.submit(voice)` | S45 |
| Back to reading | `mode.read` | S42 |

**States:** playing, paused, asking, question waiting

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| chapter progress | `halo_fx_listen_chapters` | ring 160dp | stop | static segments | — |

**Accessibility:** Every chapter has its text one tap away  
**Capability:** native

### S46 · Speak to answer cards
*study* — today: Review cards are typed / revealed.

**Hierarchy:** Card question read aloud · answer by voice (preview) · reveal · halo compares meaning, not exact words · rate.

| User action | halo event | New view |
|---|---|---|
| Say answer | `voice.open(card)` | S46 heard |
| Reveal | `review.reveal` | S46 compared |

**States:** question read, listening, heard, compared

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| compared: close match | `halo_fx_learn_insight` | card | — | check | Rate |

**Accessibility:** Rating buttons speak their meaning  
**Capability:** native

### S47 · Explain it back
*study* — today: Not present.

**Hierarchy:** ‘Explain photosynthesis as if to a friend’ · speak for up to 2 min · halo shows what you covered and the 1–2 gaps · go deeper into a gap.

| User action | halo event | New view |
|---|---|---|
| Explain | `voice.open(long)` | VR-SHEET |
| Fill a gap | `learning.branch(gap)` | S42 |

**States:** prompt, listening (long), comparing, covered + gaps

| Trigger | Animation | Scope | Exit / cancel | Fallback | Next-action cue |
|---|---|---|---|---|---|
| comparison ready | `halo_fx_explain_back: compare` | card 900 ms | stop | static arcs + list | the first gap |

**Accessibility:** Covered/gaps given as a text list too  
**Capability:** native (long local STT ≤ 2 min per take)

## 6. Motion assets (new)

| File | Meaning | Markers | Size |
|---|---|---|---|
| `halo_fx_voice_field_{dark,light}.json` | The mic glyph opens into a small open ring (open); breathes while recording (listen, loops; level comes from Compose); tightens while recognising (recognize); closes back to the mic (close). | `open`, `listen`, `recognize`, `close` | 4.6 KB |
| `halo_fx_dictation_insert_{dark,light}.json` | The preview chip shrinks into the caret and the caret blinks once. | `insert` | 2.1 KB |
| `halo_fx_read_aloud_{dark,light}.json` | Sound arcs leave the speaker through the ring gap (start), loop gently while reading (reading), fold away (stop). | `start`, `reading`, `stop` | 5.0 KB |
| `halo_fx_voice_wave_{dark,light}.json` | Level bars arranged along the open ring for the dictation sheet; loops while the mic is open. | `listen` | 26.5 KB |
| `halo_fx_canvas_voice_add_{dark,light}.json` | A spoken card drops from the ring onto the canvas and a connector draws to its neighbour. | `add` | 4.2 KB |
| `halo_fx_kids_page_turn_{dark,light}.json` | Story page curls from the upper-right corner (the gap) to the next page. | `turn` | 5.2 KB |
| `halo_fx_kids_word_pop_{dark,light}.json` | A tapped word lifts in a soft bubble while it's spoken. | `pop` | 2.0 KB |
| `halo_fx_listen_chapters_{dark,light}.json` | A segmented open ring fills chapter by chapter; current segment breathes. | `chapter` | 3.9 KB |
| `halo_fx_explain_back_{dark,light}.json` | Two speech arcs meet; gaps that weren't covered stay open as small Apricot dots. | `compare` | 3.0 KB |

Same naming as `assets/halo/fx/`, static fallbacks in `static/`. Kids assets (`kids_*`) use the warm kids palette. Validated in lottie-web; shape layers only; ring gaps at 45° upper right.

## 7. Gaps (annotated)
- **Word timing:** word follow needs a TTS provider that reports word boundaries; otherwise follow by sentence.
- **Media session:** lock-screen / headset control for the read-aloud bar was not found in source — verify.
- **Number/code normalisation** per language (LV/RU/PL number words) must be checked with the installed STT model.
- **K16 reading practice** depends on local STT word alignment; without it show the transcript only (no word wash).
- **Polish:** no `values-pl` in the app yet.
- **Long dictation:** current capture caps at 15 s (`maxDurationMs=15_000`); the long sheet and S47 (2 min) need chunked capture.
