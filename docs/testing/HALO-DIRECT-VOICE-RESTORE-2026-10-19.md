# Direct conversation voice restoration — 2026-10-19

Owner request: restore the previous speak-and-answer flow without transcript confirmations, and continue fixing findings from the connected-phone pass.

## Source

Attempted `git pull --ff-only` before changes. GitHub DNS resolution failed; continuing at `e500b2aff470b3791b6d4b1e88b1b6aeae1a2d6d` with all previous local fixes and existing untracked work preserved. No hosted CI.

The network retry succeeded during verification: `git pull --ff-only` reported Already up to date, and `origin/main...HEAD` was `0 0` at the same base.

## Changes

- Restore Home's composer from the pre-DevKit-field-migration implementation in `97c82273`. Talk invokes the existing `togglePresence` → HomeViewModel → speech runtime. Accepted recognition already enters cognition automatically; no new confirmation or recognition owner is added. Typed drafts remain editable and are not submitted by Talk. Stop, voice setup, interruption and large translated action names retain their original callbacks/layout.
- Restore Mini's empty-composer microphone to its existing `onTalk` callback, instead of field dictation. A nonempty typed draft retains Send. Existing Stop and durable action-review behavior remains owned by the runtime.
- Add direct callback regressions for Home and Mini. The pre-fix layout tests first failed because field dictation attempted to initialize an absent speech host. Running layout tests with the existing hostless theme then exposed the actual missing Talk controls: 11 tests, six failed, including both new direct-conversation tests. These tests check microphone dispatch and absence of typed-draft submission; they do not pretend to prove physical ASR accuracy.
- Make protected Kids options vertically scrollable, bounded to 600dp and padded for the Android navigation bar, so enlarged/localized copy does not permanently hide Colours or Full halo.
- Localize diagnostic names at display time. Add EN/RU/LV entries for the five essential facts and Notebook's memory subtitle; replace Russian `Открыть Study` with `Открыть учёбу`. Regenerate the catalogue/resources: 2945 keys.

## Verification

- Serial offline Gradle build: PASS, `BUILD SUCCESSFUL in 6m 42s`.
- Targeted app suite: 203 tests, zero failures. Includes Home/Mini direct microphone callbacks, interruption, typed-draft preservation, translated large text, prior Kids/Study/brand/reader regressions, voice trace and System Check tests. Speech-engine suite: 22 passing tests, unchanged and reused by Gradle.
- Generated translations check: PASS (2945 keys). `git diff --check`: PASS. Native UI mechanical detector: no findings on the four changed UI files; this does not establish full visual acceptance.
- Debug APK installed with `adb install -r` on `f01fe673`, successful launch, no data clear. Installed `base.apk` SHA-256 equals the built APK: `5b1ef4841aa61fa15515bb2b3a23bf1854041b33439496c6d18f086d4b723a6a`.
- Physical installed Home hierarchy has a labelled `Говорить` action wired to the conversation route, beside Send, and no field-dictation microphone/long-dictation instruction. See `home-installed.xml`.
- The owner was invited to test one real utterance on the phone. Automated callback tests establish dispatch; live recognition/answer evidence is recorded separately when available.

The earlier instrumentation APK is not represented as matching this new app APK. No CPU/GPU qualification was repeated or carried forward as fresh evidence for the new artifact, because this continuation changes presentation/callback wiring rather than native inference.

## Boundaries

This restores conversational microphones in Home and Mini. Workspace/editor dictation is a separate editing path; it is not converted into automatic assistant requests. The earlier incoherent Russian tutor response remains a quality finding: no claim that changing a composer fixes model output. Existing learning, child privacy, source policy and model selections are retained. No new models were downloaded.

Evidence: `artifacts/HALO-DIRECT-VOICE-2026-10-19/`.
