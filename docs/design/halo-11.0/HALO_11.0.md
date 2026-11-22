# halo 11.0 · Kids, homework coach, photo coach, Library, maps, AI-built pages and new tools

**Status:** this is a design and developer specification. Nothing has been built or tested on a phone, tablet or device. The prototype and boards were checked automatically for clipped and overflowing text in English and Russian. Nobody has reviewed them by eye. A native Russian speaker hasn't checked the Russian yet.

**New boards:** 113–116, each in English and Russian.
**Prototype:** `prototype/halo11.html`. Use the tabs for Kids · Study · Library · Maps · Today, plus EN/RU and Motion/Still. The 10.6 Kids and Study flows are still inside it.
**Films:** halo-kids-en/ru, halo-study-en, halo-library-en, halo-maps-en, halo-today-en.
**Dev:** CoachSession.kt, halo-library.json, halo-instruments-11.json, halo-blocks.json, halo-maps.json, halo-routes.json (26 routes), halo-flows.json and halo-flows.ru.json (32 flows), and strings_11.xml in English and Russian (325 keys).

---

## 62 · A livelier Kids flow

- **buddy greets Mia by voice** in a speech bubble on Kids Home, with a face that reacts: happy, thinking or cheering.
- **Today's adventure** sits on top. It has three stops (learn, make and find outside), built only from approved kids sources.
  - A finished stop becomes a picture, and the whole adventure becomes a page in My book.
  - buddy cheers once, gently. Under Still there's no confetti.
- **The four big choices stay** (Learn · Make · What is this? · Play with buddy). There are still no points, streaks or scores.
- **One name:** "What I learned" and "My book" are now one book, called My book.

## 63 · Homework helper (Kids): questions, not answers

The grown-up switches on homework time, with times and subjects, from their own phone. During homework time, the adventure on Kids Home is replaced by "Show me your homework".

1. **Show it.** Mia holds the sheet up to the camera or looks at it through her glasses.
2. **halo reads it** and lists the questions. Mia picks where to start.
3. **buddy asks small questions.** For 27 + 15:
   - "What is 7 + 5?"
   - "Which number goes in the ones place?"
   - "How many tens, with the one you carried?"
4. **When Mia is stuck or wrong:**
   - A wrong choice gets "Not quite — let's look again", then a smaller question with a picture (dots, rods or a number line). Red is never used.
   - **"I'm stuck"** makes the question smaller.
   - **"Just tell me"** is answered kindly: "I won't give you the answer — but I can make it smaller."
5. **Mia writes her own answer** on paper and types it in. halo checks her thinking. If it's wrong, halo names the step to look at again, never the number.
6. **Mum gets a kind summary:** "Mia did 1 of 3 questions herself. Carrying needed one hint. No answers were given."

**Hard rules:**
- halo never writes a final answer and never fills in the sheet. Before display, any output that matches the answer is blocked until the child has typed it.
- halo never sends a photo of answers, and there are no grades.
- A homework photo stays on the phone unless the child keeps it.
- A question that's unsafe or unclear gets "Let's ask a grown-up."

## 64 · Photo coach (Study, adults)

The entry point is Study › "Coach me on a photo", or Ask halo with a glasses photo.

- **Choose how:**
  - **Coach me** is the default.
  - **Explain the idea** gives a short explanation, then you try.
  - **Show the answer** is always available to adults and is shown plainly. halo then offers "Try a similar one".
- **Coach steps:**
  1. **What do you know?** Tap the givens. halo asks about anything that's missing or extra, such as "Is the distance in the problem?"
  2. **Which idea fits?**
  3. **Each step.**
  4. **Your answer.** halo checks your working and units, not just the number.
- **The help ladder stays:** Hint → Explain → Show answer. Using help never changes when cards come back.
- **Keep a card:** a solved problem can become a card you approve, and it goes into the course collection.
- **halo ink** mirrors the problem and your steps as you go.

## 65 · One Library: organising everything

The problem was that the same things lived in many places under different names: album, glasses Inbox, vault, memory pages, My book and "What I learned", Make cards and Keep.

- **Where it lives:** Home › **Library** (a tab). The Home tabs are now **Today · Library · Tools · Reads**.
- **One Library:** every saved thing is one item of one kind. The kinds are Notes · Photos · Files · Scans · Cards · Places · Recordings · Plans · People. Tools open items; the Library keeps them.
- **One name everywhere,** following the pattern **what · where · when** (for example, "Receipt · Rimi · 12 Nov").
  - halo proposes a name and never changes one you typed.
  - The old file name is kept as "Was called" for 30 days.
- **Collections** cut across kinds (a trip, a course, the house).
  - Adding an item to a collection never moves or copies it.
  - halo can suggest a collection ("12 glasses photos from Riga → Trip to Riga?"). You add them or choose Not now, and Undo lasts 10 s.
- **Private** is a lock on any item or collection, unlocked with your fingerprint. It replaces halo vault as a separate place. Private items are hidden from search, suggestions and Today. AI keys stay in their own key vault and never appear in the Library.
- **Find anything** searches every kind at once, grouped by kind. When it can, halo adds a short answer with its source, for example "You spent about €84 at Rimi in November, across 5 receipts."

**Renamed:**

| Before | Now |
|---|---|
| Places (news sources tab) | **Reads**, because "Places" now means real places on a map |
| halo vault | **Private** (a lock on any item) |
| Album › Library sidebar, glasses Inbox | **Library › Photos** (filter: From glasses) |
| "What I learned" + "My book" | **My book** |
| Memory pages | **Notes** (linked). Memory is now only what halo knows about you |
| Save / Keep / Add / Store | **Keep** (to the Library) · **Add to** (a collection) |

## 66 · halo maps

- **Entry points:** Home › Tools › maps, the route block on Today, a Place item, Ask halo, or "halo, take me home" through the glasses.
- **Leave-by time** comes from your calendar.
- **Turn by turn** shows a big next-turn card on the phone. The glasses lens shows the arrow and distance and nothing else, and the buds speak the turns.
- **Arrived** shows what's next at that place and what you saved there before.
- **Search in words**, for example "pharmacy open now", lists open places with the nearest first.
- **Your photos here** shows glasses photos on the map where they were taken. This happens on the phone only.
- **Share arrival** asks first. It sends a time, not where you are, and stops when you arrive.
- **Privacy:** your location stays on the phone, and location history is off by default.
- **Kids** get no map. The grown-up sets Safe places in Family & Kids.
- **Open decisions:** the map data provider, the live transit times source, and a privacy review of Safe places.

## 67 · Pages halo builds (dynamic content)

- **Today is made from blocks:** your day with the route, weather advice, cards due, a short read, and a Library suggestion. Afternoon and evening sets change: evening shows tomorrow's first thing and when to wind down.
- **Every block** names its source and has a ••• menu with **Pin · Less like this · Why am I seeing this?**
  - "Why" gives a plain reason, for example "You have Coffee with Anna at 10:30 at Café Rīts. It's a 12-minute walk…"
- **Blocks never move or resize while you can see them.** New ones arrive below with a **New** chip, and pinned blocks stay as the time of day changes.
- **The same block system builds** Reads, tool start pages ("continue Market list"), Library suggestions, Kids adventures (kidsSafe blocks from approved sources only), homework time and the Study coach offer.
- Private items are never used to build a page. When offline, the page is built on the phone and says so once.

## 68 · Six new tools

All new tools are lowercase and desktop-style, like the others. There are 39 new instruments in total; `dev/halo-instruments-11.json` lists each instrument with its levels and whether it asks first.

| Tool | Modelled on | Highlights |
|---|---|---|
| **halo maps** | a desktop map app | Search in words, leave-by, route, turn by turn, share arrival (asks first), layers, offline area |
| **halo coach** | a workbook | capture, What do you know?, step questions, I'm stuck, check my thinking, answer box, show answer (adults only), similar problem, keep a card, grown-up summary |
| **halo calc** | a desktop calculator | history tape, ask in words ("split €62 by 3"), units and currency, graph |
| **halo measure** | a ruler and level | camera ruler (shown as an estimate), level, room outline (Pro), keep to Library |
| **halo translate** | a two-pane conversation | conversation (the buds play the other side), camera text with captions in the glasses lens, offline English and Russian, phrasebook |
| **halo focus** | a timer | visual ring (Kids version shows a shrinking picture), study blocks with no streaks, quiet while focusing |

In Kids, calc's "Explain this" opens coach instead, so the child gets questions, not answers.

## Still open

- **Carried over:**
  - Which children's source the adventure and "What is this?" use
  - The tablet menu position
  - Rights to news photos
  - Who maintains the list of AI services
  - The privacy and security reviews
- **New:**
  - Which map data provider to use
  - A privacy review of homework photos and Safe places
  - Whether "Was called" should last longer than 30 days
- **Brand book:** not updated in this round.
- **Usability:** the usability tasks on board 112 need a build. Add two tasks: "finish one homework question without being told the answer" and "find last month's receipt".
