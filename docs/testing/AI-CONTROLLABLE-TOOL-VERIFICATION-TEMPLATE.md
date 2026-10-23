# AI-controllable tool verification

Required for each new or changed interactive tool. Missing contract or evidence blocks declaring it complete. This is a review template, not an execution or authorization system.

## Feature and canonical owners
- Feature, issue, source commit and exact domain owner shared by UI and assistant:
- ToolRegistry descriptor IDs and ToolOperability version/capability/operation:
- Actual registration and `registry.operabilityReport(featureBindings)` output (must be empty or resolved):
- Functional operation coverage: query/list, create/edit/export/delete where supported. Explicit justified hardware/security/human-gesture exceptions:
- Discovery descriptions, plain-language examples and ambiguity clarification:

## Typed operation contract
- Closed typed argument schema, bounded result schema/encoding, durable result IDs and ActionTruth:
- Exact selected source IDs/revisions and stale-source behavior:
- Missing setup, permission, disabled capability, unavailable/trash/revoked source recovery:
- Existing runtime timeout/cancellation, confirmation and replay/idempotency policy:
- Process-death reconciliation, duplicate/uncertain write handling, undo/preview where meaningful:
- Private/Power, content categories, Kids, Android/external permissions and approval owners:

## Three functional workflows
For each workflow, record typed request and actual spoken request, resolved same semantic operation, policy outcome, saved object/result IDs and versions, provenance, restart/readback and truthful feedback. Run on the on-device route and permitted Power route. Differences due to missing hardware/provider are NOT_RUN, never a pass. Do not store private prompts/audio/provider credentials in repository evidence.

| Workflow | Typed on device | Spoken on device | Typed permitted Power | Spoken permitted Power | Durable readback |
|---|---|---|---|---|---|
| 1 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 2 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 3 | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

## Evidence and acceptance
- Source review and regressions authored before first gate:
- Focused build/test results, exact APK/source identities and render inspection:
- Device serial/API, installed APK readback and native results:
- Actual STT-final recognition and permitted provider/approval evidence:
- Remaining unavailable inputs, human/device/provider checks and issue state:

Never equate dispatch with display or persisted effects. Model text is not permission; document/web/card contents are untrusted data. No replay of uncertain writes merely to obtain a success message.
