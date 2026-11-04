# Studio issue 65 operability record

Uses [AI-CONTROLLABLE-TOOL-VERIFICATION-TEMPLATE.md](AI-CONTROLLABLE-TOOL-VERIFICATION-TEMPLATE.md). Production compiled and native local workflows ran on the connected Android 15 phone. The latest completed related desktop gate on `88291b18f` has 645 passes / four failures; the next correction batch is authored and unverified. Canonical owners: `StudioService`, `StudioProjectRepository`, `StudioRenderer`, Workspace file/provenance owners, existing Ink controller/session and image-model consent/review owner.

| Registered operation | Typed / STT-final contract | Source/version fence |
|---|---|---|
| `halo_studio_get` / current / list | Default document view discovers revision/title/recipe/selection. Pin that revision for layers (four summaries/page), exact layer properties, text (384 UTF-16 units/page, surrogate pairs preserved), ordered effects, capabilities and saved versions. List pages four previews from the latest 100 saved projects. Every result fits 4,000 characters, including JSON escaping. | All follow-up projections require the exact revision; stale pages reject. Document-only pixel sampling requires exact revision and validates sources and revision after render. |
| `halo_studio_edit` | Text, shape, line/frame, ink, texture, crop, typography, blends, editable effect stack; group/ungroup/duplicate/layout/style; connected fill | Exact project revision, validated closed fields, same commands/ancestor locks as UI; fill persists a new source-linked layer |
| `halo_studio_save` | Save/copy/rename/discard via existing owner | Exact project revision; undo/history preserve older states |
| `halo_studio_ai_draft` / status | Explicit review handoff; dispatch is not generation, billing, UI render or kept output | Exact review source; existing provider/privacy/spend owner; immutable proposal source and independent kept copy |
| export / insert / open / create | Existing PNG/JPEG, exact Canvas/file versions and managed-source creation | Existing Workspace/Room/source and ephemeral presentation leases retained |

Legacy-compatible documents retain schema 1; creative fields use document schema 2; operability framework remains supported version 1. No parallel command registry, storage engine or provider dispatcher exists. Tool IDs and skill ownership are unchanged; the existing Notebook/Workspace skill owns Studio. Human pen/drag gestures have data-operation equivalents (portable ink and numeric geometry); viewport/mode changes are presentation only. Android camera/import permission dialogs remain human/system security boundaries.

Three workflows for later executable acceptance:

1. Create a preset → add text/image/shape → style/align/group → switch Paint and add pressure-aware marks → return Design → undo/save/reopen/export → insert on Canvas.
2. Select image/group → edit crop/mask/blend/effect order and enable state → preview → save/reopen/export with unchanged retained source → exact typed readback and stale-revision rejection.
3. Open contextual AI review → select an actually configured editing model → explicit existing consent → inspect proposal → keep as a new layer in an independent project copy with original stack/provenance → preserve concurrent original edits.

Native local workflow 1 has touch editing, paint/save/undo/reopen, managed PNG export and real Canvas insertion evidence. Workflow 2 has saved effect order, independent named enable switches, group/child locks and durability evidence. The registered readback in the completed desktop gate exposed real JSON truncation at the Runtime's 4,000-character ceiling; new domain views/page contracts and escaped-metadata regressions address it without changing the Kernel budget. They await the next complete gate.

Workflow 3 reached configured OpenRouter / FLUX 3 Image review and the explicit paid-request reserve gate. **Paid generation was skipped by the user**, so no live image request/proposal acceptance is claimed. Actual Russian microphone input successfully saved an exact Inbox task on the updated app, including reopen durability and no background brief crash; this does not prove Studio-specific actual spoken commands. Typed and STT-final operations still share the existing registered semantic owner; human camera/OS unlock boundaries remain separate.

Source regressions cover codec/history/Room/CAS/archive, hierarchy/locks/selection, strict typed edits and bounded results, effect/texture/blend/fill pixels, global coordinates, mode invariance, explicit AI review and native Compose tap/drag. The fresh batch adds full text reconstruction, effect readback, page/revision and escaped-metadata bounds through the real registry, plus narrow/large-text layout. Native narrow labels and Organizer decisions still need rechecking after the corrected APK is built.

See [the phone report](ISSUES-63-65-PHONE-2026-11-04.md), [implementation map](../studio/ISSUE-65-IMPLEMENTATION.md) and [validation evidence](artifacts/HALO-STUDIO-65-2026-11-04/README.md) for current results and historical failed/interrupted attempts. No issue closure or full acceptance is implied.
