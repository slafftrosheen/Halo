# Actions and Workflows source coverage — 2026-10-27

Source implementation against issue #58, continuing the uncommitted checkpoint on `7257b4706`. No build, test, lint, workflow execution, device, provider request, publication or issue mutation occurred. Regression source was written for the later gate.

## Remaining code addressed

- `ToolRegistry.validateTemplate` now validates saved literal values and whole-argument input types with the same registered schema/dependency owner as ordinary dispatch. Missing required operands, unknown/mismatched bindings, overlap and invalid literals refuse the proposal. It never fills in imaginary source IDs or revisions. Actual supplied values still undergo full ordinary validation before use/dispatch.
- `ProcedureToolExecutor` uses that validation before proposal and again after approval before saving. The composition root injects the existing registry callback. Contract fingerprints now include execution policy; workflow source versions include trigger phrases as well as steps, inputs and enabled state.
- Native `MyWorkflowsPanel` supplies localized explicit Yes/No choices for Boolean inputs, numeric keyboard for integer inputs, a named accessibility switch, frozen fields while preparing an action and a visible refusal if the review cannot be rendered. Existing atomic management, exact-version checks and foreground/unlocked handoff remain.
- `ToolTemplateValidationTest` covers literal constraints, missing/wrong/extra/overlapping bindings and mandatory full validation after binding. It is **NOT_RUN**; no passing compilation is implied.

## Every implementation slice

| Slice | Canonical source implementation | Remaining qualification |
| --- | --- | --- |
| A — capabilities | `ToolRegistry`, `ToolDescriptor`, `ToolOperabilityReview`, `HaloToolDescriptors`, `ProcedureOperability`, host capability publisher and domain operability catalogues | Registered review and native/typed/final-STT parity runs deferred; individual OS/credential/gesture exceptions remain explicit. |
| B — plans | `LearnedProcedure`, `ProcedureContracts`, `ProcedureReview`, `ProcedureToolExecutor`; runtime `ExecutionPlan`, `PlanPresentationProjector` | Templates are bounded data with exact contracts and reviewed operands. No executable script or additional authority. Model proposals are not completion evidence. |
| C — execution | Existing CognitiveSession/turn coordinator, `ToolRegistry`, policy authorizer, resource arbiter, `AgentRunGuard`, `AgentRunLimits`, tool invocation claims/journal, domain revision guards | Template reading never executes. Every actual step re-enters the existing owner; concurrency, deadlines and replay/reconciliation remain runtime/domain responsibilities. Fault/restart/cancellation proof deferred. |
| D — progress/recovery | `HomeTaskProgress`, Activity run receipts, `DefaultAgentRecoveryExecutor`, `AgentRecoveryPlanner`, durable approval/input requests and `RunContinuityResolver` | Uses observed steps and confirmed/unknown outcomes. A queued operation is not completed. Real cancel/resume/unknown-write recovery deferred. |
| E — My Workflows | Existing `LearnedProcedureStore`, SharedPreferences implementation, reviewed proposal/save/list/use/enable/delete, `MyWorkflowsPanel` on Skills, metadata-only Skills page lease | Exact schema/owner/requirements/security/replay contract fingerprints; no unattended grant or automatic compensation. Native persistence/CAS/approval proof deferred. |
| F — voice/UX | Shared Home `submitText` / final-STT CognitiveSession entry, ordinary action approvals, native workflow review and EN/RU/LV/PL resources | No parallel execution pipeline. Actual speech, interruption, readability, TalkBack and language acceptance deferred. |

## Five recipes

1. Photos → ordered local composite → Workspace/reopen/export: existing Workspace media/file owners. The issue explicitly permits a smaller supported composite; arbitrary format-preserving PDF editing is not claimed.
2. Note → live linked Canvas card → fresh/stale revision: NotebookCanvasRepository / WorkspaceContinuity reference semantics; copied text remains distinguished.
3. Document → grounded Study → reviewed practice → save/resume: exact Study source resolver, StudyUseCases and native learner draft/Keep owners.
4. Studio crop/annotation → managed new version → Canvas/Study: StudioService / managed Workspace export, source lineage and reversible editing owners.
5. Approved source reminder → routine/watch → delivery/cancel: existing routine/watch policy, scheduler and durable delivery/cancellation owners; Screen only projects their receipts.

The recipe primitives are source-backed, not five executed demonstrations. A missing configured provider, inaccessible source or unsupported format returns its existing availability/refusal rather than simulated success. The issue's five typed and three spoken demonstrations, security negatives and recovery matrix remain for the explicitly deferred testing phase.
