# halo 13.0 · Batch 3 — Sources manager + Planner

Checked against repo `slafftrosheen/Halo` at **99aac2f** (23 Nov 2026). This is a design and developer spec, not a build, and nothing has been tested on a device.
Boards: 149 Your sources · 150 sources at work · 151 Planner views · 152 adding and help · 153 tablet, family and kids · 154 icons, motion and files.
It builds on batch 1 (depths, plain words, meaning colours) and batch 2 (asking for access only when needed).

---

## 1. What the repo has

**Sources.** `everyday/collectors/CollectorModels.kt` defines:
- `CollectorSource`: id, title, publisher, kind, category, language, endpoint, selected, enabled, **muted**, refreshMinutes 15–1440, place, **childApproved**
- `CollectorKind`: 5 local kinds, plus FEED, WEATHER, ALERTS, PUBLIC_CALENDAR and AIR_QUALITY
- `SourceCategory`: PERSONAL, LATVIA, WORLD, SCIENCE, TECHNOLOGY, WEATHER, OFFICIAL, CALENDAR and CUSTOM
- `CollectorAttemptState`: 11 technical states

`StarterSourceCatalogue` has **18 starters**:
- LSM ×4, BBC World, NASA, Ars Technica, The Verge, Hacker News
- CERT.LV, Meteoalarm Latvia, ECB, GOV.UK, WHO
- USGS, arXiv AI, arXiv Astro, NASA APOD

Study sources (`study/StudySources.kt`) and Kids learning sources live on other screens.

**Planner.** `everyday/organizer/OrganizerModels.kt` defines:
- `OrganizerKind`: EVENT, TASK, REMINDER, ALARM, TIMER
- `OrganizerCalendar`: Me, Family space, Other calendars
- `TaskPriority`: LOW–URGENT
- per record: `leaveBeforeMinutes`, `parentId` (for steps) and `studyExam`

`PlannerView` offers DAY, WEEK, MONTH, AGENDA and EASY.

13.0 adds **no new storage**. Everything maps onto these models.

## 2. Your sources — `ui/sources/v13/SourceManager.kt`, `assets/sources/sources-13.json`

**One list, six groups.** The groups are: Your own things · News & reading · Weather & warnings · Calendars · Study books & files · Kids-safe.

**Plain state on every row.** The 11 technical states map to 9 plain ones:

| Repo | Shown |
|---|---|
| CURRENT, NOT_MODIFIED | Up to date (mint) |
| FETCHING | Updating… |
| LOCAL_ONLY | On this phone (mint) |
| OFFLINE | Waiting for internet |
| BACKOFF, QUOTA | Taking a break · tries again at 14:20 |
| FAILED, 1–2 times in a row | Can't reach it right now (red: this is an error) |
| FAILED, 3+ times in a row | Needs you (apricot) |
| DISABLED, CANCELLED, or `enabled = false` | Paused |
| NEVER | New · first update soon |

**Who stands behind it, never a score.** The labels are Official · Public news · Science · Community · Yours · Added by you. The starter mapping is in `sources-13.json`. Community (Hacker News) carries the line "posts by people, ranked by people — check before you trust."

**What halo uses it for.** Each source has three switches: Today's brief (`selected && !muted`) · Answers (`selected`) · OK for kids (`childApproved`, which needs the grown-up PIN). **Tell me right away** is available only for official ALERTS, at most 3 a day.

**Other things a source shows:**
- **How often:** every 15 minutes, every hour, a few times a day, or once a day.
- **Used this week:** a count from the "What halo used" records.

**Quiet controls:**
- **Keep, but don't bring it up** (muted)
- **Pause** (enabled = false)
- **Remove**, with a confirmation. Old answers keep their links.

**Your mix** is a small page showing categories and languages. It is shown, never judged, and halo never nudges you to it.

**Pro / tablet:** a health table showing state, last update, how often, use over 7 days and kids approval.

## 3. Adding a source — `AddSource.kt`

There are six ways in:
- paste a link
- browse ready sources (the 18 starters)
- follow a topic
- add a city (weather, air and pollen)
- add a calendar (a link or an ICS file)
- add a book or file (Study)

**What happens with a link:**
1. halo checks it on the phone: https only.
2. It finds the site's news feed (`rel="alternate"`) and rejects feeds over 2 MB.
3. It shows 3 recent items, how often the source updates, and what halo will use it for.
4. The person taps **Add**.

If the site has no feed, halo offers "Save this page to read".

Only a grown-up with the PIN can mark a source as kids-safe.

## 4. Sources at work
- **What halo used:** every answer has a sheet listing its sources. Tap one to open it or keep it out of answers.
- **When a source breaks:** say what happened → what halo did (keeps the last 40 stories and tries again in an hour) → what the person can do: Find the new address · Pause · Remove.
- **Today's brief settings:** one switch per source, plus "Tell me right away" for official warnings.

## 5. Planner views — `assets/planner/planner-13.json`

One switcher: **Today · Day · Week · Month** (plus Agenda on Pro).

| View | Maps to | Depth | What's new |
|---|---|---|---|
| Today | EASY | Simple and up | Leave by card, must-do tasks, add by voice |
| Day | DAY | Standard | a timeline you can drag, visible free time, a now line, Leave by ribbons |
| Week | WEEK | Standard | Me, Family and Tasks in colour; free days |
| Month | MONTH | Standard | dots instead of text; tap a day to peek |
| Agenda | AGENDA | Pro | calendars, time zones, ICS import and export, repeat rules |

**Plain priorities:** URGENT → *Must do today* · HIGH → *Important* · NORMAL → *Normal* · LOW → *Some day*.
**Some day** is a list of ideas with no date and no reminders.

## 6. Timeline — `DayTimeline.kt`
- Items snap to **15 minutes**. Overlapping items sit side by side in columns.
- A task dropped on the timeline becomes a **time-block**. It stays a TASK and gains a time.
- Events from **other calendars** (`calendarUid != null`) are **fixed**. Dragging one makes it spring back, with the message "This comes from another calendar".
- A **Leave by** ribbon appears above anything with a location and `leaveBeforeMinutes`.

## 7. Adding and help
- **Natural add** (`NaturalAdd.kt`, EN and RU, rule-based, works with no AI brain).
  - Example: "Swimming with Mia every Thursday at 5" becomes a draft: weekly Thursday at 17:00, Family calendar.
  - Guesses (such as "at 5") are shown in **apricot** with "I guessed 5 pm. Change it?".
  - **The preview card always shows**, and nothing is saved without a tap.
  - An AI brain may refine the draft, but it never skips the preview.
- **Find a time** (`FindTime.kt`).
  - Gives 3 options, one per day, between 08:00 and 21:00, with a 15-minute buffer.
  - **Never auto-books.** Offers "Make it weekly".
- **Too much today?** (`TooMuchToday.kt`).
  - When the day is 90 % or more full, an apricot card suggests what could move.
  - **Must do today** tasks are never suggested. The person ticks what moves. halo never moves anything by itself.
- **Steps:** a task can have steps (`parentId`) and links to notes and sources.

## 8. Tablet, family and kids
- **Tablet:** the Week view with a to-do and Some day side panel to drag from. Keys: T, D, W, M, N (new) and F (find a time).
- **Family:** one column per person (`OrganizerCalendar.FAMILY`). A clash card appears when two people need the same grown-up at the same time.
- **Kids — My day:** pictures, read by the buddy. The child sees only their own day and their homework window (`KidsHomeworkSchedule`), and can't edit grown-up plans. No points.
- **Glasses:** the next thing and Leave by only, on one line.

## 9. Icons, motion and strings

**21 icons:** src_news, src_official, src_science, src_community, src_own, src_book, kid_safe, fresh, paused_src, add_link, your_mix, timeline, week_view, month_view, drag_block, find_time, leave_by, too_much, someday, repeat, family_lane.

**5 Lottie animations** (256 × 256, 30 fps, dark and light):

| Name | Frames |
|---|---|
| source_refresh | 75 |
| add_source | 75 |
| timeline_drag | 90 |
| find_time | 90 |
| leave_by | 90 |

**Strings:** `strings_sources_planner_13.xml` has 51 strings in EN and RU. The Russian avoids gendered past tense for halo ("Похоже, это 17:00").

## 10. QA
- [ ] Every CollectorAttemptState shows its plain label. Red appears only for a FAILED state.
- [ ] Removing a source leaves old answers' "What halo used" links working.
- [ ] Kids-safe can't be turned on without the PIN.
- [ ] Natural add never saves without the preview, in EN and RU, and guesses are marked.
- [ ] Find a time never books by itself. Too much today never offers must-do tasks.
- [ ] Fixed events can't be dragged. Tasks dropped on the timeline keep their kind.
- [ ] The 5 tests in `PlannerV13Test` pass. A native Russian speaker reviews the strings.

## 11. Open decisions
1. Should Study books and files live in Sources, or only show there as a link to Study?
2. Topic following: which sources are allowed (starters only, or open web search)?
3. Family clash rules: does it need a "who drives" field, or is it free text?
4. Should Week start on Monday everywhere, or follow the phone's region?
