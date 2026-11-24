# halo 13.0 · Batch 5: Study and Kids mode

Checked against repo `slafftrosheen/Halo` at **99aac2f** (23 Nov 2026). This is a design and developer spec, not a build, and nothing has been tested on a device.

The new logic compiles in a standalone Kotlin project, and **7 of 7 tests pass**. One test checks every new Study and Kids string, in English and Russian, against the no-scores rule. The logic has not been compiled inside the Android app, and no screens are built.

Boards: 161 Study · 162 help and checking · 163 cards and tests · 164 Kids home and learning · 165 Kids homework, focus and grown-ups · 166 icons, motion and files.

---

## 1. What the repo has

**Study**
- **Help:** `TutorStage` has ASK, HINT, EXPLAIN, ANALOGY, CHECK_ATTEMPT, PARTIAL_SOLUTION and SOLUTION. `LearningHelpLadder` already states: "neither points nor elapsed time gate any rung."
- **Results:** `StudyVerdict` has CHECKS_OUT, NEEDS_WORK, INSUFFICIENT_EVIDENCE and UNVERIFIED.
- **Review:** `StudyReviewPolicy` schedules cards at 1, 2, 4, 7, 15 and 30 days, with ratings AGAIN, ALMOST and KNOWN. `knownStreak` and `wrongStreak` are stored for scheduling.
- **Stages and phases:** `LearningStage` has 7 stages. `StudyLearningPhase` has EXPLORE, TRY, REVIEW and COMPLETE.
- **Other tools:** the photo homework coach, explain-back review, rehearsal, listen mode, the exam plan and the course outline.

**Kids**
- **Age bands:** `KidsAgeBand` has EARLY (4–6: 72 dp buttons, 2 choices, 2 stops), PRIMARY (7–9: 56 dp, 3, 3) and OLDER (10–12: 48 dp, 3, 4).
- **Adventures and topics:** the adventure stops are LEARN, MAKE, FIND and TELL. The topics are animals, counting, nature and dinosaurs. There are 6 skies.
- **Homework:** `KidsHomeworkWindow` defaults to 16:00–18:00, and homework photos are stored in noBackupFilesDir.
- **Focus timer:** 5, 10 or 25 minutes.
- **Grown-ups:** `KidsGrownUpGate` uses the device's own screen lock.

13.0 adds **no new storage**.

## 2. Study (board 161) · `ui/study/v13/HelpPlain.kt`

- **Four starts:** a page or homework (photo), a file or my notes, a topic, or a test. You can also just say it.
- **One path:** Look → Try → Check → Done.

| Repo | Shown |
|---|---|
| START · DISCOVER · EXPLAIN · EXPLORE | Look |
| PRACTICE | Try |
| REFLECT | Check |
| CONTINUE | Done |

  `StudyLearningPhase` maps onto the same four steps.
- **Set up** asks three choices: the page's language, the language for explanations, and "Show me first / Let me try first / Read it to me".
- **The ending** celebrates without points: "You got through all 6. Two needed a hint. That is how learning goes." halo then offers cards, a summary in Notes, and a practice time in Planner, where you choose the time.
- **By depth:**
  - **Simple:** the four starts and the path, with read-aloud on.
  - **Standard:** adds set-up, cards and test plans.
  - **Pro:** adds the course outline, citations, your own review steps and export.
- **No points, scores or streaks anywhere.** `knownStreak` is never shown.

## 3. Help and checking (board 162)

**Help ladder.** Every rung is always available, for everyone, kids included. Only "Show it all" asks once: "You can still try first."

| TutorStage | Shown |
|---|---|
| ASK | Remind me |
| HINT | A hint |
| EXPLAIN | Explain it |
| ANALOGY | Another way |
| CHECK_ATTEMPT | Check my try |
| PARTIAL_SOLUTION | Show one step |
| SOLUTION | Show it all |

**Results use meaning colours and never red.** Red is only for a broken source.

| StudyVerdict | Shown | Colour |
|---|---|---|
| CHECKS_OUT | Checks out | mint |
| NEEDS_WORK | One step needs a fix (says which) | apricot |
| INSUFFICIENT_EVIDENCE | I can't tell from your page | neutral |
| UNVERIFIED | halo's guess, not checked | lilac |

- **Homework photo:** marks the one answer to look at again, and offers a hint. The photo stays on the phone.
- **Explain it back:** spoken, followed by two tips. The recording is deleted after the tips unless you keep it.
- **Where it came from:** every explanation shows its page. If no source was used, it says so.

## 4. Cards and test plans (board 163) · `ReviewPlain.kt`, `ExamPlan.kt`

- **The three buttons** are Again, Almost and Got it. Each says when the card comes back *before* you tap it, using the repo's own policy.
  - **Again:** one rung down. After two misses in a row, it goes back to 1 day.
  - **Almost:** stays on the same rung.
  - **Got it:** one rung up, or two after two "Got it" answers in a row.
  - **Example:** for a card on the 4-day rung, Again is 2 days, Almost is 4 days and Got it is 7 days.
- **Done for today** shows the count and time (about 40 seconds a card) and when cards come back. Missed days just wait. There is nothing to lose.
- **Test plan:**
  - The cards are spread evenly over the study days, with weekends as rest days by default.
  - The day before the test is light: a quick look and nothing new.
  - Nothing is booked until you tap "Put it in Planner".
- **Practise out loud** (rehearsal) and **Listen** are both kept. Recordings are deleted when the sheet closes, unless you keep them.

## 5. Kids home and learning (board 164) · `ui/kids/v13/KidsAgeLayout.kt`

- **Home always has three doors:** Ask Fox, Learn and Make. There is also "Talk to Fox".
- **A daily adventure** as a path of stops. How many stops depends on age: 2, 3 or 4. The buddy hops along. There is no rush and there are no points.
- **Sized by age:**

| Age | Buttons | Choices | Text | Read aloud |
|---|---|---|---|---|
| 4–6 | 72 dp | 2 | 20 sp | always |
| 7–9 | 56 dp | 3 | 18 sp | on tap |
| 10–12 | 48 dp | 3 | 16 sp | the buddy, on tap |

- **Wrong answers** get "Look again" and a hint, never a buzzer.
- **Stories:** the child picks the idea, Fox helps write it, and the child chooses or says what happens next.
- **My sky:** 6 skies, plus the buddy.

## 6. Homework, focus and grown-ups (board 165) · `ui/kids/v13/KidsWeek.kt`

- **Homework:**
  - Help is available only inside the homework window.
  - It gives hints and "look again", never the answer, even when the child asks for it.
- **Focus:** 5, 10 or 25 minutes, ending with a soft chime. Stopping early is fine.
- **Grown-up space:**
  - It is unlocked with the phone's own lock and locks again after 2 minutes idle.
  - It holds: the child's week, homework time, kids-safe sources, time in halo, and age and reading settings.
- **The child's week** shows topics explored, things made, and where hints helped, plus "something to try together". It never shows a score or ranking, and it stays on the phone.
- **Hand back:** the child sees a calm waiting screen, then "I'm back!".
- **A test enforces the wording:** `KidsWeek.FORBIDDEN` blocks score, points, streak, rank, behind, fail, wrong, lazy and worse, plus Russian stems. The stems match only at the start of a word, so «карточки» (cards) passes and «очков» (points) does not.
  - **Known gap:** «очки» also means glasses. This needs a native-speaker review.

## 7. QA checklist

- [ ] Every help rung opens with 0 tries and 0 seconds on a question.
- [ ] No result, in Study or Kids, uses the error colour.
- [ ] The Again / Almost / Got it labels match the real next due date after tapping, for all 6 rungs and both streak cases.
- [ ] A test plan with 1, 2 and 30 days to go, and with every day a rest day, still has a light last day.
- [ ] Recordings are gone from storage after closing the sheet without Keep.
- [ ] Kids, ages 4–6: buttons measure 72 dp or more, and every screen reads aloud.
- [ ] Homework help outside the window says "later" kindly. Asking for the answer gives a hint.
- [ ] The grown-up space locks after 2 minutes idle and when the app goes to the background.
- [ ] The child's week contains no forbidden words (the test covers strings, not generated text; check generated text by eye).
- [ ] A privacy review of the homework camera and spoken recordings.
- [ ] A Russian native-speaker review of strings_learning_13.xml.

## 8. Files

| Path | What |
|---|---|
| ui/study/v13/HelpPlain.kt | Help ladder, plain results, one path |
| ui/study/v13/ReviewPlain.kt | When a card comes back |
| ui/study/v13/ExamPlan.kt | Day-by-day test plan |
| ui/kids/v13/KidsAgeLayout.kt | Sizes by age, three doors, idle lock |
| ui/kids/v13/KidsWeek.kt | Grown-up week, no-score check, homework rule |
| test/…/study/v13/StudyKidsV13Test.kt | 7 tests, passing |
| assets/learning/learning-13.json | All rules as data |
| res/values(-ru)/strings_learning_13.xml | 76 strings, EN + RU |
| assets/halo/artwork/ic, res/drawable | 27 icons |
| res/raw/halo13_motion_* | 5 Lottie animations × dark/light |
| docs/design/halo-13/source | Board, icon and motion sources |
