# FEATURE-076 / PROACTIVE-CONTEXT-002

Status: software verified and installed on codex/feature-076-context-continuity; physical acceptance and main integration remain incomplete. Fetched origin/main remains bb27bc57. FEATURE-075 exists locally at 6fdae3bd, with physical acceptance pending, and is not merged. Merged dependency/main acceptance is not claimed.

## Ownership

InteractionContextContinuity holds portable identity, lifecycle, selection and conversation affinity. It permits one slot per visual, Workspace, Study and result category, bounded to 32 process-only entries. No Uri, paths, bytes, addresses or access grants enter portable state. AndroidInteractionContextAdapter resolves through existing Workspace, Study and media owners. There is no new source store, conversation runtime, attention queue, model planner or Room migration.

Selection is captured at submission with the exact conversation. The preparer resolves those references; existing workload planning, provider capability evidence, context budgets and privacy gates remain authoritative. The executor commits using the prepared turn's exact metadata, preserving original conversation affinity after a switch. A fresh user turn gets new interaction/request identity; selection never resumes a durable run.

## Exact media and Workspace

Explicit REFERENCE cannot use ANY_RECENT to select an unrelated image. A selected image is bound to the new turn through the existing binder. Temporary selection retains a distinct lease on the same existing bytes for at most two minutes, capped by asset age; it never refreshes age. Terminal cleanup still removes the turn lease. Removal, cancellation and expiry release the continuity lease. A freed/expired source fails with reattachment instructions shown in the context notice, without a camera call or newer image substitution. Conversation B inherits none of A's selection. Restart does not restore transient references.

Use in conversation checks active lifecycle, readability, revision and exclusion policy. It does not change working_set. Revision changes block admission until Use updated source or Remove. Note reference versions include attachment identity, so replacing an image cannot silently retain the same selected reference merely because the text revision is unchanged. Existing Notebook/Study resolution provides real note passages, single-image attachments and readable text files. Explicit text-file context verifies the actual bounded read against its recorded SHA-256. Unknown, partial or changed file revisions require re-import; ordinary Study partial-source behavior is preserved. Rendered PDFs are not declared text-readable; their exact page workflow remains in Study. Selected external source content remains untrusted data, never system instructions or authorization.

Per-object privacy is checked against resolved provider locality, followed by existing global and per-conversation policy. Selection does not enable cloud images or Workspace sharing. Verified image capabilities and actual media preparation remain mandatory. Failed acquisition preserves source identity rather than falling back silently.

## UI and next actions

Home displays removable context chips and an explicit chooser over existing Notebook items, active Study sessions and image import. Opening Home, Notebook or the chooser does not acquire camera/audio/screen content or run inference. Existing capture review stays the FEATURE-075 owner. Context does not change input modality, audio endpoints, fallback or surface delivery; FEATURE-071/072 retain those owners.

Selecting Study names an exact active session and returns to the existing tutor screen. Source citations, learner setup, language preferences and solution gates remain in StudyUseCases/ViewModel. Study selection is not consent to feed its sources to an unrelated Home turn. Source-based Study proposals open existing normal learner setup.

ContextualNextActionProjector is deterministic, read-only, typed and limited to two proposals. Home scopes proposals to an exact committed turn with conservative content-length predicates; a greeting yields no new suggestions. Suggestions hide during active speech/inference/execution. Projection makes no model calls, writes, notifications or tool invocations. Personal learning proposals keep existing Memory review; there is no generic automatic memory promotion.

Save result opens the exact persisted response for user review. Confirmation uses an existing Workspace transaction and provenance identity, making retries idempotent even after a conversation switch. Provenance states MODEL_GENERATED_UNVERIFIED. Missing/changed results fail safely. AppShell routes typed navigation and checks source/session availability. Ask follow-up focuses the composer without submitting anything.

## Truth rules

UI focus != context selection.
Context selection != cloud permission.
Recent image != intended image.
Conversation continuation != agent-run resumption.
Context reference != durable memory.
Suggested action != authorized execution.
Saved result != verified factual claim.

## Verification

The requested serial SDK/cognition/app tests and debug assembly run with one worker, no parallel execution and no daemon. No model qualification or repeated hardware campaign is added. Final release gate passed 700 tests: SDK runtime 52, cognition 242, app 406; zero failures, errors or skips. Debug APK assembled and installed successfully on Samsung SM-G990B2, API 36, and MainActivity launched. The first compile attempt exposed a ContextReference import conflict with existing delegation contracts; explicit imports preserve those contracts. Subsequent exact-source gates included omission notices, bounded text revision verification and attachment revision invalidation. No provider inference, real photo or heard audio acceptance is inferred from these checks. CONTEXT-CONTINUITY-PHYSICAL-001 records one separate available-hardware pass; missing human/provider/hardware observations are never inferred from software tests.

Physical report: [CONTEXT-CONTINUITY-PHYSICAL-001](../testing/CONTEXT-CONTINUITY-PHYSICAL-001.md). The user observation request remains pending. Main is not merged or pushed because the merged FEATURE-075 prerequisite and physical acceptance are not established.
