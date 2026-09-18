# Android real-hardware week

Status: DESIGN TARGET / TEST PROCEDURE. Updated 2026-09-18.

Use [seven-day protocol](7-day-hardware-test.md). This is a planned test, not a result.

Cover connection/reconnect, cognitive routing, provider loss, actual audio route,
permission revocation, phone lock/background/recreation, battery/thermal behavior,
capability freshness and diagnostics. Record current build/OS/firmware for each scenario.

Disabling battery optimization is not proof of unrestricted background microphone access.
Unsupported actions and fallback behavior must be observed, not assumed to fail gracefully.
