# Deferred phone acceptance — 2026-10-28

The owner explicitly answered **“Phone tests later”** after ADB disconnected. Do not poll for reconnection, request speech participation, or substitute fixtures for physical acceptance during that deferral. Continue only when the owner resumes device testing. See [the evidence ledger](ISSUE-CLOSURE-ACCEPTANCE-2026-10-28.md) for completed checks and artifact identities.

## First device actions when resumed

1. Verify the actual authorized device and unlocked state. The prior phone was Redmi 23124RA7EO / Android 15, serial `f01fe673`; do not assume it remains attached.
2. Restore the outstanding QA rotation override first: original automatic rotation was 1, QA left it at 0 with user rotation 0 when the cable disconnected. Density had been reset and font scale restored to 1.0. Preserve any newer deliberate user choices. The original absent global animator-scale override was already restored. Retain Russian language and existing appearance/data/models/credentials.
3. Install the final hidden-progress-fix APK only after matching SHA-256 `26267c937f4d9f2e7e2072b2c2bf378d934d8a0109de4a2f12eb9fd23c40e386`. The phone's last installed build was `52993c500e992b5051dde03e1b09e7af8ed89586505af004087da7b37aa3f5ce`; it contains the search/idle-ring/footer changes, **not** the final hidden-progress fix.
4. Start from a clean process and repeat Screen ambient idle sampling for five minutes, with normal system motion settings. The old build used 90.85% of one core; the first correction still used 81.59% and 92.77% in subsequent samples. Record CPU, frames, memory and thermal evidence. The external `<10%` idle assertion is a diagnostic regression guard, not a release energy/latency qualification. Do not infer PASS from host tests.
5. Check that global approvals/inputs still appear and require explicit responses while passive progress is accessed through Activity. Qualify delayed approval → protected Canvas export → exact persisted effect without bypassing approval, revisions or budgets.
6. Check portrait/landscape and ambient Exit → Chat for system-bar restoration. A later capture retained an immersive viewport; isolate this before claiming complete window-policy restoration. Repeat actual carousel/list selection after the window configuration settles.

## Continue the actual source-linked workflow

- Existing QA note: `1e543729-b284-4d79-8226-1f20f4ccc618`, title `HALO-QA-20261028-SOURCE`, last read revision 1. Body is the synthetic river/leaves text recorded in the local readback.
- Existing QA Canvas: `e145715f-9ba8-45db-9045-8abd3c877baa`, title `HALO-QA-20261028-BOARD`, last read revision 2.
- **The current card is not a live reference:** node `84d031d7-fbd0-4335-b59a-0b478b3e045d` has type TEXT, null `refObjectId`, and text containing the note ID. Preserve this failed attempt as evidence.
- The corrective typed request was **not entered or sent** because the phone disconnected. Read current source and board versions, then use the existing `halo_workspace_insert` capability to insert the real note reference. Read back the resulting NOTE node and exact `refObjectId`. Do not count a Markdown link or a text ID as a live reference.
- Continue the exact source to Study, saved practice/progress, force-stop/reopen and stale-source rejection. Complete the original issue-specific chains rather than counting isolated steps as a whole workflow PASS.
- The visible composer retained sent literals after the live provider responses, but a clean restart showed an empty composer. Reproduce with a controlled typed native request and inspect UI/draft/submission receipts before changing code. Preserve newer edits and recovery text; never clear unrelated drafts to hide the symptom.

When human participation resumes, qualify the missing actual spoken workflows and active-capture continuity. The earlier user-confirmed “Open Workspace” plus heard reply remains one valid scoped speech PASS. Source/fixture tests, public provider metadata and successful navigation do not establish every spoken action, microphone route, language or offline model.

Issues 55–62 remain open until their applicable acceptance is complete or the owner explicitly accepts a specific limitation. Provider-only unavailability is non-blocking for issue 55 under its existing exception; unresolved Android-owned failures are distinct.
