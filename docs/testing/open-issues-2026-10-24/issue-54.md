# #54: [P0][HALO-TOOLS-AI] All user-facing tools must be operable by Halo via voice and natural language

Original: https://github.com/slafftrosheen/Halo/issues/54 · updated 2026-10-24T06:04:13Z. Full source is retained in source-issues.json.

No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.

### I54-P01: Three semantic workflows

Use three controlled fixture workflows: (1) existing note→structured edit/attachment→Canvas exact card, (2) exact file/PDF region→Study learner attempt/hint→saved note/resume, (3) Studio source→edit/export→Canvas/working-set handoff. Execute each by typed request and actual speech on-device and on a permitted Power route.

**Pass evidence:** Record exact target/saved IDs, versions, source hashes, semantic operation/approval/result truth and restart readback. Spoken/typed resolve the requested same canonical targets/outcomes; no navigation-only parity or synthetic text presented as speech.

Status: NOT_TESTED. Evidence: pending.

### I54-P02: Safety and operability

Inspect actual ToolRegistry feature contracts/discovery, then deny restricted/destructive/external actions, revoke source/permission/capability, race revisions, cancel/interrupt and restart before acknowledgement; repeat duplicate/uncertain requests and Private/Power transitions.

**Pass evidence:** Existing approvals/Kids/OS/privacy policies hold; exact closed schemas and meaningful domain operations; truthful receipts and no automatic uncertain replay. Private never silently uploads workspace/screen/memory.

Status: NOT_TESTED. Evidence: pending.

### I54-P03: New ink controls

Review halo_ink_status/document/start/stop/selection registration, bundled/runtime risks, WORKSPACE provenance, output quotas and same-interaction region guards. Verify pairing/certificate approval stays a protected human security act.

**Pass evidence:** Start needs fresh direct authority + exact source revision + qualified activation. Stroke/selection cannot call tools or grant cloud/app permission. Gated ink does not masquerade as completed physical #30 acceptance.

Status: NOT_TESTED. Evidence: pending.

## Requirement-by-requirement review

- [ ] **I54-R01** (original line 7, Agent instructions): 1. First inventory existing tools and owners against **`ToolRegistry`, `ToolDescriptor`, `CapabilityCatalogue`, `ToolDiscoveryService`, `PolicyToolAuthorizer`, `CognitiveSession`, `AgentStep` journal, existing host native actions/permissions**. Extend those owners, do not create another execution/approval engine.
- [ ] **I54-R02** (original line 8, Agent instructions): 2. Define an additive versioned **AI-operability contract** for every feature: capability ID; user-facing descriptions/discovery; typed argument and result schemas; read/query/list, create/edit/export/delete operations as applicable; supported modes/devices; actual availability and permissions; idempotency/replay policy; timeout/cancellation; undo/preview where meaningful; provenance and result receipts.
- [ ] **I54-R03** (original line 9, Agent instructions): 3. Register discoverable, actionable operations with the existing runtime at feature integration time, rather than exposing static tool-name suggestions. Tool UI and assistant invocation must converge on the **same use case/domain owner**, returning equivalent outcomes.
- [ ] **I54-R04** (original line 10, Agent instructions): 4. Assistant should resolve a named object/selection/source revision and clarify ambiguity. Ensure real STT-final intent -> safe typed plan -> authorization -> execution -> verified feedback. Do **not** execute guessed tool names, trust untrusted file/web text as instructions, treat model output as permission, or allow hallucinated success.
- [ ] **I54-R05** (original line 11, Agent instructions): 5. Existing Android permissions, Kids restrictions, Private/Power dispatch gates, scoped provider billing/limits and approval for consequential actions stay enforceable. Prefer one user-configured permission/policy over repetitive Halo 'Allow tool?' prompts for routine allowed actions; never bypass Android or external effect confirmations.
- [ ] **I54-R06** (original line 12, Agent instructions): 6. Both hands-free voice (#38) and ordinary chat (#44) discover/use these operations, including errors, cancellation, continuing long tasks and clear 'needs setup' / 'unavailable' states. Do not claim arbitrary cursor/pen gestures can be automated when only a human hand can perform them; expose meaningful document and tool operations.
- [ ] **I54-R07** (original line 13, Agent instructions): 7. Update **`.agents/AGENTS.md` / engineering review checklist** when implementing so every future feature must demonstrate assistant-callable functional parity (or document an explicit, justified hardware/security exception). Apply this rule retroactively to high-value existing tools and to new Studio/Workspace tools.
- [ ] **I54-R08** (original line 14, Agent instructions): 8. Keep tool status and user-facing presentation aligned with Home Tools catalog #45 and Orb #46; UI convenience wrappers are *not* separate execution systems.
- [ ] **I54-R09** (original line 17, Acceptance / Definition of Done): - [ ] At least three real cross-tool workflows execute end-to-end by **typed request and by spoken request** with exactly the same saved objects/outcomes and truthful receipts, both on-device and permitted Power route.
- [ ] **I54-R10** (original line 18, Acceptance / Definition of Done): - [ ] Agent cannot perform restricted/destructive/external actions without the existing required approval, and Private never silently sends content to cloud.
- [ ] **I54-R11** (original line 19, Acceptance / Definition of Done): - [ ] Discoverability, typed validation, missing-capability/permission recovery, cancellation/interrupt, process death, source-version conflicts and duplicate/replay safety are demonstrated.
- [ ] **I54-R12** (original line 20, Acceptance / Definition of Done): - [x] Add a reusable 'AI-controllable tool' verification template to the development workflow; missing it blocks declaring a newly shipped interactive tool complete.
