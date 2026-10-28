# Open issues — Android implementation checkpoint

Follow-up: [requirement-by-requirement source coverage and residual implementation, 2026-10-27](OPEN-ISSUES-SOURCE-COVERAGE-2026-10-27.md). Both batches remain uncommitted and untested.

The owner explicitly changed sequencing: implement every other open issue now, leaving testing for later. This authorizes source development of #55–61 before #62 or the alpha gate closes. It does **not** waive acceptance or authorize closing any issue.

Repository `slafftrosheen/Halo`, synchronized baseline `7257b47069bcc91c993f2a78e6b4e86343f7def6`. The changes described here are a local source checkpoint. No Gradle build, unit/instrumented test, emulator, ADB, provider request, model download, inference, speech check or hardware qualification was run in this pass. Earlier green gates and installed APKs do not qualify this diff.

| Issue | Implementation in this batch | Source record |
| --- | --- | --- |
| #55 Android acceptance | Canonical OpenRouter cancellation covers headers/body consumption; bounded JSON/catalogue/SSE/text/tool assembly; no implicit connection retry/redirect in production default transport. Existing Android domain/recovery/security owners remain. | [Android reliability](ISSUE-55-CODING-2026-10-26.md) |
| #56 Five-stage roadmap | All five stage owners developed together, with exact shared integration and truthful source/verification boundaries. Roadmap stages remain unqualified until their applicable acceptance runs. | This checkpoint and the five linked records |
| #57 Context Bridge | Canonical note block/range anchors in existing Working Set/Study references and receipts; exact selected Study-session validation; source/object exclusion and current memory dereference/reranking guards. No second context store. | [Context Bridge](ISSUE-57-CODING-2026-10-26.md) |
| #58 Actions & Workflows | Reviewed named-input typed templates, exact schema/policy/source fingerprints, complete bounded approval review, paginated saved workflows, atomic enable/delete, native My Workflows, ordinary Home/session handoff. | [Workflows](ISSUE-58-CODING-2026-10-26.md) |
| #59 Private Offline Pack | Exact starter/optional conversation manifest; full installed-byte verification; official-versus-imported voice provenance; storage/cancellation/recovery; optional conversation acquisition through existing UI/typed installer lifecycle. Never auto-selects or qualifies an artifact. | [Offline Pack](ISSUE-59-CODING-2026-10-26.md) |
| #60 Screen & Presence | Exact source continuation, real routine revision, native persisted follow-up cards, truthful active task/freshness, monotonic undo, bounded caches and ambient sheet redaction. | [Screen](ISSUE-60-CODING-2026-10-26.md) |
| #61 Study & Kids | Before/after inference source validation, exact-version history, persistent learner baseline, genuinely graduated hint history, scoped journey actions, localized review copy and stable source-linked summary artifact. | [Study & Kids](ISSUE-61-CODING-2026-10-26.md) |

## Integration decisions

- Existing ToolRegistry, CognitiveSession, AgentRun, Notebook/Workspace/Study, routine/watch, model/speech and profile owners remain authoritative. No parallel assistant, database, dispatcher, scheduler, runtime replacement or backend was introduced.
- Saved workflow data is not an execution grant. Every real step still uses current schema/source/permission/policy, exact individual approval and durable outcome receipts. Unknown writes are not replayed automatically.
- Source/material exclusions remain effective in Local and Power. Processing mode does not create a new object-sharing, child-media, provider, OS, cost or unattended permission.
- Ordinary manual editing and navigation remain available independently from AI context inclusion. Source revisions remain canonical owner metadata.
- Offline acquisition verifies bytes and provenance, not model quality, latency or language coverage. Qualification stays with existing exact-device/artifact/backend evidence owners. Historical Qwen CPU failure is retained separately from current unmeasured candidates.
- New consumer controls use native existing components and EN/RU/LV/PL resources. No new bitmap assets are required by these changes.

## Source review

Independent read-only review found and drove fixes for workflow result truncation, complete target review, lifecycle/lock handoff, installer pause/registration races, bounded setup-status paging and imported-versus-official voice provenance. Primary integration review also covers selection changes at the same source revision, metadata-only manual revisions, memory reranking and summary artifact identity. These are source reviews, not passing tests or compilation evidence.

## Deferred gate

When the owner resumes testing: compile the entire integrated diff and both APKs, run meaningful affected regression/contract/policy/localization suites, then exercise real persisted cross-tool journeys and recovery on an authorized target. Include workflow review/paging/CAS, source-anchor staleness and memory revocation, summary restart identity, Kids hints and source edits during slow responses, Screen lock/ambient/source continuation, OpenRouter blocked-response cancellation/bounds, installer corruption/pause/process-kill/storage/rollback and offline network isolation.

Actual spoken input/audible playback, device/language/backend performance, thermal/battery, accessibility/responsive screens, provider/cost/account and Play/hardware acceptance remain separate. No acquisition candidate is promoted by a catalogue row or file hash. License/provenance audits and supported-language claims require their own evidence. Keep #55–62 open until applicable verification or an explicit owner-accepted exception is recorded.
