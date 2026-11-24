# halo 13.0: the complete pack (batches 1–6)

halo 13.0, "made for everyone": anyone can use halo without help. Every power feature stays, but it's hidden until someone needs it.

This is a design and developer specification, not a build. The pure Kotlin rules compile on their own and their tests pass. The Android screens aren't built, and nothing has been tested on a device. The Russian text hasn't been reviewed by a native speaker.

The pack was checked against github.com/slafftrosheen/Halo at **99aac2f** (23 Nov 2026). Its folders mirror the repo, so you can copy this folder over the repo root. It only adds files and overwrites nothing that already exists.

## Read first
- **Brand book v8.0 (PDF, 484 pages):** a separate download because of its size. It replaces v7.0. Chapter 30 (p 415) is halo 13.0.
- `docs/design/halo-13/prototype/halo13.html`: the clickable prototype. Add `?lang=ru` for Russian.
- `docs/design/halo-13/films/`: the prototype demo in English and Russian.

## Specs (docs/design/halo-13/)
| Batch | Spec | Boards |
|---|---|---|
| 1 Foundations: plain words, three depths, dynamic UI, sky wallpaper | FOUNDATIONS_13.md · BATCH1.md | 137–142 |
| 2 Onboarding: four steps, ask when needed, kids hand-over | ONBOARDING_13.md · BATCH2.md | 143–148 |
| 3 Sources manager and Planner | SOURCES_PLANNER_13.md · BATCH3.md | 149–154 |
| 4 Canvas, Studio, Notes, Translate, Scan | MAKING_TOOLS_13.md · BATCH4.md | 155–160 |
| 5 Study and Kids mode | STUDY_KIDS_13.md · BATCH5.md | 161–166 |
| 6 Book, Russian boards, prototype, films, this pack | BATCH6.md | boards/ru (24) |

## Reference code (to review, not drop-in)
- 26 Kotlin files under `app/src/main/java/com/halo/app/`:
  - `ui/plain`, `ui/adaptive/v13`, `ui/onboarding/v13`
  - `ui/sources/v13`, `ui/planner/v13`
  - `ui/canvas/v13`, `ui/studio/v13`, `ui/notes/v13`, `ui/translate/v13`, `ui/scan/v13`
  - `ui/study/v13`, `ui/kids/v13`
  - `ui/theme/v13`, `wallpaper/v13`
- 5 test files under `app/src/test/`. Results when each batch was run:
  - planner 6/6
  - making tools 6/6
  - Study + Kids 7/7
  - The plain-words ratchet test and the onboarding test were written in batches 1 and 2.
- Batch 4 includes the fix that makes Russian work in `NaturalAdd.kt` ("в 10", "с мамой", "завтра").
- Strings: `res/values{,-ru}/strings_*_13.xml`. There are 297 in English and 297 in Russian.
- Icons: 117 icons, as SVG (`assets/halo/artwork/ic`) and VectorDrawable (`res/drawable`).
- Animations: 25 Lottie animations, each in dark and light (`res/raw/halo13_motion_*`).

## Board source
- `docs/design/halo-13/source/`: open `t13board.html?b=137` … `?b=166`.
- Russian boards: run `render_ru.py`, which uses `ru13.json` and `fit.js`.
- The book: run `book13.py`, then `renderbook.py`, then `merge.py`. You also need brand book v7.0, the 9.5–11.0 additions book and the 12.0 book.

## Open for the team
- **Decisions:** which sources are safe for kids' topics; trying the age bands with real families. Carried over from 12.0: which graphics system draws brushes, whether brushes can be shared (including in Kids), where the tablet menus go, monitor mode, and the RAW decoder. Latvian and Polish text for 13.0.
- **Security review:** moving from an old phone.
- **Privacy reviews:** homework photos and the camera on glasses, spoken rehearsal recordings, and Safe places.
- **Checks:** a native Russian review (check the label for "points"), a by-eye check of text halo writes on the fly, and a by-eye check of all boards.
