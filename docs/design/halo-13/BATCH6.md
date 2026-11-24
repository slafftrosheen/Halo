# halo 13.0 · Batch 6: book, Russian boards, prototype, films, one pack

Repo check: 99aac2f (23 Nov 2026), unchanged since batch 1. This pack only adds files.

## What is new in this batch
- **Brand book v8.0** (separate download, 484 pages). It replaces v7.0.
  - p 1: new cover
  - pp 2–347: brand book v7.0, unchanged (its contents page still covers chapters 1–27)
  - p 348: Part II contents · p 349: what later releases replace
  - Chapter 28, halo 10.6 and 11.0, starts on p 350 (pages 43–57 of the 9.5–11.0 additions book)
  - Chapter 29, halo 12.0, starts on p 366 (pages 3–50 of the 12.0 book)
  - Chapter 30, halo 13.0, starts on p 415 (new: an overview, the five batches with all 30 boards, motion, the prototype, 24 Russian boards, open items, a glossary and the file map)
  - Reused pages keep their original footers. Use the page numbers on the Part II contents page.
- **Russian boards:** `boards/ru/`, 24 boards. These are every board people see. Board 137 (the brief) and the five developer boards (142, 148, 154, 160, 166) stay in English.
  - Translations: `source/ru13.json` (1,570 strings). It was built from the 13.0 strings files and new translations in `source/ru-translations/*.tsv`.
  - `render_ru.py` swaps the text, and `fit.js` makes text slightly smaller where Russian runs longer. No board was left with overflowing text.
  - Code, names and enum ids stay in English.
- **Clickable prototype:** `prototype/halo13.html` with seven journeys:
  1. First open
  2. Your depth
  3. Sources
  4. Planner
  5. Canvas (real drawing; the sheet counter goes up without a message)
  6. Study
  7. Kids
  - Options: `?lang=ru`, `?flow=1…7`, `?auto=1|all`, `?still=1`.
  - Stills are in `prototype/stills/`.
- **Films:** `films/halo13-prototype-en.mp4` (141 s) and `films/halo13-prototype-ru.mp4` (140 s). Both record the demo that plays itself.

## Checks
- An automatic run of every prototype journey in English and Russian found no text outside the phone and no script errors. Nobody has checked it by eye.
- Book pages were checked automatically for broken images and text running off the page.

## Not done yet
- The Android build of batches 1–5. The screens aren't built.
- Testing on a device.
- A review of the boards by eye.
- A native Russian review, including the label for "points" (баллы, never очки).
- The security review for moving from an old phone.
- The privacy reviews for homework photos and the camera on glasses, spoken rehearsal recordings, and Safe places.
