# UI-DAILY-001: everyday Halo

Date: 2026-10-11

## Job and scope

When I need to think something through, write a message, learn, or return to saved work,
I want a clear starting point and predictable controls so I can get on with my day.
This pass covers Home, Library, primary navigation and the shared Android theme.
The runtime, providers, permissions and delivery authority retain their existing owners.

## Findings and changes

| Finding | Change | Acceptance target |
| --- | --- | --- |
| Russian Workspace navigation wraps into four lines on the connected phone | Short Library label; bounded labels; accessible icon names at large text sizes | No four-line navigation columns |
| An idle composer reserves a full disabled Stop button | Show Stop only when work or voice is active; retain separate Send and Talk targets | Stop absent when idle; drafts remain editable while busy |
| Empty Home offers no concrete starting point | Three everyday suggestions prepare editable drafts | Suggestion never sends; Send remains explicit |
| Today is below tools and attachment controls | Show a compact Today card before the answer when updates exist | Updates reachable without scrolling past the answer |
| Library opens with four large tool cards before saved work | New note and Save to Inbox remain immediate; other tools expand on demand | Saved-work controls visible near the top |
| Default Material container colors do not match Halo surfaces | Opaque, coordinated sage/cream and forest palettes, including every surface container role | No unrelated purple/transparent container defaults |
| An app font-size preference can override larger system text | System size is a floor; larger app preferences still apply | 200% system text stays at 200% |
| Reply action rows cannot wrap on narrow screens | Flow layouts for metadata and Copy/Save | Actions remain reachable at enlarged text sizes |
| Camera setup catches coroutine cancellation as a camera error | Rethrow cancellation | Navigation cancellation is not reported as a missing camera |
| Canvas image combining cannot compile due to nullable file access | Require a present current file before checking version/URI | Missing source is rejected before starting image work |
| Today headings/actions bypass localization | Use the reviewed EN/RU/LV catalogue, including spoken action names | New entry points and Daily action labels translated |
| Library filters, sorting and file-type labels bypass localization | Translate app-authored labels while retaining source titles and snippets | Russian Library primary controls use Russian copy |
| Opening the keyboard counts Scaffold padding and IME padding twice | Consume Scaffold's applied window insets before child layouts apply their own insets | Composer sits above the keyboard without an extra keyboard-sized gap |
| Canvas pixel tests use legacy graphics that do not rasterize the composition | Run the existing color, size and source-preservation assertions with native Robolectric graphics | Actual red/blue/green output pixels verified |

## Design review

Initial Refactoring UI diagnostic: 6/10 (5 of 8 checks satisfied). The identified
gaps were competing hierarchy, space consumed by idle/secondary controls, and overly
wide reading content. Home now caps its content at 760 dp; the Library gives saved
work priority; the composer keeps the same Send and Talk actions during work.
A whole-app 10/10 rating would require inspecting the remaining feature screens.

Calculated WCAG contrast for the changed tokens:

| Pair | Ratio |
| --- | --- |
| Light secondary text / white surface | 6.29:1 |
| Dark secondary text / dark surface | 8.36:1 |
| White / light primary button | 5.06:1 |
| Dark text / mint primary button | 11.17:1 |

New text is authored in `app/src/main/localization/ui.tsv` and generated with
`python scripts/generate_ui_languages.py`. Generation also normalizes the previous
Canvas string IDs to the generator's hash convention; their translations remain.

## Validation

- Regression coverage: idle/busy composer, draft editing, explicit suggestion submission,
  voice interruption, translated large-text actions, system font-size floor.
- Visual fixtures: light/dark welcome and Russian welcome at 200% text, plus the existing
  Home and workspace rendering fixtures.
- Final gate: `:app:testDebugUnitTest :app:assembleDebug`, serial, one worker,
  no daemon, 2 GB JVM heap, with Roborazzi recording enabled: **BUILD SUCCESSFUL**.
- App tests: **579 passed, 0 failures, 0 errors, 0 skipped**.
- `git diff --check`: passed.
- Debug APK installed with `adb install -r`: **Success**. MainActivity launched.
- Connected-phone inspection: Russian Home, Library and focused composer checked.
  Today title and Library controls translate; navigation labels fit; idle Stop is
  absent; the extra keyboard gap is gone. Existing conversation and Library retained.
- Current app-process AndroidRuntime error log: no entries during these checks.
- Phone screenshots: `C:/Users/slavd/HaloEco/halo-ui-after.png`,
  `C:/Users/slavd/HaloEco/halo-library-final.png`,
  `C:/Users/slavd/HaloEco/halo-keyboard-final.png`.
- Changes are local and uncommitted. Pre-existing `.idea` changes were retained.

The phone inspection uses the existing saved conversation and Library. It does not
submit messages, create notes, clear conversations, or alter provider/device setup.

These UI checks do not establish live provider, voice, wearable, or all-feature acceptance.
