# FEATURE-075 / CONTEXTUAL-CAPTURE-001

Current integration correction (2026-10-09): FEATURE-072 (8014911b), FEATURE-074 (ee8e508d), FEATURE-075 (6fdae3bd), and FEATURE-076 (2ce2a6f9) are ancestors of verified main baseline 05b1dc73. Source integration is established; physical acceptance remains pending and FEATURE-073 watch microphone/PTT is absent. Status statements below describe the original acceptance run and are retained as historical evidence.


Status: PARTIAL acceptance; software implemented and verified on `codex/feature-075-contextual-capture`. Base includes local FEATURE-072 (`8014911b`) and FEATURE-074 (`ee8e508d`). FEATURE-073 remains absent after fetching all origin references; origin/main is `bb27bc57`. Dependency integration is not verified and main integration is withheld until that boundary is resolved.

## Ownership and behavior

`ContextualCaptureController` is the application workflow owner. It stores bounded process-local capture identity and typed state, not image bytes or an independent durable database. Phone capture keeps the existing TakePicture intent and pending-file cleanup; imported/shared images enter the same validated review. The sheet reads the actual existing media reference and decoded dimensions. No ordinary receipt/save dispatches inference or preloads vision.

HeyCyan quick capture uses the existing verified thumbnail coordinator, preserving the distinction from full media. Hardware photo-ready reception enters review rather than automatically preloading a model or publishing inference. Full-media synchronization remains separate: a command acknowledgement is only dispatch, and downloaded inventory identities remain distinct from the newest button event. No new BLE protocol behavior is promoted. FEATURE-074 TakePhoto and CaptureAndAsk delegate acquisition to this controller path; Ask selection does not submit a model request.

The existing EphemeralMediaAssetStore has reference leases for review, pending attachment and exact interaction. All leases resolve the same hashed cache file. Terminal interaction cleanup removes that interaction lease; it does not remove the review lease. Expiry, process restart and capacity limits continue to bound temporary media. Failed Home submission retains the pending image, preventing loss and permitting retry without retaking it. Home captures the conversation identity at Send and gates repeated Send until the exact dispatched interaction reaches a terminal state.

Saving uses LocalCaptureStore's existing Notebook transaction and provenance. Save is serialized/idempotent by capture/source identity and records the actual image hash. No second file-store or Room migration is introduced. Notebook images reopen through exact attachment asset IDs. WorkspaceInteractionMediaStore only adapts durable resolution to the existing binder; it owns no bytes or index. Attaching to an existing note adds a reference to the same owned bytes and preserves the body and other attachments.

Capture notes with exactly one image attachment resolve as a visual Study source using actual bytes through WorkspaceMediaAssetResolver. Multiple image attachments fail closed with a request to choose one exact source. Existing guided, source-only learner setup, language preferences, lesson start, tutor solution gate and citations remain owners of Study behavior.

Ask/Extract explicitly attach the chosen image, open Home, and await the user's question/Send. They use the existing binder, CognitiveSession, conversation and provider router, with tool use disabled for image questions. Extraction offers visible text, objects and key information prompts that demand uncertainty and prohibit guessing hidden data. Results appear in the existing conversation with Copy and Save to note; they are model-generated drafts, not verified OCR. No contact, task, navigation, purchase or other extracted-field action runs automatically. Structured JSON extraction is not offered.

Memory accepts an editable bounded textual fact, preserves an explicit model-inferred origin when selected, saves source evidence in Workspace, then opens the existing MemoryHub review and conflict flow. No model result is automatically promoted. Secret classification remains mandatory. MemoryHub approval now preserves original origin instead of relabeling inferred content as an original user statement; reviewed conflict updates use the existing origin-preserving repository operation.

## Privacy

Temporary camera/import media is local-only by default. Explicit per-image cloud selection grants temporary permission only; global image inference and cloud-image permissions still apply, along with exact TEXT+IMAGE capability evidence. A saved source uses EXCLUDE_FROM_AI / LOCAL_ONLY / ALLOW_CONFIGURED_CLOUD, with LOCAL_ONLY default. A missing Workspace object ID no longer grants remote image access. The policy associates a retained temporary reference with its saved source so the object policy cannot be bypassed by continuing to use the cache reference. Screenshots are not selected from unrelated invocations.

Metadata-only eligibility checks do not load models. Local vision additionally requires existing qualification. Unavailable or denied inference preserves the real image and offers model setup or Save. No filename-only or fabricated-description fallback is added.

## Truth boundaries

Command dispatched ≠ image received. Image received ≠ image saved. Image saved ≠ image analyzed. Image analyzed ≠ claim verified. Claim verified ≠ memory approved.

Thumbnail receipt is a real low-resolution preview, not proof of a full-resolution photograph. Review itself changes neither conversation nor audio output affinity. Saved sources survive process restart; process-only temporary identities can expire or become unavailable and must never be presented as saved.

## Verification

The final requested serial gate passed: runtime SDK 52, cognition 233 and app 396 unit tests (681 total), zero failures/errors/skips. Debug APK assembly passed and the update installed successfully on Samsung SM-G990B2, API 36. The initial invalid-image regression exposed Robolectric legacy decoding behavior; native decoding now verifies rejection. Navigation dismissal, review lease cleanup, saved-policy retention, import/share origin separation, optional Notebook details, guarded still-image acquisition and one-shot Memory handoff were included in the final successful source gate. Focused regressions cover review leases after failure, forged owners, duplicate operation identity, invalid receipts, temporary cloud grant, saved policy precedence, concrete model evidence, actual visual Study attachment bytes, durable re-resolution and idempotent note attachment. Final counts and device evidence are recorded in the software validation artifact.

Physical report: [CAPTURE-WORKFLOW-PHYSICAL-001](../testing/CAPTURE-WORKFLOW-PHYSICAL-001.md). Phone available; connected Samsung SM-G990B2. HeyCyan hardware is NOT_AVAILABLE. No missing hardware or provider result is counted as acceptance.

## Remaining acceptance boundaries

FEATURE-073 is unavailable. Real HeyCyan full-photo/thumbnail correlation and duplicate hardware event acceptance cannot be established without that hardware. Hardware tests will not be inferred from source tests. No synthetic photo or model response substitutes for user inspection of a camera image.

Saved provenance preserves source kind, quality, available capture timestamp, trigger and SHA-256. Reopened thumbnails retain their preview label; unknown quality remains unknown. Future camera adapters use the existing StillImageSource and require validated image bytes, not an acknowledgement.

The twelve focused capture regressions also prove that duplicate generic still requests do not retake a photo, returned driver dimensions do not replace actual decoded dimensions, and a successful acknowledgement without media cannot establish receipt or trigger an automatic retry. Optional title/description save atomically in the existing Notebook capture transaction. Physical observations remain pending; the final APK installed successfully after confirming there were no temporary assets to interrupt. No physical camera, analysis, Study or Memory approval is claimed.

Save-preparation failure returns to the same reviewable image without creating a note. A single existing-storage boolean records that an unsaved review was present; after process restart, Home truthfully reports that temporary image unavailable. No image bytes or new durable capture store are added. These paths are included in the final successful gate.
