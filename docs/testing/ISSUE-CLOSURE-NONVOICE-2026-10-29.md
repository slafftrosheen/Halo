# Non-voice continuation — 2026-10-29

Status: **IN_PROGRESS**. The owner explicitly requested “go on without voice for now.” Speech, microphone, narration and human-heard acceptance remain deferred; that is not a waiver of issue requirements. Issues 55–62 remain open.

## Starting point

Fetched `main`: `96f99dfaf5456b758335ef0ec246fc8a45db866f`, clean checkout and zero divergence. The same authorized Redmi reconnected. Installed app SHA-256 independently remains `46e9b75a7c420cbf4097c0bc859adc9f1363a585ba0bb2dbd0552e64ca3d59b6`; this binary's production corrections correspond to `a3513d4ef`, with later evidence/test-only commits described in the [preceding ledger](ISSUE-CLOSURE-RESUMED-PHONE-2026-10-28.md). No replacement app was needed to start these checks.

## Study source and local refusal

Resumed the QA lesson `35af7be0-c1eb-4430-8e7f-c6c3d358a0a4` through the actual Study home. The Material inspector visibly identifies `HALO-QA-20261028-BOARD`, reports one readable source and none needing attention, and displays the exported text including the original note's exact source ID and river/leaves passage. This finishes the previously interrupted on-screen source inspection, alongside the exact canonical ID/revision readback already recorded.

For that lesson's existing **On this phone** choice, Create practice cards produced the truthful localized refusal “No permitted provider can serve this study turn.” No successful generation or saved practice is inferred. The refusal was covered by the still-open Material modal and became visible only after dismissing it. This is a reproduced feedback defect, separate from provider eligibility or artifact integrity.

The source correction dismisses that modal when the user explicitly starts practice generation, exposing the existing lesson's progress/refusal/draft-review surface. It invokes the same ViewModel method with the same enabled condition; source, privacy, model, approval and generation owners are unchanged. New-binary qualification is recorded below.

## Studio preview crash

Actual cold Studio entry rendered its empty state. Tapping New Project crashed Halo. App-scoped AndroidRuntime evidence records `RuntimeException: Canvas: trying to use a recycled bitmap` at `2026-10-29 11:10:34` device local time, through AndroidCanvas/BitmapPainter. Earlier warm entry attempts returned to Library; they are not classified as an independent navigation defect without further proof.

The preview had a delegated bitmap state read inside `onDispose`. When the state changed from null to its first bitmap, disposing the old null-keyed effect read the new bitmap and recycled it before drawing. Several Studio/Canvas preview surfaces shared this pattern. Even capturing the old bitmap is insufficient authority to destroy pixels retained in Android display lists during replacement/navigation.

New `StudioPreviewLifetimeTest` uses the real StudioService/renderer, isolated Room/cache, native Android graphics, document changes, actual image capture and composition disposal. Its first compile had an incorrect test import; that compile error is not a product regression. After correcting the import, **1 test failed with the same recycled-bitmap RuntimeException**. The red XML is preserved outside Git as `closure-pass/nonvoice-preview-red.xml`.

The source correction removes manual recycling of published pixels from Studio document/Ink previews, Workspace image previews and Canvas image/PDF/stitch/Ink preview surfaces. The last Compose/Android renderer reference now governs collection. Bounded decoding, renderer serialization and cleanup of unpublished/cancelled or temporary processing buffers remain intact. No source media is modified, and no exporter, bitmap cache, permission or execution owner is added. Grouped build and physical results follow.

## Grouped build and installed qualification

**63/63 affected host tests PASS**, zero failures/errors/skips across 18 classes. The real-renderer regression is green. Included Studio test families, Workspace image decode/concurrency, annotated Canvas/preview policy and affected Study review/source-refresh cases. Both APKs built successfully in **8m43s**. This is an affected gate, not a new full-app-suite claim.

| Artifact | SHA-256 | Device status |
| --- | --- | --- |
| App | `08b3a82e45cfcfc556c8a5e20336d0321d34c9b83336b8417fc159264a41e88f` | Installed; base.apk independently matches |
| Test APK | `6931becf6947b4e7709692ac3f37c6e9d57c24937162d99f7616454216d4bdc4` | Installed |

Built against `96f99dfaf` plus these corrections; publication does not rewrite its embedded dirty-build provenance. [Machine gate](issue62/2026-10-29/nonvoice-gate.json) ties source hashes/Git blobs to these artifacts.

Actual `MainActivityStudioPreviewPhysicalTest`: **1/1 PASS**, 32.005 seconds, zero ignored. Three production New Project dialog/preview cycles typed QA titles, waited for the rendered image, cancelled without saving, and navigated through Workspace and warm Studio requests. No project or provider request was created by this instrumented case. Later scripted warm requests use the same explicit NEW_TASK/CLEAR_TOP/SINGLE_TOP flags as that test; bare shell requests that did not visibly change the page are not promoted to a navigation-defect diagnosis.

Actual Study reproduction on the new binary: resumed the QA lesson, opened Material, scrolled to Create practice cards, and invoked it. The sheet closed and the exact localized local-provider refusal appeared on the lesson **without any extra Back/dismiss action**. UI-tree assertions verified absence of the sheet and the expected notice. **PASS for feedback visibility**, not successful practice generation. Evidence: `nonvoice-fixed-study-refusal-visible` and grouped/native logs.

## Actual Studio import → edit → save/export → reopen

Created own project `HALO-QA-20261029-STUDIO`, ID `44079031-958d-479a-ab69-95f9d45a95f1`, through native UI. Imported self-generated `HALO-QA-20261029-STUDIO-A.png` using Android's file picker. Source: 240×160, 715 bytes, SHA-256 `cb2c8051b589f086ed959160c8b1acc1bf5be6c459228a177e789c5954574f15`. Applied Rotate right and center-square crop in the transform dialog, added text `HALO-QA-29`, and saved.

Canonical project revision **5** has committed IMAGE and TEXT layers, recipe turns=1/square=true, base canvas 240×160. IMAGE source is managed file `c7ab8b15-aba0-4f04-8952-a0fe92ce5b3c`, anchored to the exact source SHA. Its bytes and the separately staged original both retain that hash.

Native Export PNG produced ACTIVE managed file `dade3c21-af3a-4f9c-95f6-951647d3cb14`, title `HALO-QA-20261029-STUDIO.png`, **160×160**, **2562 bytes**, SHA-256 `a2028c4a2bd7e95ca87d85bc20f4a4f762d1f3aacec528feeac2a80d470f2d91`. Independently checked its PNG header/hash, visually inspected saved pixels, and opened its native saved-file detail. Provenance records operation `studio-export`, exact project ID/revision 5 and matching document/source recipe.

Force-stopped Halo, cold-opened Studio, selected the saved QA project from its list, and observed its rendered document and “All changes saved” status. **PASS for this touch edit/save/export/project-reopen chain**. This is not a typed assistant recipe, Canvas/Study insertion of the output, arbitrary format fidelity, physical Ink or every stale/cancel/recovery case. Evidence: `nonvoice-studio-persistence.json`, `nonvoice-studio-output-provenance.json`, `nonvoice-studio-export.png`, `nonvoice-studio-restart-list` and `nonvoice-studio-reopened` outside Git.

## Installed local artifact — metadata PASS only

The existing SmolLM2 360M file was verified directly on the phone: **373719040 bytes**, SHA-256 `8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e`, matching the pinned catalogue artifact. No download, import or model-selection change occurred.

Actual `LocalArtifactInspectionTest#inspectInstalledArtifact`: **1/1 PASS**, 55.629 seconds. Exact model/fingerprint/length/run/source arguments were supplied. Canonical native inspection reports VERIFIED_ARTIFACT, LLM, CPU/GPU metadata, no native vision/functions, runtime 0.18.0, Android 15, device 23124RA7EO, total RAM 7970328576 bytes. Metadata inspection itself took 522 ms; the whole test duration includes setup and physical verification. These are distinct measurements. This is not model quality, backend performance, language, airplane-mode, assistant or voice acceptance.

A separate `DownloadedModelDiagnosticsTest#diagnoseResidentEndurance` requested ten CPU turns at context 2048, without selection setters, downloads or speech. **FAIL**, 106.422 seconds for the test: one successful control conversation, two attempted, then the existing runner stopped. First conversation: first token 16448 ms, total 18742 ms, nonempty and matching expected output. Second: first token 15805 ms, total 27288 ms, nonempty output that **did not match** the expected control response. Creation and first-token stages passed for both; this is a semantic completion failure, not an observed native crash or timeout. Sampled peak PSS 1626335 KiB; GPU memory is not visible to this sampler. No new model role or suitability recommendation is granted.

These diagnostic conversations are not ten user-facing assistant turns, an airplane/no-outbound trace or a whole-pack performance claim. Do not retry unrelated models or weaken the expected answer to turn this result into PASS. The existing On-this-phone Study refusal remains distinct from artifact integrity. Local evidence: `nonvoice-smollm-installed-snapshot.json`, `nonvoice-smollm-metadata.json`, `nonvoice-smollm-artifact-inspection.log`, `nonvoice-smollm-endurance.log` and `nonvoice-smollm-endurance-progress.json`.

## Evidence handling

Raw app-specific crash/test logs, captures and QA-only canonical readbacks stay outside Git under `C:\Users\slavd\HaloEco\verification-2026-10-28\closure-pass\nonvoice-*`. The local UI helper now refuses capture/taps when Halo is not foreground; unrelated application/notification contents are not eligible for publication. Phone settings, credentials, user documents and provider/model selections are preserved.
