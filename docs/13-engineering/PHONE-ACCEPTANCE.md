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
