# Halo brand sources and Android assets

The repository root no longer contains DevKit staging directories.

| Content | Canonical location |
| --- | --- |
| Current complete product contract, package specs, reference frames, prototypes and motion films | `docs/15-brand/v4/` |
| Earlier incomplete 3.0 handoff | `docs/15-brand/v3/` |
| Earlier Brand Book 2.3 / DevKit 1.4 reference sources | `docs/15-brand/reference-v2/` |
| Android icons and static orb artwork | `app/src/main/res/drawable/` |
| Runtime orb animations | `app/src/main/assets/halo/` |
| Runtime effect animations | `app/src/main/assets/halo/fx/` |
| Reduced-motion effect artwork | `app/src/main/assets/halo/fx/static/` |
| Original vector artwork and import provenance | `app/src/main/assets/halo/artwork/` |
| Offline Android brand fonts | `app/src/main/res/font/` |
| Reviewed EN/RU/LV copy | `app/src/main/localization/ui.tsv` |

`scripts/import_devkit3_assets.py` imports the current supplied runtime artwork and generates native icon resources. Its manifest records the source path and SHA-256 for every imported asset. It excludes reference boards, screen captures, HTML prototypes and reference films from the APK.

`scripts/generate_brand_assets.py` regenerates the earlier orb resource family from `reference-v2`. Run the current importer after it so current icon and animation sources win. `scripts/generate_ui_languages.py` generates the app language catalogue and Android string resources from `ui.tsv`.

## Runtime effect owners

| Event | Owner | Asset / rendering |
| --- | --- | --- |
| Capture, thinking, action, attention, error | Home and native assistant | Shared state aura and edge layer |
| Native assistant invocation | `AssistantSurface` | `halo_fx_assist_invoke` |
| A live task | `LiveTaskPill` | `halo_fx_task_step` running marker |
| A confirmed historical action | Activity | Static task-step done plate |
| A pending approval | Shared interaction renderer and task pill | `halo_fx_approval_breathe` |
| Confirmed / unconfirmed action receipts | Home | Corresponding `halo_fx_receipt` marker |
| Stop | Shared motion events and task pill | `halo_fx_stop_cancel`; cancelled interaction IDs suppress delayed receipt effects |
| Applying a reviewed writing draft | Note editor | `halo_fx_apply_draft`, after an actual apply event |
| Forgetting a memory | Memory detail | `halo_fx_memory_forget`, after successful removal |
| Entering / passing the grown-up gate | Shared Kids credential gate | `halo_fx_kids_gate`; unlocking requires Android credential success |
| Updating a routine result in place | Screen card renderer | `halo_fx_card_update` |

Existing reading, learning, tool, Canvas, pairing and presence effects retain their existing owners and use the imported runtime assets. Reduced motion uses still artwork. Decorative motion is constrained by lifecycle, screen state, battery saver and thermal pressure.

## Implementation evidence

This is a source implementation record. The initial coding pass ran no tests or builds. The user subsequently requested a checkpoint APK: `:app:assembleDebug` completed successfully on 2026-10-19. No tests, device runs or visual comparisons were performed. Importing assets or wiring an event does not sign off pixel matching, runtime behavior, accessibility or hardware operation.

DevKit 4.0 supplies A, B, C, D, E, F, G and SCREEN. It supersedes the incomplete earlier handoff. The import records 904 source assets, with complete-kit canonical directories taking precedence over copies in prototype folders.

Checkpoint APK and its SHA-256/build record: `releases/checkpoints/halo-brandbook3-2026-10-19-debug.apk` and the adjacent JSON/log files. It includes the uncommitted working tree based on `4f3c333ef9cbfac14ad4a955e6acff53b5812ce6`.

