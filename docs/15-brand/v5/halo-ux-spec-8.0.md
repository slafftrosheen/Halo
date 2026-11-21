# halo 7.5 · Toolkit, widgets, wallpapers, functions — UX/UI design spec

Design only. This document explains what each new piece is for, how it looks, how it behaves, and the rules it keeps. Boards 53–58 show every screen. Nothing here has been built or tested on a phone.

## 0. Rules that do not change
- Bottom 25% = halo zone (place line, actions, nav + labelled ring). Top 75% = work.
- One filled (primary) button per dock. Buttons name the action ("Save", "Keep", "End focus").
- Colour meanings: **mint** listening / working / done · **Apricot** needs you · **lilac** help · **red** error only.
- Hard stops (from 7.4): paying, sending to a person, deleting, posting always ask first — in every tool.
- Every tool has Show me, Easy view, Watch-then-try and its own "make it yours" (7.3 layer).
- Lowercase "halo" everywhere.

## 1. The toolkit (board 53)
Workspace grows from 5 to **9 tools**, grouped by what you want to do, not by technology:

| Group | Tools |
|---|---|
| Make | HaloStudio, HaloCanvas, **HaloSheets** |
| Write & capture | HaloNotes, **HaloScan**, **HaloRecord** |
| Plan, learn, keep | **HaloPlanner**, HaloStudy, HaloMemory |

- Tools show as big named tiles, three per row. New tools have a one-time "new" tag.
- Each tool has its own **identity tint** (Scan yellow, Planner sage, Record sky, Sheets sand). Tints only mark *which tool*; they never replace the state colours.
- Long-press a tile → pin to the dock. Hidden tools still open when you ask halo.

## 2. HaloScan — the camera tool (board 53)
**For:** reading small print, scanning documents, translating, identifying things. Built for older eyes and first-time users.
- **One frame, four modes:** Read · Document · Translate · What's this? as chips under the camera. A yellow frame plus one plain line ("Hold still — reading").
- **Read:** text appears large (19sp+) in the work area with the lilac read-along highlight; tap any word to start there. Dock: Pause (primary) · Slower.
- **Send it to:** halo suggests the right next tool from what it read (two times in a leaflet → Planner reminders).
- **Translate:** translated labels sit on the real words so the page stays recognisable; tap a label to hear it.
- **Privacy:** the frame is processed on the phone when possible; the answer chip says "On this phone" or "Used online".

## 3. HaloPlanner — the calm organiser (board 54)
**For:** the organiser that left HaloScreen. The in-app home for days, reminders and repeats.
- **Week strip:** seven big day tiles with dots for busyness; today is sage. Below: today's items with time, place and "leave at" time.
- **Add by talking:** "Every Thursday evening remind me…" → a check card: What / When / Remind / Starts. Guesses are explained ("evening" = 19:00); tap any line to change it.
- **At most one nudge:** e.g. "15:00–18:00 is empty". Follows the Next-for-you levels from 7.4.
- Easy view: today only, big rows, read aloud.

## 4. HaloRecord — conversations into notes (board 54)
**For:** meetings, doctor visits, lectures, stories from grandparents.
- **Never secret:** while recording the ring caption says "Recording", the mint edge stays on, the timer is large, and "on this phone" is shown.
- **Live words** with speaker initials and times.
- **In short:** after stopping, a summary split into *Decided* and *To do*. Send to Planner turns to-dos into reminders; sending to a person is a hard stop.
- Glasses (7.4) can show captions from the same feature.

## 5. HaloSheets — tables without formulas (board 55)
**For:** takings, costs, lists with numbers — people who would never open a spreadsheet.
- Buttons instead of formulas: **Add up · Sort · Add a row · Ask about it**.
- Totals live in a sand "Together" row; numbers right-aligned and tabular.
- **Ask the table:** "Which Saturday was best?" → one-line answer + the reason + the row highlighted + a simple bar chart.

## 6. Tools working together (board 55)
- Any tool can hand its result to another. The hand-off is drawn as tool icons on a dotted path (Scan → Sheets → Planner), then shown as a mini plan with steps (7.4 plan grammar).
- **Your tool shelf:** choose up to 4 tools for the dock; the rest stay on the shelf. Easy view shows only those four as big tiles.

## 7. New widgets (board 56) — 13 new
| Set | Widgets (size) |
|---|---|
| Capture | Scan 2×2 · Record 2×2 · Quick note 4×1 · Translate 2×2 · Air & pollen 2×2 |
| Plan & move | Planner week 4×2 · Focus 2×2 · Little habits 2×2 · Next ride 4×1 |
| Family & learning | Our home 4×2 · Learn 2×2 · Mila's sky (Kids) 2×2 · Your things 4×1 |

Rules (same as 6.2): glass card r28, one clear action, small halo ring, freshness line ("updated 2 min ago"), honest empty states ("Choose a source"), Apricot only for needs-you, tap opens the matching tool, long-press opens its settings. **Little habits** uses dots — no streaks, no scores. **Mila's sky** lights stars for *trying*, never for being right. On light wallpapers widgets switch to light glass.

## 8. More live wallpapers (board 57) — 12 in total
| New wallpaper | Tied to | Reacts to halo |
|---|---|---|
| Northern Forest | time of day | moon turns Apricot when needed; mint mist while listening |
| Baltic Dunes (light) | real wind | ring tint follows state |
| Rain Glass | real rain where you are | ring glows while working |
| Lumen Garden | things done today | a flower opens per done item; resets overnight |
| Night Train | time of day | slower and darker after 22:00 |
| Seasons (light/dark) | real season + weather | blossom, leaves, autumn, snow |

All follow the motion setting (Full / Gentle / Still), go still on battery saver, never flash, never use red. Kids get Lumen Garden and Seasons at half speed.

## 9. New functions (board 58)
- **Focus:** big timer + ring; other dock tabs fade, ring caption "Focus", messages are held in a list. Starred people and alarms still get through.
- **Routines:** built like sentences — *When* (sage) → *Then halo* (mint) blocks with icons. "Try it" runs once without saving. Privacy line explains what is used.
- **Family space:** shared lists, plans and notes only — never chats, memory or photos. Initials + colours show who added what. Kids join via a grown-up.
- **Magnifier:** steady live lens up to 6×, allergens/warnings highlighted yellow; Read aloud, Freeze, High contrast always one tap away.

## 10. Where things live
- Tools → Workspace (and the dock shelf). Focus → ring long-press menu + Focus widget. Routines, Family space → Places. Magnifier → HaloScan's "Read" mode at higher zoom, plus a Quick Settings tile.
- Wallpapers → Make it yours › Wallpaper (second row). Widgets → Places › Widgets & sources.

## 11. Acceptance (design review checklist)
1. Every new screen keeps the 25/75 split and one primary button.
2. Recording is impossible to miss (caption, edge, timer).
3. No new colour takes a state meaning.
4. Every tool works in Easy view with ≤4 big buttons.
5. Every widget has a freshness line and an empty state.
6. Wallpapers never animate faster than the motion setting.

---
# 8.0 · Final upgrade additions (boards 59–64)

## 12. New tools for every age (board 59)
- **Scan · Easy view:** four 132dp tiles — Read it to me (filled, most used), Translate, Keep a paper, What is this? One line: "Just point the phone."
- **Planner · Easy view:** today only; big time, plain words, a round tick per item; actions: Add by talking, Read my day.
- **Kids · What is this?:** point → "🍃 A leaf!" + one sentence, 26sp on a warm card. Kids-safe source chosen by a grown-up; no web, no search box.
- **Kids · My day:** a day made of pictures set by a grown-up; "now" glows Apricot, ticks light mint; no points, no streaks.

## 13. HaloAlbum & HaloVault (board 60) — tools 10 and 11
- **HaloAlbum:** find photos by saying what you remember ("Gran's birthday last summer") — faces, places, words, dates, on the phone. **Make a story:** best photos + title + soft music; Send to family is a hard stop.
- **HaloVault:** warranties, receipts, policies, documents in one locked list (fingerprint). Shows what each paper is and when it ends; Apricot only when action is needed soon. Add from a photo: halo fills What / Bought / Ends / Remind and says where it read them. halo never reads the Vault unless you open it.

## 14. Smarter widgets (board 61)
- **Lock screen:** up to four small widgets under the clock (next event, next ride, rain, focus). Safe info only.
- **Bedside:** while charging at night — dim warm clock, tomorrow's alarm + weather in one line, notifications held; double-tap to whisper to halo.
- **Smart stack:** several widgets in one place; halo shows the right one by time and habit; dots show more; swipe to change.
- **Edit in place:** long-press → mint outline + a sheet: Size (Small/Wide/Big), Style (Glass/Solid/Clear), Shows (This week/Today/Next only). Live preview.

## 15. Wallpapers — 18 in all (board 62)
New: Riga Rooftops, Deep Sea, Paper Cut, Your Photo, Meadow Wind, Ink Wash.
- **Picker in three rows:** Live & lively · Calm & light · Tied to the world.
- **Schedule:** up to three wallpapers for morning / day / night; 2-second fade, never while you're looking. Off by default.
- **Your Photo:** pick a photo, choose blur and ring position; stays on the phone.
- Paper Cut and Ink Wash are near-still — the recommended default for Easy view and motion-sensitive people.

## 16. Functions for real life (board 63)
- **Morning check-in:** set up together, visible to the person, turned off by them any time. One reminder, then one message to family. No tracking, no alarms.
- **On the move:** when driving, the screen becomes one direction line, one message at a time and two 120dp buttons. Suggested replies still need one tap.
- **Wind down:** warm dim screen, notifications wait, one sentence about tomorrow, optional story or sleep sounds with a timer.
- **Emergency info:** allergies, medicine, who to call, language — line by line, on the lock screen. Apricot-brown heading; red stays for errors.

## 17. halo at a glance (board 64)
One map of places, 11 tools, 22+ widgets, 18 wallpapers and all functions, with the four rules: 25/75 · four colours, four meanings · hard stops · every age.
