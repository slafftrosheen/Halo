# Studio issue 65 operability record

Uses [AI-CONTROLLABLE-TOOL-VERIFICATION-TEMPLATE.md](AI-CONTROLLABLE-TOOL-VERIFICATION-TEMPLATE.md). Source batch authored; executable evidence pending. Canonical owners: `StudioService`, `StudioProjectRepository`, `StudioRenderer`, Workspace file/provenance owners, existing Ink controller/session and image-model consent/review owner.

| Registered operation | Typed / STT-final contract | Source/version fence |
|---|---|---|
| `halo_studio_get` / current / list | Layer selection, hierarchy, all editable properties, styles, brushes, available blend/effect values; bounded readback, optional local rendered colour sample | Sample requires exact revision and validates again after render; source images remain pinned |
| `halo_studio_edit` | Text, shape, line/frame, ink, texture, crop, typography, blends, editable effect stack; group/ungroup/duplicate/layout/style; connected fill | Exact project revision, validated closed fields, same commands/ancestor locks as UI; fill persists a new source-linked layer |
| `halo_studio_save` | Save/copy/rename/discard via existing owner | Exact project revision; undo/history preserve older states |
| `halo_studio_ai_draft` / status | Explicit review handoff; dispatch is not generation, billing, UI render or kept output | Exact review source; existing provider/privacy/spend owner; immutable proposal source and independent kept copy |
| export / insert / open / create | Existing PNG/JPEG, exact Canvas/file versions and managed-source creation | Existing Workspace/Room/source and ephemeral presentation leases retained |

Legacy-compatible documents retain schema 1; creative fields use document schema 2; operability framework remains supported version 1. No parallel command registry, storage engine or provider dispatcher exists. Tool IDs and skill ownership are unchanged; the existing Notebook/Workspace skill owns Studio. Human pen/drag gestures have data-operation equivalents (portable ink and numeric geometry); viewport/mode changes are presentation only. Android camera/import permission dialogs remain human/system security boundaries.

Three workflows for later executable acceptance:

1. Create a preset → add text/image/shape → style/align/group → switch Paint and add pressure-aware marks → return Design → undo/save/reopen/export → insert on Canvas.
2. Select image/group → edit crop/mask/blend/effect order and enable state → preview → save/reopen/export with unchanged retained source → exact typed readback and stale-revision rejection.
3. Open contextual AI review → select an actually configured editing model → explicit existing consent → inspect proposal → keep as a new layer in an independent project copy with original stack/provenance → preserve concurrent original edits.

For each workflow, typed device, actual STT-final voice, permitted model/provider and durable readback evidence are **NOT_RUN**. Synthetic tool calls and native Compose tests cannot stand in for real microphone recognition, actual provider support or physical rendering. Current phone status: not detected by ADB after the Pi restarted; no installation or app-data mutation performed.

Source regression cases cover codec/history/Room/CAS/archive, hierarchy/locks/selection, strict typed edits and bounded results, deterministic effect/texture/blend/fill pixels, global coordinate order, mode invariance, explicit AI review and native Compose tap/drag. All outcomes remain pending until the later single-worker gate. See [implementation map](../studio/ISSUE-65-IMPLEMENTATION.md) for exact coverage and limitations.
