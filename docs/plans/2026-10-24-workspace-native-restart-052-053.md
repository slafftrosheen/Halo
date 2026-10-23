# Native saved-object restart acceptance (#52 / #53)

Test-only completion of native persistence evidence; reuse the production app assembled with Canvas clear. No implementation expansion into unsupported Office/native PDF formats.

- [x] Replace isolated in-memory fixture DBs with unique file-backed databases and guarded cleanup.
- [x] Add TXT/Markdown/CSV create/edit/export/readback, Canvas references and Study resolution after actual database close/reopen; check JPEG rotation, annotated PDF page output and preserved originals.
- [x] Reconstruct Studio/Canvas/Study/media/search readers after database reopen, preserving saved IDs, source trash and empty Memory.
- [x] Correct the native Polish tool-loop instruction that previously fell back to English; unchanged English/Latvian/Russian prompts, unknown-language rejection regression.
- [x] Read-only review, then test-APK-only gate; verify production APK identity is unchanged.
- [x] Install matching production/test packages after running role probe finishes; verify installed hashes and run all affected native cases.
- [x] Publish main and update/close issues only against the actual scoped acceptance. Record database restart separately from OS process-death, spoken/Power and full release qualification.
