# Study and Kids BrandBook continuation - 2026-10-20

Authority: canonical v4 Package C (S01-S03) and Package D (K01-K02), exported screens and B/source screens_c.js, screens_d.js and halo-app.css. Base b9c1408cc76ade76429d42c6715f457bc3e638af; local uncommitted changes retained.

## Production changes

- K01: greeting and grown-up shield above a 96dp Mint mascot; three full-width 76dp choices (Ask a question, Learn something, Make something). Real lesson/project resume information belongs inside its activity choice. No fourth resume card. Choices keep pre-reader and TalkBack ownership.
- Warm Kids background and content color are explicitly provided; changing the Material color scheme alone did not paint the previous root. Shared aura follows actual input/generation state. White light-theme cards and plum dark-theme surfaces follow Package D.
- K01 voice dock: separate status caption, 88dp pill, 56dp side controls and raised 72dp mic. Busy mic becomes Stop and cancels through the existing global stop owner. The mic is outside the clipped pill surface. No runtime permission or recording is started automatically.
- K02: three topic suggestions plus a custom-input invitation. Topic selection is a reversible draft; lesson creation remains explicit. Existing real child lesson can resume from Home. Topic titles remain literal. Current explanation sits in a dedicated card, followed by Repeat/Next and disclosed thinking/help controls. Repeat cannot replay another exercise. Reading highlights still require exact TTS character ranges.
- S01: explicit empty-state entry, compact review and resume cards, single accessible Continue target, 200%-text reflow. Session progress reads the persisted journey when available; no synthetic timing, mastery or progress count is invented.
- S02: new lesson still clears only unfinished setup and never preselects cloud. Back from the first setup step returns to Study home, with Notebook as the home Back destination. Incoming Notebook source entry still opens setup directly.
- S03: current exercise response before secondary controls; earlier turns stay available through Show earlier steps. Seven-stage Study progress uses actual journey state; Kids groups that same state into five child-facing phases. Practice submits an attempt through its existing owner; the anchored field otherwise asks a question. IME Send uses the same guard. The full answer editor remains available in Learning options.
- Fourteen EN/RU/LV keys added to ui.tsv and generated resources (3355 total). No provider keys, access policy or saved user objects were changed.

## Validation and limits

Final build/checkpoint results are recorded below after verification. Host renders are component-layout evidence, not Android screenshots or certification of full pixel parity. They must not replace the website's authentic device capture assets. Phone testing is deferred at the user's request.

Unchanged deeper workflows (homework intake, creation persistence/sharing, story reader, source permissions and tutor assessment) still need their full device journeys checked against K03-K05 and S04-S05. This report does not mark them accepted.

## Variable-font defect found during visual review

The bundled Sora, Atkinson and Onest files all contain a wght axis whose default is 400 (verified directly from each TTF fvar table). The resource Font overload previously used selected weight metadata with empty variation settings in the cached Compose implementation. It could therefore match a semibold/bold face while drawing the variable font at its normal default. BrandFonts now explicitly sets FontVariation.weight for every face and includes Atkinson 600. The dedicated italic resource is preserved. This follows the official Android variable-font API: https://developer.android.com/develop/ui/compose/text/fonts . This change also improves typography outside Study and Kids; shared header/dialog and Canvas control checks are included in the final gate.

Host render setup uses a test-only power-saving state for static frames. This stops guide-light animation from keeping the renderer's event queue active. It does not change production motion or phone preferences. An earlier blank render export was rejected and is not part of the delivered artifact set.

## Primary-action consistency

Kids learning, creation and reading now share KidsPrimaryButton: 56dp minimum pill, 17sp semibold label, 22dp horizontal inset, and the active accent fill/on-fill colors. This corrects default Material buttons drawing the darker accent ink as a fill. Callbacks, enabled guards, explicit Keep and the dedicated credential gate remain unchanged. Study review uses the same fill hierarchy, compact Start insets, and canonical study/practice glyphs.

## Verified handoff

Debug assembly passed. 41 selected checks passed with zero failures/errors/skips, including eight inspected native-renderer compositions. Localization consistency (3355 EN/RU/LV keys) and diff whitespace checks passed. Build log: `C:/Users/slavd/HaloEco/brand-review/study-kids-contrast-final.log`.

Checkpoint: `releases/checkpoints/halo-brandbook3-study-kids-2026-10-20-debug.apk`.
SHA-256: `36ac9d121c47b91871b9fa3d15c4b2ccf3e03e4882b630a903888c77ca964987`.

Host previews and manifest: `docs/testing/artifacts/HALO-STUDY-KIDS-2026-10-20/`. These are component compositions, not full device screenshots. No website capture assets were replaced. No phone installation or test was performed in this pass. Source remains local, uncommitted and unpushed.

A dark-mode regression check verifies readable body text outside cards, preventing inherited black text on the plum background. All eight final host compositions were inspected.

## Subsequent device test and repair

The checkpoint above was subsequently installed with `adb install -r` on the original application, preserving its existing data and provider configuration. The device began in Russian, light theme, mint accent, font scale 1.0 and rotation disabled. Home, Notebook, Study library, setup Back, explicit execution choice and a real cloud tutor response were exercised. The topic-only response used the existing provider; no additional key or capture application was created.

Device inspection exposed these repair items:

- Kids creation omitted the mandatory execution choice, blocking every new lesson. The child start now supplies phone by default and cloud only with the existing parent session grant, and records the choice through the same storage as adult lessons. Gateway restrictions remain authoritative.
- A Study session retained the primary navigation and live-task dock. Sessions now use focused editor chrome; the Study library retains primary navigation and pending runtime work is preserved.
- Discovery saved the generated teaching instruction as the learner's visible question. New responses now store only the learner's words while retaining the instruction in the provider request. Existing saved turns are not rewritten.
- Tutor responses displayed raw Markdown and untranslated stage/verdict/accessibility labels. The shared Markdown renderer now handles answers, with missing EN/RU/LV UI entries added. The unverified verdict remains explicit.
- Back from a new lesson reopened its completed setup. Successful creation now leaves setup before navigating, so Back returns to the library.

Targeted regression checks exercise real Room-backed child session creation for both grant states and persisted discovery text, alongside reader recovery and existing Study/Kids checks. Results and device retest status will be recorded after the repair build completes. Raw test captures may contain existing personal objects and remain outside the repository and website.

The repair gate passed 22 checks with zero failures/errors/skips. The first repair APK (`b4581e9a5114568db9a25001a9f2e77f6710bb6632ed65cce8f609c87c19161e`) was installed and device-tested: a new Kids lesson opened, local-only execution refused unavailable providers honestly, Stop and return worked, and parent-gate cancel plus successful device authentication were exercised. The existing parent cloud grant remained off; full Halo mode was restored. Study returned a genuine answer after retrying one empty provider response, displayed the localized unverified verdict with formatted content, and Back returned to the library. Font scale remained 1.0 and rotation remained disabled.

Subsequent cleanup wraps Study stage actions and localizes known empty-response/local-provider errors and parent-gate labels. Its 22-check gate passed again (`study-kids-device-localized.log`), but the phone disconnected before installation. Checkpoint `halo-brandbook3-study-kids-device-final-2026-10-21-debug.apk`, SHA-256 `687abcec1e3dbe40ccaf45bb55b1d620c0910cacf6e9b01c5b39d74ae8341be7`, is built but not installed. Three reviewed, unedited Russian device captures are recorded under the artifact folder's `device/` directory with their exact installed APK hash. They are regression evidence and were not added to the English website assets.
