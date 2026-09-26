# Phone Acceptance & System Check Test Script

Status: EXECUTABLE TEST SCRIPT. Updated 2026-09-26.

This document describes the step-by-step physical phone verification sequence for Halo.

## Test Sequence

1. **Clean / Upgrade Install**:
   - Install `app-debug.apk` onto Android 13+ device (`adb install -r app-debug.apk`).
   - Launch app and verify Home screen loads with `HaloSignalFace` in `Ready` state.

2. **System Check — Core Probe**:
   - Navigate to `Settings` -> `Runtime Status` -> `System Check`.
   - Click `Run Safe Checks`.
   - Verify `Core Runtime` status is `PASSED`.

3. **Android Assistant Enrollment**:
   - In `System Check` -> `Android Assistant`, tap `Set Halo as assistant` / `Open assistant settings`.
   - Enroll Halo as Default Assistant.
   - Return to app and tap `Run Safe Checks`.
   - Verify `Android Assistant` status updates to `PASSED` (`Halo is default system assistant ✓`).

4. **Microphone & STT**:
   - In `System Check`, tap `Test Microphone`.
   - Grant `RECORD_AUDIO` permission if prompted.
   - Verify 2-second audio sample records, measures amplitude, and updates status to `PASSED`.

5. **Local Model Load & Text Inference**:
   - In `Settings` -> `Intelligence`, select an installed local GGUF model (or download via Model Browser).
   - In `System Check`, tap `Test Text`.
   - Verify text probe prompt (`Reply with exactly: HALO`) executes, displays TTFT and tokens/sec, and status is `PASSED`.

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
