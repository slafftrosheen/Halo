# Issue 58 — reviewed workflows source checkpoint

Baseline `7257b4706`; implementation only. No build, test, ADB, provider request, download or workflow execution was run. New regression sources are written but **NOT_RUN**.

## Implemented

The existing `LearnedProcedureStore` remains the saved-sequence owner. Additive version-1 fields carry named text/integer/boolean inputs, whole-argument bindings, reviewed literal arguments and an exact descriptor contract fingerprint. Legacy instruction recipes remain readable; structured templates require the exact current workflow `sourceVersion`. Inputs never interpolate strings or become executable code. Templates cannot recursively manage workflows.

`ProcedureContracts` fingerprints the canonical tool schema, owner/capability/operability version, source guards, security/replay, requirements/dependencies, resources and automation metadata. Proposal/save/use recheck registration and contracts. Binding parses only declared primitive types and uses the existing registry schema validator; the real domain owner rechecks source revision and authorization at actual dispatch. Missing/extra inputs, null SDK arguments, changed contracts and oversized previews refuse instead of guessing.

The proposal creates a complete human-readable review of exact literals and bindings, capped at 2,000 characters. Typed saving requires that exact review as an approval argument. The additive generic `ToolPresentation.reviewArgument` projects bounded review data in full through the existing durable approval surface; it does not add another confirmation or authority owner. A larger sequence must be split so a consequential review is never silently truncated. Use previews stay below the registry's 4,000-character result ceiling; lists paginate under 3,800 characters, with a real `nextOffset`.

Saved-workflow list, exact-version enable/disable and delete now have typed ToolRegistry operations, discoverable skill metadata, local capability metadata and versioned operability. Mutations retain ALWAYS approval / NEVER_REPLAY. Personal workflow metadata/templates/results use the existing Workspace disclosure category. Saving/enabling never grants unattended, provider, phone, OS, cost or child permissions.

Native **My Workflows** is available from existing Skills. It lists saved sequences, shows their complete literal/binding review, collects named inputs, refreshes, enables/disables and explicitly confirms deletion. Manual edits use the same atomic compare-and-set store boundary as typed tools. Use validates the current row and schemas, then checks resumed/unlocked state immediately before sending an explicit request to the existing Home/CognitiveSession. Input drafts and the selected workflow survive recreation through saved state. No step is executed by this panel or by the template reader.

The selected workflow also projects its exact ID/version and registered operations through the existing Skills page lease and metadata-only current-screen owner. Closing/disposal clears that projection. No template bodies or input values enter current-screen metadata.

Persistence now acknowledges the SharedPreferences commit. Full capacity refuses new recipes instead of evicting older reviewed ones. Malformed/oversize/duplicate storage refuses access instead of becoming an empty collection and being overwritten. Legacy records keep their defaults. No second workflow database, scheduler, tool dispatcher or assistant engine was added.

Actual step admission, serialization, run/interaction identity, cancellation, durable approval/recovery and outcome receipts remain with the existing CognitiveSession/ToolRegistry/AgentRun owners. The template tool returns **PREVIEW_ONLY**; each actual action must be dispatched normally. A failed/denied/cancelled/unknown outcome stops progression; uncertain writes require reconciliation. No promise of automatic compensation is made.

## Recipe primitives and truthful limits

| Requested recipe | Existing canonical primitives reused | Deferred evidence |
| --- | --- | --- |
| Photos → local PDF/composite → Workspace | WorkspaceMediaFiles ordered collage/batch; managed versioned output and file/Canvas handoff. This smaller composite path is supported; format-preserving PDF assembly is not advertised. | Real selected-photo output/reopen/export and receipts |
| Note → linked Canvas → later source edit | NotebookCanvasRepository references and WorkspaceContinuity source freshness | Real fresh/stale readback and restart |
| Document → Study → reviewed practice → resume | Study source/version resolver, StudyUseCases, Keep/Discard, existing persisted journey | Real grounded output and accepted-card readback |
| Studio crop/annotation → new version → Canvas/Study | Existing StudioService and Workspace managed export/provenance | Actual edit/save/export/reopen and lineage |
| Approved source reminder → routine/watch → receipt/cancel | Existing routine/watch approval, scheduling, delivery and cancellation owners; Screen follow-up projection | Real authorized delivery/cancel and exact run receipts |

The issue explicitly permits a smaller truthful workflow when an owner cannot perform a format operation. No PDF editing, delivery, source reference or successful action is simulated.

## Deferred verification

Compile the complete batch, then run `ProcedureWorkflowToolsTest` and the existing ProcedureAdmission/ToolRegistry/policy/skill/approval/persistence suites. Cover exact typed values, schema/policy changes before save/use, extra/missing inputs, full approval visibility, bounded pagination, CAS/replay, commit failure, malformed existing storage, saved-state restoration, background/lock during preparation, and preservation of unrelated Home drafts/actions. Source review is not execution evidence.

Run the issue's five real typed workflows and three actual final-STT workflows later on the authorized device/provider route, with persisted object/source/version and outcome readback. Keep #58 open pending that acceptance. No new CI or paid action is introduced.
