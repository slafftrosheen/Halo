# C03 routine suggestion review

Contract: `product/F/spec/package_f.json`, C03, and `product/F/PACKAGE_F.md`.

The preceding routine trial/history batch was pushed to main as `cfef268a` before this continuation.

## Behavior

- Pending automation suggestions from the existing personal-learning repository appear in Routines, with No thanks / Edit / Turn on together in a wrapping action row. The existing Notebook Inbox route opens the same review page with its exact suggestion and routine identities.
- Review shows the saved routine's When / Only if / Do / Tell me fields and a localized explanation based on the existing repeated-manual-run evidence count. No new suggestion generator or invented evidence is introduced.
- Turn on opens the existing approval page. It does not execute or enable anything merely by opening the page. The user can try the draft once, approve and turn it on, save it off, or go back to edit.
- Edit opens an unapproved draft. Back/discard preserves the saved definition and leaves the suggestion pending. No thanks records dismissal through the existing learning repository and cooldown policy.
- Saving a suggestion draft verifies that the exact suggestion is still pending, is an automation, and targets an existing matching routine. The routine write and acceptance/archive decision are one Room transaction. Failed writes roll back both; replay, dismissal and missing-target cases cannot write the routine.
- Removed targets remain dismissible. Unavailable or mismatched route identities do not expose enable/edit actions. Draft restoration never automatically saves or executes.
- Existing EN/RU/LV keys are reused; seven new localized keys cover the reason, explanation and unavailable states.

## Verification

- The shared Halo action-button revision passed all **33 focused checks**, zero failures/errors, plus debug assembly. Coverage includes real Room acceptance/rollback, exact-target and pending-state checks, Compose save/restoration, removed targets, 200% text, retained history and real trial execution. Log: `../halo-suggestion-brand-final.log` (5m27s). JUnit evidence is retained under `../brand-review/suggestion-tests/`.
- Inspected the final 360dp light, dark and Russian host renders. Actions wrap instead of clipping, and No thanks remains grouped with Edit / Turn on. Corrected RU/LV count wording to avoid plural-agreement errors; the final Russian render and APK assembly passed again afterward (`../halo-suggestion-copy-final.log`, 1m33s).
- All 3,574 EN/RU/LV keys and formatting placeholders are complete and consistent. `git diff --check` passed.
- Final APK SHA-256: `F35E5EA52E29E3A4AB12C9AFDF6CB08922CC1C0A50CF92B9FC93520E510C995D`. `adb install -r` returned Success on Xiaomi 23124RA7EO; launch returned Status ok. The installed base APK hash was independently checked and matches.
- On the final APK in Russian: opened Routines, retained History and the earlier successful trial receipt; entered a new battery draft, opened review and trial confirmation, cancelled without executing, then discarded the draft. The list remained **0 on / 0 off**, with the earlier 12:51 trial still the newest receipt. Returned to Home/Chat. The existing unrelated pending user approval was untouched. Final app-PID AndroidRuntime error capture has zero lines.
- No eligible routine suggestion exists on this phone profile. Actual-device acceptance/dismissal of a generated suggestion remains untested; the host integration and UI evidence above must not be presented as physical acceptance or full BrandBook pixel parity.
- Local phone evidence: `../brand-review/suggestion-history.xml`, `suggestion-receipt-ru.png`, `suggestion-confirm.xml`, `suggestion-discard.xml`, `suggestion-clean.xml`, `suggestion-final-home.xml`, and `suggestion-final-crash-check.log`.

## Parallel phone probe

At the user's request, the existing local-model harness ran while UI work continued, on the earlier matching app/test APKs. The fresh Qwen3 0.6B CPU/English campaign reached its 30-minute limit: 23 recorded cases, with 10 correct answers, nine wrong answers and four 120-second case timeouts. It did not complete three repetitions or tool/vision assessment. The harness conservatively exports NOT_TESTED for every final role grade. The partial report and exact artifact/configuration evidence are in `../phone-role-fresh-20261021-ui/REPORT.md`; these results are not a completed qualification of the new UI APK.

Other in-progress glasses and local-model work in this checkout is preserved and excluded from this routine commit. No hosted CI/Actions is invoked. A build of this working checkout includes those existing source changes; routine tests do not establish their hardware/model acceptance.
