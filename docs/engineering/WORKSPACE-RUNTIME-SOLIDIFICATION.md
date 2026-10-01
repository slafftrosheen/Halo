# Workspace and runtime solidification

Source implementation only. No builds, tests, lint, provider requests, emulator,
ADB, APK installation or device acceptance were run. `git diff --check` and
manual source review are the validation for this pass.

## Navigation and workspace

Home, Activity, Workspace, Devices and Settings are the primary destinations on
both rail and bottom navigation. Workspace opens Notebook, Canvas and Study from
a shared toolbox. Notebook and Canvas offer browse and create actions; Study
opens the independent topic/session/review tool without requiring a note. Study
and its sessions keep Workspace selected. Existing file/object list, search,
filters, sorting, selection, collections and lifecycle actions remain in place.
Due-card actions open review mode rather than starting another lesson turn.

## Runtime wiring

Provider construction is serialized so runtime and Settings share one provider
set and speech selector. The legacy model-selection action now passes any
registered provider to the canonical target writer. CHAT and AGENTIC routing,
capability requirements, role bindings and tool approval gates remain the runtime
authorities. Skill instruction loading refreshes availability and refuses unknown
or removed skills in addition to disabled/unavailable skills.

STT observes the persisted selector and refreshes the local Whisper delegate on
selection changes. Unsupported selections report unavailable instead of implying
that an installation is missing. Shutdown cancels the selection observer before
closing its delegate. TTS still resolves a pinned provider through the shared
speech output coordinator; its configured Android fallback remains explicit and
preview calls do not change the persisted selection.

Workspace remote permission checks read current persisted policy on use; the
Workspace context indicators refresh when the screen resumes after Settings.

## Reload persistence

Explicit settings batches commit before reporting success, including model/role
and speech selections. Provider non-secret configuration is saved in one batch
on the existing IO save path. Failed configuration saves retain the credential
draft for retry; protected credentials remain represented as configured rather
than being placed back into a visible text field.

A shared lock serializes Keystore alias creation and AtomicFile operations across
runtime and bootstrap secret-store instances. Reads never generate a new alias,
and AtomicFile backup recovery remains possible if the primary file is absent.
Migration atomically preserves a newer protected credential over stale plaintext.
Startup preserves an explicitly unset model rather than restoring the bundled
model every reload. Inference selection mutations are serialized.

These changes address source-level races and persistence gaps. The reported
reload failure has not been reproduced on a device, and existing ciphertext
whose encryption key was already lost cannot be recovered by this change.
A later authorized device pass should verify save/reopen/reload for each provider,
CHAT/AGENTIC bindings, speech selection, language and credentials without logging
secret values.
