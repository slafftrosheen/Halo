# Phone Acceptance & System Check Test Script

Status: EXECUTABLE TEST SCRIPT. Updated 2026-09-28.

This document describes the step-by-step physical phone verification sequence for Halo.

## Test Sequence

1. **Clean / Upgrade Install**:
   - Install `app-debug.apk` onto Android 13+ device (`adb install -r app-debug.apk`).
   - Launch app and verify Home screen loads with `HaloSignalFace` in `Ready` state.

2. **System Check — Core Probe**:
   - Navigate to `Settings` -> `Runtime Status` -> `System Check`
   - Click `Refresh`.
   - Verify the diagnostic surface refreshes; do not treat this row as proof of AI generation.

3. **Android Assistant Enrollment**:
   - In `System Check` -> `Android Assistant`, tap `Set Halo as assistant` / `Open assistant settings`.
   - Enroll Halo as Default Assistant.
   - Return to app and tap `Refresh`.
   - Verify Default role and Active Interactor are both active; then invoke Halo with the system gesture and verify service/session/capture lifecycle rows advance.

4. **Microphone & STT**:
   - In `System Check`, tap `Test Microphone`.
   - Grant `RECORD_AUDIO` permission if prompted.
   - Verify 2-second audio sample records, measures amplitude, and updates status to `PASSED`.

5. **Local Model Load & Text Inference**:
   - In `Settings` -> `Intelligence`, download, verify, and select the pinned `.litertlm` model.
   - In `System Check`, tap `Run AI probe`.
   - Verify the bounded probe executes through the selected provider/model and returns non-empty text.

6. **Local Tool Protocol**:
   - Verify prompt-mediated tool calling (`<halo_tool_call>`) parses and executes configured tools correctly.

7. **Approval & Recovery Flow**:
   - Trigger a tool call requiring approval.
   - Verify turn transitions `EXECUTING` -> `WAITING_FOR_APPROVAL` without spawning duplicate runs.
   - Approve: Verify execution completes exactly once and stores `ToolOutcomeRecorded`.
   - Deny: Verify turn records `USER_REJECTED` and tool executes zero times.

8. **Audio Route Verification**:
   - In `System Check`, tap `Test Audio`.
   - Verify test tone plays through phone speaker (or connected Bluetooth endpoint if active).

9. **HeyCyan Glasses Hardware Integration**:
   - Turn on HeyCyan glasses.
   - In `Devices` screen, connect to HeyCyan.
   - Verify connection state transitions `DISCOVERED` -> `CONNECTING` -> `CONNECTED` -> `READY`.
   - Perform test image capture: verify image transfers and thumbnail renders.

10. **Background & Process Relaunch**:
    - Place app in background while turn is active.
    - Kill process via `adb shell am force-stop com.halo.app`.
    - Relaunch app: verify `AgentRecoveryPlanner` reconciles interrupted runs cleanly without unexpected side effects.

11. **Phone Observation & Screen Control (accessibility)**:
    - Enable Halo's accessibility service, then ask for `halo_read_screen`.
    - Verify the output is delimited by `UNTRUSTED_SCREEN_CONTENT` / `END_UNTRUSTED_SCREEN_CONTENT` and that
      every node line carries `token=`, `role=` and `region=` (`top-left`, `center`, …). Raw `bounds=` must only
      appear if the display size could not be read.
    - Verify `role=list_item` for rows inside a list while a `button` inside a row still reports `role=button`,
      and verify no line reports an inert empty wrapper: those are collapsed and counted under
      `omitted=collapsed_container`.
    - Scroll a long list (Settings, a message thread) and read again: if rows were pruned off-screen, the
      scrollable node must carry `hidden=` with up to three `hiddenPreview=` entries.
    - Verify a password field shows `sensitive=[REDACTED]` and never its text or hint.
    - Act on the screen and verify the receipt line reports `status=confirmed_success`,
      `dispatched_effect_unconfirmed` or `outcome_uncertain` rather than a bare success, and that a token from a
      superseded snapshot is rejected instead of replayed.
    - Open a blocked package (Play Store, a banking or password-manager app) and verify the failure is
      `PACKAGE_BLOCKED_BY_POLICY` with the block reason, not a generic read error.
    - On a non-native surface (Flutter, Unity, canvas or a WebView that exposes nothing), verify `halo_read_screen`
      answers `status=unreadable_surface next=REPORT_TO_USER` and that Halo tells the user the screen cannot be
      read instead of retrying or acting on a guess.
    - Deny the action consent when prompted and verify nothing is dispatched; verify dismissing the prompt is not
      treated as approval.

## MINING-P5-001 — later sandbox acceptance (NOT RUN, 2026-09-30)

This is a future manual checklist. No build, test, adb, emulator or physical acceptance action was
performed for this batch. Open Settings → Runtime Status → System Check → Open phone-control sandbox
when later acceptance is authorized. Opening it must not itself capture or disclose its contents.

1. Explicitly read/preview the sandbox; inspect revision, safe labels and collection metadata.
2. Manually mutate it; an old token must return SNAPSHOT_SUPERSEDED and dispatch zero actions.
3. Find Test toggle uniquely, set checked true, verify the Boolean; repeat from a fresh generation and
   verify already-in-requested-state without another click.
4. Set Test text; confirm only when the full normalized value is exposed. Inspect journal/export/logs
   for length/digest and absence of entered values. Check long/truncated/empty and password fields.
5. Scroll Test rows; confirm directional keys/indices in the selected container. Unverified movement,
   missing metadata, end-of-list and ambiguity must stop the bounded helper.
6. Open/dismiss dialog and navigate to details; inspect action-specific evidence. When Android exposes
   identical structural roots and no navigation evidence, expect unconfirmed dispatch rather than a lie.
7. Verify unrelated text/animation does not confirm another target; duplicate labels require refinement.
8. Check semantic full/compact approval, then change the screen during approval and verify refusal.
9. Check blocked packages, permission controllers, unreadable/CAPTCHA controls and manual Continue.
10. Restart at claim/receipt/outcome boundaries: no click/scroll replay, fresh observation, observational
    set-checked reconciliation only. Old approval cannot authorize a new token.
11. Verify Home needs a package transition to the known launcher; Android return Boolean alone is insufficient.
12. Exercise bounded waits/search, P1 repeated/oscillating limits, small-model compact output, native-tool
    preference, screen injection rejection and observation-only screenshot boundaries.
13. Inspect Home, Assistant, Skills, Activity and diagnostic summaries; unconfirmed effects remain
    unconfirmed and diagnostics contain no labels, field values, snapshot trees or target tokens.

[Source ledger and all restrictions](../engineering/SEMANTIC-PHONE-OPERATOR.md).

## MINING-P6-001 — proactive automation runtime (2026-09-30)

P6 later acceptance (DO NOT RUN in this batch): validate v20→v21 Room migration; legacy reviewed re-save; time/once and boot/time/timezone reconciliation; no baseline-trigger firing; actual threshold crossings and same-side suppression; connected/disconnected debounce; exact app and notification package matching; permission/service reachability; ingestion-before-trigger; burst grouping; schema rejection/credential exclusion; grant mutation blocking; unknown vs false conditions; occurrence dedup and causal-cycle rejection; central guard; silent/default and explicit delivery; Room queue expiry/restoration/claim crashes; durable disruption budgets; exact wearable transaction with no phone fallback; Home/Activity/System Check and sandbox preview isolation. No build, tests or device acceptance has been performed.

See [Proactive automation runtime](../engineering/PROACTIVE-AUTOMATION-RUNTIME.md).
