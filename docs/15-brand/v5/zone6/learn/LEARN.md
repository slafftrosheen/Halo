# halo Learn — one path, three sizes (Study + Kids) · pack 7.1 · boards 27–32

Baseline repo `98aa2fb`. Redesign of the presentation and flow. Every learning owner stays as it is
(StudyViewModel, LearningHelpLadder, StudyReviewIntensity, KidsProjectViewModel, KidsGrownUpGate…).
Not yet built or tested on a phone.

## 1. The idea
Study and Kids are **the same five steps**: **Choose · Look · Try · Help · Keep**.
Nobody is asked their age. The learner (or a grown-up) picks a **size**:

| Size | For | On screen | Answers | Help |
|---|---|---|---|---|
| 1 · Pictures | pre-readers, anyone tired, new language | one idea, everything read aloud, 56dp | max 3 picture choices; tap = hear, tap again = choose | “Let’s do it together” |
| 2 · Simple | early readers, anyone who wants calm | short sentences | 3 choices or a short answer | Hint first |
| 3 · Full | teens & adults, homework, exams | full text, photos, PDFs | own words + working (“I think… because…”) | Hint → Help me think → Full solution (asks first) |

Kids mode = Size 1 by default (a grown-up can set Size 2). Study = Size 3 by default; “Make it simpler” in More.
halo may *suggest* a smaller size after two hard tries; it never switches silently.

Mapping to code: Look ≈ `StudyLearningPhase.EXPLORE`, Try ≈ `TRY`, Help = `LearningHelpLadder`, Keep ≈ `REVIEW/COMPLETE` + drafts → Keep card.
`LearningStage` (START…CONTINUE) stays internal; the learner sees five steps only.

## 2. Study flow, step by step
1. **Choose** — three doors: *Keep practising* (filled when cards are due), *Learn a topic* (say or type), *My homework or notes* (photo / PDF / file). “Continue” lists the open lesson.
2. **Setup (first time per topic, one question)** — “How should halo explain?” Simpler / Balanced / Deeper, each shown as the same idea written three ways (`StudyExplanationDepth`). Line: “This changes future explanations, not your level or results.”
3. **Look — homework check** — “Is this what it says?” The photo with the chosen exercise glowing + halo’s reading as text. Dock: Use exercise 2 · Fix words · Retake. No teaching before the words are confirmed.
4. **The path** — always one tap away (place line “Step 3 of 5 · Try”). Done steps fill mint, current glows, Help is marked optional.
5. **Look — explanation** — short text at the chosen depth; Read aloud highlights words as spoken; tap a sentence to repeat; optional “continue automatically”.
6. **Try** — question + answer field. Sentence starters “I think the answer is…”, “because…”, “I’m not sure”. Type, talk (hold the ring) or photo of working. Dock: Help · **Check**.
7. **Feedback** — *Almost* (lilac): names what slipped, keeps what was right, Fix it / Help / Why?. *You got it* (mint): says what the learner did. Never red, never a shake, no points or streaks.
8. **Help** — ladder: 1 Hint · 2 Help me think · 3 Full solution (“Show every step?” confirm, existing copy). Used rungs stay visible. Help never lowers the level.
9. **Keep** — draft cards from the lesson; each needs Keep or Skip. Nothing enters practice automatically. The lesson ring closes.
10. **Practice later** — one card, flip, “How well did you remember?” Again (soon) · Almost (tomorrow) · I knew it (in 4 days) — the next date is shown **before** the tap.

## 3. Kids flow, step by step
1. **Home** — “Hi Mila! What shall we do?” Ask · Learn · Make. Tap once = halo reads it (speaker badge), tap again = go (existing spoken-choice rule; TalkBack keeps its own controls).
2. **Learn · stepping stones** — the five steps drawn as 4 stones (Look, Try, Help, Done; Choose happened on Home). Done stones mint, current stone warm.
3. **Try** — one picture question, max 3 picture answers. Say it (ring caption “Say it”) or tap twice.
4. **Help together** — halo does it *with* the child step by step (“Touch each apple with me…”). The answer is never shown before trying.
5. **Done** — specific, true praise (“You counted all 5 yourself”) + what’s next. No confetti, points or streaks.
6. **Make** — My idea (talk first, “Write it myself” always there) → More about it (max 3 questions, 3 picture choices or “say your own”) → Let’s make it → Read my story (words light up, soft page flip) → Saved in My projects.
7. **Grown-up** — sharing, deleting and settings open the phone’s own screen lock (`KidsGrownUpGate`). Never a quiz.

## 4. Motion (Full · Gentle · Still)
| Moment | Full | Gentle | Still |
|---|---|---|---|
| Step enters | content springs up 26dp, 50 ms stagger, spring 0.62/380 | 200 ms fade + 8dp | 150 ms fade |
| Path / lesson ring | segment fills with spring (~900 ms); ring closes at Keep with one mint bloom | 300 ms linear fill | jumps |
| Read aloud | word highlight follows speech (karaoke) | same | same (highlight is information, not decoration) |
| Kids tap-to-hear | white ripple + speaker badge; 2nd tap = warm bloom | ripple only | badge only |
| Counting / together | each item pops 1.25× as counted (380 ms apart) | dim → full | number labels only |
| Check | mint bloom from Check | fade tint | none |
| Almost | lilac border fades in; **no shake** | same | same |
| Help ladder | rungs slide in one by one (420 ms) | fade | none |
| Practice card | 3D flip 350 ms | crossfade | swap |
| Story page | soft page flip 800 ms | crossfade | swap |
Film: `films/halo-learn-one-path.mp4` (both flows in sync on the same five steps).

## 5. Colours
Mint = right / working / current. **Lilac `#C9B8FF` = almost & help** (new learning colour). Apricot stays “needs you” only;
in Kids the warm accent `#FF9F6B` is the primary button colour (existing Kids rule). Red never appears in Learn.

## 6. Acceptance
- Each screen asks one thing. Max 3 choices at Size 1–2.
- Every word at Size 1 can be heard; nothing requires reading to proceed.
- No age question anywhere; no scores, streaks or leaderboards; help never penalised.
- “You got it / Almost” come from the real check (`StudyVerdict`), never assumed.
- Nothing enters practice without Keep. Kids sharing/deleting = phone lock.
- All controls stay in the bottom 25 % dock; targets ≥ 48dp (Kids 56dp).
