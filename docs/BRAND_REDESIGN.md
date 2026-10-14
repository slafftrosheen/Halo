# Halo brand-book redesign

Baseline: `12d821147301f424661496aea7565b9db11e1b34` (main, synchronized before both batches). Sources: `BrandBook.pdf` and `BrandAssets/`.

## Presence and assets

- Lottie Compose 6.7.1 loads the supplied standard and Kids JSON locally. All 60 supplied files are packaged; no animation requires a network fetch.
- All 21 supplied states have static Android vectors converted from the source SVG paths and gradients. Four accent variants preserve the exact brand ring gradients; Kids retains Mint/Apricot. Micro indicators omit glyphs and glow.
- Full animation begins at 80dp. Compact/micro, reduced motion, and non-foreground lifecycles use static artwork. Success and warning play once and hold; error/offline/stopped are static. States supplied only as SVG remain static.
- A static equivalent remains visible while the Lottie composition loads or if loading fails. Animation draws transparently over its host surface.
- Speech capture uses the voice artwork. Text streaming uses text lines unless the speech runtime reports speaking. Assistant transcription no longer depicts an open microphone. Readiness, preparation, planning, recovery and approval keep distinct artwork.
- Optional context/condition badges are separate from presence; Home only attaches a condition when an actual review is pending. No provider badge is inferred from a configured default provider.
- `scripts/generate_brand_assets.py` reproduces the packaged vectors and copies supplied assets. It does not modify the source kit. The shared glyph renderer uses the supplied icons wherever there is a matching asset.
- Launcher foreground/background and monochrome layers use the supplied identity and remain Mint irrespective of appearance settings.

## Screen layouts

- **Notebook:** one introduction, direct Note/Canvas/Import/Inbox create actions, two recent items, Study and Memory destinations, readable fallback titles, app-language dates, 38dp type tiles and In chat wording. Skills remains accessible from Settings. Stored titles and content are not rewritten.
- **Note editor:** persistent Edit/Read/Source switch; bottom formatting bar with undo/redo, attachment and text controls; Halo action sheet; writing operations remain available when the document is scrolled. Tools and title editing move to a sheet. Trash is confirmed. Existing save, recovery, conflict and proposal ownership are preserved. Save-status text uses readable theme ink.
- **Canvas:** persistent Select/Text/Sketch/Link/Group/Import dock and 58dp Halo action; advanced actions stay in the inspector. Connections use curves and dot endpoints; cards use 14dp corners. AI drafts still require explicit application or discard through their existing review dialog.
- **Study:** resumable lessons and due review remain above a separate new-lesson flow. Goal, Material, Style and Options have distinct steps. Styles are readable selection cards. Optional material/language settings remain optional; starting still requires a goal or material.
- **Mini Halo:** 88dp presence, status/subline, top Stop, input pill and quick-tool links. Pending review replaces input with Review/Not now. Review opens the canonical Activity flow; it does not grant approval. Large text stacks the heading and Stop instead of squeezing the title.
- **Floating and widgets:** static Open Ring, compact horizontal and wide two-row action layouts, with a narrow fallback for small legacy widget instances. Oversized labels hide secondary actions rather than clipping. No daily counts or learning percentages are invented.
- **Kids:** supplied warmer/thicker presence, Warm surfaces, Apricot action treatment, 18sp reading text, 24sp titles and 56dp minimum touch targets. Existing action/permission ownership is retained.
- **Guided setup:** supplied static identity artwork and shared brand surfaces. Its existing nine functional stages and readiness evidence remain; the book's four-screen introductory concept has not replaced that workflow.

## Validation and boundaries

The first batch passed compilation, 13 focused tests, six rendered snapshots, and independent contrast checks (44 text/action pairs, minimum 5.32:1).

The continuation adds contract checks for all 60 Lotties, every static state/theme/accent/Kids resource, honest voice/text mapping, and motion policy. Rendered tests cover all states, animation middle frames, workspace controls, Mini Halo, Kids and widgets including enlarged Russian/Latvian text. The continuation passed all 62 focused tests across 23 classes. After the final Assistant review-overlay correction, debug APK assembly and the four presence/asset contract tests passed again. The EN/RU/LV generator check passes for 2,382 catalogue entries, and git diff --check passes.

Physical-device, keyboard/IME, overlay and launcher-host review remains open. The source changes and APK are local unless a later delivery explicitly reports a push. Full pixel parity for every secondary/settings screen is not claimed.

Lottie integration reference: https://github.com/airbnb/lottie/blob/master/android-compose.md

## Debug polish and device installation — 14 October 2026

- Notebook metadata wraps at enlarged text sizes, with an ellipsis on long dates. A fresh Russian 2x-font library-row capture verifies title, date, Inbox and In chat metadata remain within the card.
- Native floating/widget rings use the matching Blue/Lilac/Coral start/end gradients rather than mixing every accent with Mint.
- Physical testing found recent Study sessions crowded the new-lesson form. History/review sections now hide during new-lesson setup and reappear with Back to lessons. The rebuilt form was checked again on the phone.
- Initial debug assembly and 63 focused tests passed. After the Study layout refinement, assembly and 43 focused presence, Mini, workspace, Study and widget checks passed (zero failures/errors). EN/LV/RU generator and whitespace checks pass.
- Installed via adb install -r -t on the connected Android 15 phone without clearing app data. Local and installed final APK SHA-256 both equal `ad3e2cddf4e388dbb884e4d8f5e6ededa9f9f5202fae349e25b570cd6d556ce7`.
- Physical checks: Home/Notebook navigation; test-note typing, local save, back/reopen with identical five-word text; formatting bar above the keyboard; tools sheet and confirmed Trash flow; existing empty Canvas and horizontally scrollable dock; Study home/step navigation and final goal form; Mini Halo launched through Settings. No provider request was submitted.
- Two notes created solely for these checks (one blank, one labelled Halo debug save check 20261014) were moved to Trash through the confirmed UI. Existing notes and canvas cards were not edited.
- Presence motion: three later frames differed from the first at normal animation scale; two frames were identical with animator duration scale 0. The original unset/null animator scale was restored in a finally block. Font scale remains 1.0 and demo-mode allowance remains 1, as observed before testing. The phone was left back in Halo Settings.
- No app crash-buffer entries were present from installation through the completed smoke checks. This does not establish provider, speech/audio, camera, floating overlay, launcher widget-host, or long-run acceptance.
- Device evidence is saved locally under `C:/Users/slavd/HaloEco/brand-review/device/` (verification JSON, motion JSON, scoped UI captures and hierarchies). Source changes remain local and unpushed.


## Continued asset rollout — 14 October 2026

- Home Talk, Send and Stop now use the supplied 24dp brand vectors. Responsive icon visibility accounts for icon, gap and button padding, preserving enlarged translated labels and separate action ownership.
- Notebook Study/Memory entries, search, Inbox/collection chips and organize menus use matching kit assets. Editor Back, outline, find, close and add controls use supplied vectors. Existing favorite-state and directional controls retain their distinct states.
- Home updates and Study source/card/reply/review summaries use count-neutral labels in EN/RU/LV, avoiding incorrect singular grammar. The catalogue now contains 2,385 entries.
- Final serial debug assembly and 90 Home/Notebook/Study tests passed with zero failures, errors or skips. Localization generation and whitespace checks pass. Evidence: `C:/Users/slavd/HaloEco/brand-review/continued-brand-final.log` and `continued-brand-verification.json`.
- Current APK SHA-256: `5f1fc44f652420624cfdf6b3fafb8d7ee3a1fd33249ebc20d668cea7c9688331`. No Android device was attached during this continuation, so this APK has not been installed or physically checked. Earlier installation evidence applies only to the preceding APK.
- Checkout was already up to date at `12d821147301f424661496aea7565b9db11e1b34`; implementation remains local and unpushed.

- A separate recording run passed all 13 preview tests; fresh Home and workspace captures were reviewed, including the Russian 200% composer with stacked actions.

- Subsequent device check: restarting ADB restored serial f01fe673. Installed the current APK successfully without clearing data; installed SHA-256 matches the final hash above. Home, Notebook and Study opened; the updated composer icons and Home/Study count labels were observed. The app-only crash buffer since 19:14 was empty. Left the phone on Halo Home; no provider requests or content edits were made. Evidence: `brand-review/device/continued-install-verification.json` and `installed-brand-*` captures outside the repository.
