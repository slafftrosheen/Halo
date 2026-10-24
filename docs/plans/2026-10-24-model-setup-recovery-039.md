# #39 setup record recovery

Base836e96d3f6db5c97baa69183ed40102fa2a7989d, fetched0/0clean. Prior goal turn made verified source/device progress; no blocker audit applies.

Existing installer discards unreadable journal as absent and can overwrite it with a new setup. Settings lacks the existing Keep-files action. Preserve unknown ownership without fabricating a baseline or deleting files. Reuse the existing installer, storage key, commands, typed executor and UI.

Expose hasSetupRecord/recoveryRequired/canKeepFiles as truthful owner state. Unknown/legacy ownership or changed plan requires explicit Keep-files recovery, with no start/resume/rollback admission. Keep only removes the observed exact stored record after successful removal; content-free fingerprints guard replacement. Storage writes/removal precede in-memory publication; failures preserve recovery and files. Enforce codec size before splitting. Inject only the existing coroutine scope for deterministic actual-owner tests.

Both settings/onboarding expose the same Keep-files action and avoid impossible rollback for unknown ownership; typed status exposes the same capabilities. Existing policy, approval, replay, role/qualification owners remain. Add two EN/RU/LV/PL messages through current generator. No new store, acquisition engine, permissions or dependencies.

Complete production/regression/localization source first, review, then one focused owner/codec/tool gate and localization checks. No unrelated APK/model/voice qualification campaign. Record exact source/results; keep #39 open for full fresh/storage/process-death pack and actual role/language acceptance.

Source review fixed captured Keep stamps, terminal/rollback write failure states and previous-completed-setup ownership before first gate.15owner+4codec+4executor=23desktop cases authored. Existing AndroidStorage uses synchronous commit; added isolated native owner recreation/typedKeep/diskXML/file-retention fixture, no globalroles/acquisition. Single focused gate will assemble bothAPKs toinstall currentrecovery plus priorInk source once. Python15 testsPASS/generatedcheck4802PL/prior4019valuesperlocale4036lookupkeys preserved+2new. Native fixture review pending; no gate started.

Completed firstgate88814PASS8m42s/23desktopcases/0failures-errors-skips/bothAPKs. All15inputhashesstillmatch. Phone35194exit0/run89cfa5aeb4014f3eaa94c2f2dc81a102/PASS0.307s isolatedcommittedAndroidStorageownerrecreation/typedKeep/diskXML/retainedfile-choice/acquisition0; installedapp8b9859d7... andtest88f82307...readbacksmatch/Mainresumed. CurrentAPKalsoincludespriorInk. Nofullpack/OSprocessdeath/quality/spoken/Inkvisualclaim; original#39/#54remainOPEN. Boundedphaseevidenceandtemplatecontractinverificationreport.
