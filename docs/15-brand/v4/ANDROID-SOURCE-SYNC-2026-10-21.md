# BrandBook 3.0 source sync — 21 October 2026

Historical checkpoint; superseded by [the completed implementation sync](ANDROID-SYNC-2026-10-21.md).

Base: `ebdebbfff83de434a80d339e7a5898d1aa92ceee`, synchronized with origin/main before editing. Canonical authority: `IMPLEMENTATION.md`, `USER_FACING.md`, Package F and G, and the supplied C01 action-review sheet.

## Source changes in this increment

- Apps & services opens a scrolling action-review sheet from an actual discovered app. Actions are grouped by their semantic action, with the enabled count deduplicated across routes. App labels come from Android; unknown labels use localized fallback copy. Discovery never claims authorization. Access management leads to the existing Skills owner.
- Everyday Skills details show localized permission names, availability, policy choices and risk descriptions. Raw provider failures and tool IDs stay in the technical view. Screen-control counters, transaction enums and inspection metadata are hidden from everyday Skills and retained for existing technical hosts.
- Skill enablement suppresses duplicate writes and reports failures. Policy selection changes only after persistence succeeds. Provider setup is represented explicitly in the projected UI state instead of comparing English status text.
- Backup export starts with password protection selected. This is an export preference, not a claim that automatic folder backups gained password encryption. Restore uses human-readable saved-item names, plain conflict choices, stacked actions and wrapping retention controls. Existing import, merge, replacement, recovery and file-picker owners remain in charge.
- Routine review/run, assistant enrollment, Check-up, Study journey/homework/listening, and Canvas page/sketch/stitch actions use the existing shared mint primary button. Existing busy guards and child touch-target sizes are preserved.
- Shared header subtitles and Notebook destination tiles grow with text instead of truncating guidance. Study chapter navigation wraps; assistant enrollment and restore actions stack.
- EN/RU/LV copy is maintained in `ui.tsv` and generated resources (3638 keys).

## Deliberate source-only checkpoint

No builds, test runners, host renders, ADB commands, phone UI checks or installs were run for this increment. Localization generation is source generation. Source reads and diff review do not establish compilation, runtime acceptance or pixel parity.

The implementation is not yet a complete acceptance of all 95 designs. Remaining work includes the full state-by-state comparison, per-service/per-action access ownership for C02 beyond the existing Skills controls, and the unfinished hardware/provider-dependent flows identified in the other package reports. Do not mark these completed based on the UI changes above. Older package reports may list gaps that later commits already implemented; verify current source before adding replacements.

The pre-existing glasses/capture and local-model changes were preserved. This increment does not validate or publish those changes. New source remains local and uncommitted. Build and testing must wait until the remaining implementation is complete, per the user's latest instruction.
