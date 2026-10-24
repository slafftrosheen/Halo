# Interface 4.0 speech inventory operations

Integrated test execution and exact device/artifact results: [verification ledger](INTERFACE-4-VERIFICATION.md). Sub-batch source notes below retain their original evidence boundaries.

`halo_speech_inventory` reads the real `SpeechModelInventoryRepository` and native
Voice owner. It returns bounded pinned recognition/VAD assets, installed flags,
catalogue language support, selection/resident identity and actual download state.
Catalogue support and installed files are explicitly not spoken/device
qualification. Raw file paths, URLs, exception bodies and custom entries are absent.

`halo_speech_model_action` invokes the same `VoiceSettingsViewModel` callbacks for
install/resume/pause/remove/select. It requires the exact observed process inventory
token and pinned catalogue asset digest, current scoped interaction, foreground,
grown-up admission, and capture/testing stopped. Pause applies only to that native
Voice owner's exact active download. AUTOMATIC is select-only. The existing native
installer owns verified files, pinned network sources, storage reserves, resumption,
shared transfer serialization and resource release; no new downloader is introduced.
Public pinned model acquisition is separate from inference/cloud data disclosure.

Removal additionally requires `artifactVersion` from the exact asset row. The
repository rechecks catalogue/file/sidecar metadata under the existing transfer
mutation lock before deleting. This prevents a queued removal from deleting a newer
independent acquisition that finished while it waited. The existing selected,
automatic-selection and resident-provider guards remain authoritative. Unsupported
inventory implementations reject exact removal rather than assuming support.

All writes require central durable ALWAYS/NEVER_REPLAY policy. Receipts say
DISPATCHED/completionConfirmed=false; read actual inventory/selection/progress after
dispatch. Failed or uncertain operations must not replay. Downloads never select
models or grant accuracy, permission, cloud, paid-media or qualification authority.
Inventory metadata uses existing Workspace disclosure/result-provenance policy.

Custom voice-pack import retains its OS file-picker and reviewed package owner.
Credentials, permission dialogs, real microphone tests and user-heard confirmation
remain human/platform/hardware exceptions. The nearest typed setup alternatives are
the existing pinned `halo_local_setup_*` bundle operations and these specific
recognition/VAD catalogue operations; they are not substitutes for qualification.

Regression sources: `SpeechInventoryToolsTest` covers closed arguments, interaction/
admission, missing exact removal artifact, no custom URL/model, and dispatch truth.
`SpeechInventoryExactRemovalTest` covers changed artifacts surviving stale removal
and unchanged selected-model protection. This sub-batch ran no Gradle/download/
network/install/device tests; compilation and execution belong to the root combined
gate. Actual download interruption, capture-resource behavior, spoken accuracy and
device/OS acceptance remain NOT_RUN here.
