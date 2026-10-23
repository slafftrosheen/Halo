# Exact drawing recovery and cleanup — #28

Base main508f4daa1a7f82b9f03de4724e71af945f049d81, fetched0/0 clean before source changes. Normal Edit sketch/Annotate could attach a recovery draft based only on node ID, then bind old draft bytes to the newer source's expected original/file version. Save and Cancel also cleared the canvas-wide draft unconditionally.

- [x] Match recovery kind, node, original and file version through the existing draft model; apply guards at Canvas host and shared annotation-dialog boundary. Preserve legacy unknown versions without promoting them to current identity.
- [x] Clear only exact consumed draft snapshots in the canonical Room transaction; Save/Cancel keep unrelated, stale-source and replacement drafts. Keep existing draft store/format and source CAS.
- [x] Author negative source/version identity, legacy version, compare-and-clear replacement, unrelated/new-card save/cancel, changed-source recovery and annotation-version integration regressions before validation.
- [x] Review full source/call sites, then focused desktop gate after this code batch and any phone-discovered admission correction are complete.
- [x] Publish verified source/evidence; physical pen/tablet acceptance remains separate.

No new assistant operation, authority, permission, storage owner or workflow. Existing human Ink gestures remain the explicit exception; assistant text edits still exclude sketches.
