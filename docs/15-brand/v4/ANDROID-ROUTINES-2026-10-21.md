# BrandBook 3.0 continuation: routine controls (C03)

Authority: `IMPLEMENTATION.md`, `USER_FACING.md`, `product/F/PACKAGE_F.md`,
`product/F/spec/package_f.json`, and `product/F/screens/C03_list_dark.png`.
Baseline: main, `da67070485a193ee791ad72c22a807e9f9b109f9`, pulled on 21 October 2026.
The existing uncommitted D04 glasses implementation was preserved.

## Changes

- Add list switches bound to the existing `RoutinesViewModel.enable` operation. Display the persisted enabled state; turning off remains possible even when a routine is blocked or running. Turning off does not delete the definition.
- Gate both Run now and its confirmation using current readiness, reviewed grant, and in-flight state. Saved manual execution still uses `RoutineCommands` and the existing executor; the UI grants no new authority.
- Replace stale scheduled/event promises while off with “No runs while off.” Unknown delivery receipts say “Delivery not confirmed”; actual queue/pending states remain distinct.
- Use the When / Only if / Do / Tell me review labels. Wrap detail actions at enlarged text sizes and label switches for accessibility.
- Localize list counts, readiness, template descriptions, review/run/delete confirmations, history dates, action counts, and delivery receipts in EN/RU/LV.
- Localize new template names/messages only, preserving saved user content. Make tabs horizontally scrollable so long localized labels do not break inside words.
- Keep successful run output as source content. Show state-based failure explanations and allowlisted field-validation errors instead of arbitrary exception/protocol text. Stored user result text remains source content.

## Validation

- Two initial regression tests failed on the old off-state next-run promise and unknown-delivery label, as expected.
- Serial Gradle gate succeeded: `:app:testDebugUnitTest --tests '*RoutineBrandBook*Test' --tests '*EverydayAutomationTest' --tests '*RoutineDraftStateTest' --tests '*GlassesBrandBookFlowTest' :app:assembleDebug --no-daemon --no-parallel --max-workers=1 -Dorg.gradle.jvmargs='-Xmx2g -Dfile.encoding=UTF-8'`.
- 24 tests passed: 9 existing automation tests, 7 existing glasses-flow tests, 4 routine state/copy tests, 2 new Compose interaction tests, 2 draft-state tests. The old draft UI fixture was corrected to use the existing hostless theme, avoiding initialization of live speech services in a test Application.
- `git diff --check` passed. New localization rows have unique keys, complete EN/RU/LV values and matching format placeholders.
- Final debug APK SHA-256: `290E8AA8AF88CC95351C8574ED32653469663DC546D3DA8592334514C5E27F6C`. `adb install -r` returned Success; the app launched on the connected Xiaomi 23124RA7EO. All 24 focused tests passed again after the final tab/template corrections.
- Physical phone: Russian, light theme, 1080x2400 at 440dpi, system font scale 1.0. Verified Settings → Routines, localized title/subtitle/counts, template picker, draft entry and Back → discard confirmation. No routine was saved or run. The unrelated pending approval was not accepted.
- Final phone checks also confirmed horizontal scrolling to History, no routine runs, and the localized new-draft name “Низкий заряд”.
- Local evidence outside Git: `../halo-routines-brandbook-delivery.log`, `../brand-review/routines-list-ru.png`, `../brand-review/routines-picker-ru.png`, `../brand-review/routines-draft-ru.png`, and draft/discard/history XML files in the same review folder.

## Remaining acceptance

This is a C03 continuation, not completion of all 95 designs or pixel-parity certification.
Suggestion review and the broader C03 visual/device matrix remain open. The continuation below implements unsaved trials and durable previous-success history.
The initial control checks above did not create, enable or run routines. See the continuation below for the separately recorded trial execution checks.
Full dark/light, 360/412dp, TalkBack and EN/LV/RU device-matrix coverage remains open.


## Continuation: unsaved trials and previous success

- Review now offers **Try it once** with a separate confirmation listing the actions and conditions. It executes a fixed snapshot through `RoutineExecutor`, including validation, current permissions, condition checks, resource leases, execution limits and the durable never-replay action journal.
- The trial definition never enters the saved routines table and never reaches the scheduler. Its generated identity is independent of the edited routine. Trial output is recorded locally in History; notifications, wearable speech and Screen publishing are deliberately excluded and disclosed in the confirmation.
- Trial state survives configuration changes in the ViewModel; restoration never auto-executes. Save/back/edit and repeated trial dispatch are blocked while running, with an explicit Stop trial action. Results are tied to the reviewed draft's identity and exact definition.
- The history query includes the recent 100 runs plus each routine's last successful result. Later failures remain visible with their own status. Successful text is stored with its terminal state in one database update, bounded to 2,400 characters.
- The detail view and failure history show the previous success separately. Trial rows are labelled Trial run rather than Removed automation. New copy is localized in EN/RU/LV.
- Regression evidence: the persistence test failed before the query change because the successful row was missing after 120 failures. Its file-backed Room fixture uses TRUNCATE journaling for the Windows Robolectric legacy SQLite driver and reopens the database before asserting retained history.

### Issue review

Reviewed all 15 open repository issues. No issue was closed from this batch. #37 also requires persistent Screen/research cards, composition, reorder and on-device Ambient/privacy behavior; #43 requires repeatable voice, native-action and Study alpha journeys. #41 requires a network-flow inventory, trust-boundary documentation and privacy evidence. Routine trial/history completion alone does not satisfy those issues. Other open items concern voice/runtime, intent models, embeddings, commerce, auth research, Ink or watch hardware.

### Continuation validation

- 25 focused tests passed after the runtime/result-text changes: 9 existing automation, 4 routine state/copy, 2 existing Compose, 2 draft-state, 2 persistence, 4 real trial-executor and 2 trial/history Compose tests. Both the persistence and executor tests use real Room repositories; trial tests run the real coordinator, registry and resource acquisition.
- The persistence suite also checks deterministic same-time successes, separate routine identities, and exclusion of unrelated trials from last-success retention. Cancellation after action dispatch records an uncertain terminal state and cannot replay the occurrence.
- Candidate APK `7E661DE3A76EC1983A78C98C810D37DC0DD8C35E12A502DDB351B6AA6509CF77` installed successfully on Xiaomi 23124RA7EO. In Russian, opened/cancelled trial confirmation, confirmed a local message-only trial, and observed 1/1 completion with an unsaved/off notice. History labelled it Trial run and Trial recorded in History.
- Created only the temporary `Halo_flow_check` definition, saved **off** with silent delivery, manually ran its message successfully, then saved an intentionally unmet BatteryAbove(100) condition while keeping it off. The next manual run recorded 0/1 and conditions-not-met, while the separate previous-success card retained the earlier message. The list showed 0 on / 1 off. Existing unrelated pending approval was untouched.
- The physical review exposed missing Russian labels in review/delivery/condition selection, a character count and the interval summary. Added EN/RU/LV translations and changed the builder hint to point to Try it once before saving.
- Source content is preserved; new successful records store action text separately from localized status/counts, avoiding the previous English executor wrapper in new trial results.
- Local evidence: `../brand-review/trial-confirm-ru.png`, `trial-previous-success-candidate-ru.png`, `trial-history.xml`, `trial-list-off.xml`, and content-free `trial-phone-events.log`.
- Final localized gate passed: **25 tests, 0 failures, 0 errors**, plus `:app:assembleDebug`. Final log: `../halo-routine-trial-delivery.log` (BUILD SUCCESSFUL, 2m38s). One intervening run hit Windows MAX_PATH in the legacy SQLite fixture; shortening its method/database name fixed it without weakening the 120-failure/reopen assertions.
- Final APK SHA-256: `64277A9606382AD5E7B702F005BE823095A38473F17C2F3C962B718608AEF6CB`. `adb install -r` returned Success. The app relaunched and retained the off definition, skipped run, and previous success.
- On the final APK, removed the condition only in an unsaved edit and confirmed another trial. It completed 1/1 and displayed exactly `Halo_trial_check_completed.` without the English status wrapper. Discarding that draft restored the saved BatteryAbove(100) condition and off state, proving the trial did not overwrite the saved definition. The new Russian approval, delivery, interval and pre-save trial hint were verified on device.
- Removed only `Halo_flow_check`; the final list returned to **0 on / 0 off**. Hid its temporary Screen card via the card's Hide from Screen action. History remains as an audit record. Returned to Home/Chat; the pre-existing pending approval was never accepted. Captured final-PID AndroidRuntime error log contains no fatal entries.
- Additional final evidence: `../brand-review/final-trial-result-ru.png`, `final-history-retained.xml`, `final-definition-kept.xml`, `final-clean.xml`, `final-screen-clean.xml`, and `final-crash-check.log`.
- `git diff --check`, changed-file UTF-8 decoding and all 3,567 EN/RU/LV key/placeholder checks passed. This routine batch is ready for publication; existing glasses work was preserved. No hosted CI/Actions invoked.
