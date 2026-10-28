# Open Android issues implementation plan

**Goal:** Implement remaining Android product gaps in #55–61; defer every build, test, device and empirical qualification check under the owner's code-only instruction.

**Architecture:** Extend existing domain owners and ToolRegistry/CognitiveSession. Saved workflows are bounded reviewed data, never a second executor or unattended authority. Context stays attached to exact canonical source revisions. Existing Study, Screen and offline installer remain authoritative.

**Spec:** GitHub slafftrosheen/Halo #55–61, fetched 2026-10-26. Baseline `7257b47069bcc91c993f2a78e6b4e86343f7def6`.

## Constraints

- Android only; no CI, backend, replacement inference or assistant engine.
- No tests/builds/ADB/downloads/inference/provider calls in this pass.
- Preserve existing data, routes, Local/Power, Kids, disclosure, permission, cost and confirmation gates.
- Runtime/source implementation does not establish hardware, language or provider qualification.

## Tasks

- [x] #58: Extend `LearnedProcedure` / `ProcedureToolExecutor` / existing persistence with named inputs, typed argument bindings and descriptor contract snapshots. Revalidate schemas before use; hand ordinary bounded tool steps to the existing session, never dispatch inside the recipe owner. Add saved-workflow review/enable/delete through existing Skills UI and typed tools.
- [x] #57: Review Workspace source/anchor/handoff and memory owners; repair concrete missing reference or revocation boundaries without another context store. Document remaining source gaps accurately.
- [x] #59: Extend existing curated installer with complete role/readiness disclosure and explicit consent, retaining unqualified/missing states. Separate empirical qualification from implementable setup.
- [x] #60: Complete monotonic layout undo, durable layout receipts, exact-source continuation, privacy and freshness through current Screen owners.
- [x] #61: Complete tutor source revalidation, honest lesson progression, persistent learner baseline, graduated Kids hints and source-linked reusable summaries through Study owners.
- [x] #55/#56: Reconcile all product stage requirements against actual changed source; carry all deferred acceptance into one code checkpoint, without closing trackers.

## Review focus / deferred gate

Changed tool schemas or policies must invalidate saved structured steps. Missing/extra named inputs must reject. Stale/deleted sources must never attach previous sessions. A cancelled/unknown write must not replay. Disabled/deleted workflows must not be usable. Localization must cover EN/RU/LV/PL. Review source without executing checks; later build/test gate must compile all changes and qualify those cases before publication/closure claims.

Execution: independent Study, Screen and Offline specialists; primary owns Context/Workflows and shared integration. User explicitly requested implementation now, so no additional design approval checkpoint is introduced.

## Coding checkpoint

All implementation tasks above have source changes and per-issue records. Independent source review corrections were incorporated. Builds/tests remain intentionally NOT_RUN. The integrated diff remains local and uncommitted for the deferred verification/publication gate.
