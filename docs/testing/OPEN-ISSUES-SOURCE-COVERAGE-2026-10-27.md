# Open issues: source implementation coverage — 2026-10-27

Continuing the owner's instruction to finish coding every task before testing. Repository baseline is `7257b47069bcc91c993f2a78e6b4e86343f7def6`; the previous batch and this follow-up are local, uncommitted changes. The live open issue bodies #55–62 were read for this pass. No issue was closed or its acceptance boxes marked complete.

**Execution boundary:** no build, test, lint, emulator, ADB, microphone, inference, provider request or model acquisition was run. Regression source and read-only source reviews are implementation work, not passing evidence. No commit or push was performed.

## Requirement coverage

Each linked ledger maps the issue's work packages to concrete canonical source owners and separates implementation from empirical or owner-dependent acceptance.

| Issue | Source coverage | Residual code completed in this pass |
| --- | --- | --- |
| #55 Android consolidated backlog | [All workstreams and Phase A/B/C](ISSUE-55-SOURCE-COVERAGE-2026-10-27.md) | Ignore old capture/conversation rejection events; terminate unrecoverable Voice Mode using the event's fallback capture ID. Existing cancellation, barge-in, playback and provider owners inspected. |
| #56 Five-stage umbrella | This table plus the five stage ledgers below | Integrated existing source/context, workflow, offline, Screen and Study owners. The owner's newer instruction authorizes parallel source development before alpha acceptance; it does not waive the acceptance gate. |
| #57 Context Bridge | [A–F](ISSUE-57-61-SOURCE-COVERAGE-2026-10-27.md) | Preserve exact note anchors on unchanged-source restart, deny excluded sources before resolution and retain exact source versions in saved history. |
| #58 Actions and Workflows | [A–F and all five recipes](ISSUE-58-SOURCE-COVERAGE-2026-10-27.md) | Registry-owned template validation before review/save; execution policy and triggers included in fingerprints; accessible typed native inputs. |
| #59 Private Offline Pack | [A–F and acceptance boundaries](ISSUE-59-SOURCE-COVERAGE-2026-10-27.md) | Manifest-bound source/license acknowledgement, complete ordinary approval review, temporary publication space admission and cancellable copy/hash publication. |
| #60 Screen and Presence | [A–H](ISSUE-60-62-SOURCE-COVERAGE-2026-10-27.md) | Calendar loading/failure/freshness and privacy cleanup; current edit callbacks; ambient/reduced-motion freshness feedback. |
| #61 Study and Kids | [A–H](ISSUE-57-61-SOURCE-COVERAGE-2026-10-27.md) | Versioned persisted image/PDF focus, focus-specific hint/history, scoped page and reader controls, bounded current-view pagination, validated listen-then-explain sequencing and completed-lesson summary export. |
| #62 Interface 4 | [All requirement groups](ISSUE-60-62-SOURCE-COVERAGE-2026-10-27.md) and the existing Interface implementation ledger | Continued canonical per-screen controls/context and shared presentation. No duplicate shell, conversation, runtime, policy or persistence engine. |

## Integration review

Source review caught and corrected template validation delayed until use, unlabelled Boolean choices, asynchronous source-reading handoff, completed-session summary refusal, and a model-supplied fingerprint being mistaken for user source/license review. First installer review now must be the complete exact text shown by the ordinary approval owner, bound to the selected download scope and current EN/RU/LV/PL language. Nothing in saving a workflow or installing a pack grants action permission or model qualification.

Study current-view schemas include the actual source/page/reader/pagination fields and close the result object. Collection paging preserves exact identities under the runtime output ceiling; it never emits a truncated JSON document or invents empty history. The same current native owner handles typed requests and final-STT requests.

## What remains after the source pass

The requirement maps identify implementations for the scoped coding work; they do not establish 100% product acceptance or prove that no defect remains. Builds and integrated tests are deliberately not started. Later verification must cover all changed contracts, persistence/restart, cancellation, approvals, protected modes, current-screen context, responsive/accessibility/localization and real cross-tool workflows before acceptance can be marked.

Some issue tasks are not code: actual speech/audibility, exact-device/model/backend/language performance, physical stylus/tablet behavior, license/redistribution audit and Play-store owner actions need their own evidence. Unsupported PDF extraction/format operations and unavailable hardware remain honest capability limits where the issues permit smaller supported routes; no fabricated implementation or success is substituted. Issues #55–62 remain open.
