# Fresh cognitive run approval lifecycle — runtime21

Physical export evidence reached validated `halo_canvas_export_note`, then failed while entering approval wait, before authorized execution with IllegalArgumentException. Source shows the normal CognitiveTurnExecutor creates a CREATED run, while the first ALWAYS approval requests WAITING_FOR_APPROVAL, a transition reserved for an admitted run. This plan does not attribute the failure to the provider or replay the export.

1. RED: exercise the actual CognitiveTurnExecutor with real DefaultAgentRunCoordinator, PolicyToolAuthorizer, and DefaultToolTurnExecutor. Use a synthetic provider response, closed synthetic Canvas arguments and sensitive/ALWAYS/NEVER_REPLAY metadata. Require durable pending approval and zero action execution. Cover approved resume, duplicate resolution/dispatch, denied/cancelled/expired input and changed argument digest.
2. After valid RED, repair admission at the canonical cognitive caller. Do not permit CREATED directly to WAITING_FOR_APPROVAL, fabricate approval, weaken ALWAYS or broaden replay. Preserve recovery admission semantics.
3. GREEN: run this class plus existing approval/recovery/runtime guards, then grouped app/APK gates. Device export remains a separate explicitly authorized action after canonical no-effect/readback evidence; tests are not physical/provider qualification.

Focused command: `./gradlew.bat :runtime:cognition:test --tests com.halo.runtime.cognition.turn.FreshCognitiveApprovalLifecycleTest --no-daemon` (use the root gate's existing environment options).

Expected initial RED: four fresh-cognitive lifecycle cases fail at invalid CREATED to WAITING_FOR_APPROVAL; the unchanged state-machine shortcut guard passes. No production files change in the RED batch.

Recorded RED21b: 5 cases, 4 failures at the exact invalid CREATED to WAITING_FOR_APPROVAL transition, 1 shortcut-guard pass, 0 errors. GREEN source change admits only newly created runs through the existing legal CREATED to EXECUTING transition in CognitiveTurnExecutor before provider/tool execution. Existing/recovery runs do not enter this branch. Coordinator transition rules, ALWAYS authorization, approval evidence and replay contracts are unchanged. Focused and grouped GREEN results remain pending the root gate.

Root verification20–21: final expanded app712/0 and cognition367/0 (runtime executed initial group, unchanged UP-TO-DATE final), both APKs built. Initial app2 failures and corrected30-case focus are retained in docs/testing/issue55/2026-10-25/gate-recovery-20-21.json; no assertions/timeouts were weakened. Device absent at04:57UTC; deployment/native/export/real Study review are NOT_RUN, not qualified by desktop results.
