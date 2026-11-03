# Issue 64 source review and correction record

An independent fresh-context reviewer inspected `b381c6834..c894b1961`, read-only, against the issue snapshot, plan, design, operability record and ledger. It identified seven Important findings, no Critical findings and no Minor findings. Its initial verdict was **not ready to merge**. It ran no compiler, test, build, app, device or provider probe.

The author accepted all seven findings and implemented one correction batch, with regression sources authored before execution. This record is not a second independent review or a passing test result.

| Finding | Source correction | Regression sources (NOT_RUN) |
|---|---|---|
| R1. Nested capability effects outlived revoked commitment authority | `EverydayEffectAuthority` carries exact suspended claim checks into brief publication, collector admission/publication and HTTP/robots dispatch. Room owners recheck after suspension and before transaction return; result publication retains exact claim and mode checks. | `takeoverInsideRealBriefAssemblyPreventsItsNestedDocumentAndMorningBaseline`; real research refresh revocation |
| R2. Background work lacked captured mode/family revision | Internal work tool captures family/mode revision once per admitted run, retains it across steps and checks it at nested effects and publication. Semantic presentation consumes the same authority, suppressing results after revision change. Fresh Local admission can still use retained evidence. | `modeRevisionDuringRealResearchRefreshPreventsPublicationButAFreshLocalReadCanRun` |
| R3. Next recurring occurrence replaced a pending shared handoff | A later tick holds the existing handoff, returns a truthful internal `performed=false` receipt and runs no capability. Finish callbacks cannot replace another occurrence's pending result/run. | `pendingSharedHandoffSurvivesAnotherScheduledOccurrenceAndCanStillBeAcknowledged` |
| R4. Stop/Take over lost exact recovery evidence | Non-authorizing `OrganizerWorkRecovery` retains plan/routine/run/time and uncertainty independently of execution approval. Stop, transfer, edit, reconciliation and restore preserve it. Unreviewed uncertainty remains needs-user; explicit recovery acknowledgement precedes new approval or human completion. UI and typed query retain the exact execution link. | Extended suspended takeover/codec readback and archive restore regressions |
| R5. Generic memory requests became Inbox captures | Removed generic remember/запомни/zapamiętaj native prefixes. Explicit Organizer capture forms remain; ordinary knowledge requests reach the existing semantic/HaloMemory path. | `ordinaryMemoryRequestsRemainWithTheMemorySemanticOwner` |
| R6. Run now made an unarmed recurring schedule impossible to enable | Eligible unarmed WAITING plans may request ARM, after checking actual routine enabled state. UI offers enablement while no schedule receipt is recorded; acceptance still comes from the scheduler receipt. | `aConfirmedManualRunCanBeFollowedByEnablingItsUnarmedRecurringSchedule` |
| R7. Positive site crawl delays and two-request cost were ignored | Public pages fail closed on any positive crawl delay this bounded adapter cannot honor. Admission reserves two request units for robots+page and refuses the operation if either unit exceeds rolling quotas. Unused units remain conservatively reserved. | `positiveSiteCrawlDelayFailsClosedBeforeASecondRequestAndReservesBothRequestUnits`; daily last-unit rejection |

## Decisions on the reviewer's declined items

- **Missing email/price/location/free-time owners:** accepted limitation. No installed owner is claimed; those requests require setup/take-over rather than fictitious execution. Adding those connectors is outside this issue's implemented baseline and future planning remains explicitly unimplemented. Cost: narrower monitoring coverage, surfaced in capabilities and product copy.
- **Paid/provider-specific search and model-assisted synthesis:** accepted free evidence baseline. Provider-neutral local/public retrieval meets the baseline contract; live provider coverage and model-route acceptance remain NOT_RUN. Cost: reference/archive/public-page coverage is bounded and not a full commercial search index.
- **Native scheduling, notifications, STT, rendering/accessibility and compilation/tests:** deferred by the user's source-first/power-repair instruction. These remain required later gates; they are not accepted as passing or silently waived.
- **Automatic restored-grant reapproval:** retain conservative invalidation. Imported work preserves facts and recovery links, not execution permission or child grants. Cost: users must review restored plans and source versions explicitly.

## Evidence status

The correction batch was checked by source inspection and generated-resource/diff consistency only. There has been no independent re-review of these corrections and no executable validation. Full combined #63/#64 compile/test gate and native/provider acceptance remain outstanding. The issue remains open; no push, merge or closure was performed.
