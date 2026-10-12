# Study — user journey and Halo FX contract

Status: implementation pass for alpha validation. This document describes the UI behavior, **not** evidence of Android runtime testing.

## The intended journey

1. **Study home:** resume an existing lesson, handle review cards that are due, or begin a new topic. The screen shows at most two recent lessons until expanded.
2. **Prepare a lesson:** write a learning goal; attach saved, imported, or captured material only when relevant; select guided, practice, or explain style. Language, knowledge scope, and age/level are explicitly optional.
3. **Start:** the primary action stays visible at the bottom of the lesson-setup screen. It is disabled until there is a topic or material; a reason is displayed.
4. **Learn:** the header carries session context; the guide explains Understand → Try → Review and the stage's actual intent. The most recent tutor reply is visually distinguished.
5. **Respond:** the persistent “Write an answer or question” action opens a focused draft editor without losing the lesson position. A proposed answer is submitted for feedback; a question asks without scoring an attempt. Prompt starters fill an editable draft, never send automatically.
6. **Get help:** named next actions explain the actual transition (hint, explanation, analogy, check, partial solution). A full solution requires the existing explicit confirmation.
7. **Check sources:** a Material chip opens source management and citation provenance. Readability and version changes remain visible; an unreadable source is never presented as verified.
8. **Listen:** source/response reading remains optional. The reader shows honest sentence-level progress, playback controls, and speed selection; it does not claim word timing.
9. **Save and practise:** cards are opt-in and collapsed for readability. Drafts must be accepted before joining the review schedule. Card editing and deletion are behind deliberate actions.
10. **Review:** recall by typing an answer or revealing it, then use either a deterministic check or self-assessment. Verified checks update the schedule once; unverifiable checks show the comparison and request self-assessment. Empty due queues offer a way back.

## Halo FX attention policy

- **One clear next action**: a soft primary/secondary/tertiary gradient outline appears around a ready primary CTA. Due review takes priority over “Start lesson” on the Study home screen.
- **No fake progress**: active learning phases are conceptual signposts, not completion percentages; reader progress is calculated from the actual sentence index.
- **No distracting loops**: the focus outline breathes gently only while the action is enabled, the lifecycle is started, and the Android animator setting permits animations.
- **Real semantics**: the animated frame is never clickable. Touch, focus, TalkBack, and disabled-state semantics remain on Material buttons.
- **Not a reward counter**: no fabricated mastery, scores, streaks, badges, confetti, or claim that the learner understands something without evidence.

## Alpha acceptance checklist (manual on Android)

- [ ] Small 320 dp screen: start button always usable, keyboard doesn't cover key actions, RU/LV text wraps.
- [ ] Large screen / tablet: max-width lesson column, clear spacing, no erratic alignment.
- [ ] Font scale at 1.3× / 2×, screen reader: buttons and source previews remain comprehensible.
- [ ] System animation scale 0 and app background: no Halo FX breathing.
- [ ] Topic-only lesson: create with no source; style and setup choices survive entry.
- [ ] Attached note, PDF page, and photo: source coverage and vision requirements are honest.
- [ ] Resume partially completed lesson and return from source/Workspace: unsent draft isn't lost.
- [ ] Submit a learner attempt; request a hint; ask a question; interrupt inference; retry after provider failure.
- [ ] Before giving the full answer, confirm the explicit reveal dialog opens.
- [ ] Open reader, pause, change speed, skip sentence, background the app.
- [ ] Save a draft practice card, accept, expand/collapse, edit, and inspect provenance.
- [ ] Check a clear answer once, then check an ambiguous answer requiring self-assessment.
- [ ] Exhaust due reviews; avoid dead-end blank review screens or double-rating a card.
- [ ] Finish a lesson; confirm learning records and due reviews retain their original provenance.
- [ ] Validate localization and screenshot snapshots on EN/RU/LV, light/dark.

## Suggested local validation

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest
```

The repository does not use GitHub Actions for this workflow. Do not mark a build or device smoke test as passed without running it.
