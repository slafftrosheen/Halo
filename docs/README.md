# Halo documentation

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

**Current executable code and its actual wiring are the source of truth for implemented behavior.**
Tests establish what their assertions exercise; passing runs need dated results.
Physical behavior needs build/device/firmware evidence. Comments, filenames, enum labels
and old reports do not establish that something works.

## Start here

- [Current state](../CURRENT-STATE.md)
- [Current architecture](04-architecture/ARCHITECTURE.md)
- [Documentation audit](engineering/DOCUMENTATION-AUDIT.md)
- [Product direction](../PROJECT-DIRECTION.md)
- [Proposed next phase](14-roadmap/NEXT-PHASE-2027.md)
- [GitHub/web implementation research](engineering/IMPLEMENTATION-RESEARCH-2026-09.md)
- [HeyCyan implementation](protocols/heycyan/IMPLEMENTATION_STATUS.md)
- [UI route inventory](11-ui/UI-IMPLEMENTATION-STATUS.md)
- [User redesign guide](03-design/HALO-WORKSPACE-DESIGN.md)
- [Notebook blueprint](02-product/NOTEBOOK-WORKSPACE-BLUEPRINT.md) and [delivery plan](02-product/NOTEBOOK-DELIVERY.md) — proposed, not current storage/UI
- [Notebook editor and Study plan](rfc/0002-notebook-editor-and-study.md) — next product target;
  [reference mining and source reconciliation](engineering/NOTEBOOK-STUDY-MINING.md), reviewed 2026-09-28

## Interpretation rules

| Label | Meaning |
|---|---|
| CURRENT SOURCE MAP | Source-backed description, not a build or hardware certificate |
| DESIGN TARGET | Intended requirements/examples; conformance is not implied |
| PLANNED / PROPOSED | Future work |
| HISTORICAL RESEARCH | Recorded observations/vendor analysis, not current implementation |
| HISTORICAL REPORT | Previous narrative, not current verification |
| THIRD-PARTY / RAW EVIDENCE | Preserved material; provenance and attribution matter |

For factual conflicts, trace the caller, implementation and configuration in the checkout.
Record disagreements inside code instead of selecting the most optimistic comment.
For intended direction use PROJECT-DIRECTION, CONSTITUTION and the relevant design/RFC.
Neither a specification nor this index turns an unimplemented requirement into a feature.

HPS documents are design drafts, not executable SDK definitions. Protocol research retains
provenance; use the implementation status page to enter the current driver. Vendor documents
and raw captures are not rewritten to agree with code.

## Maintenance

Link implementation claims to source and state whether they are wired, merely declared,
tested or hardware-observed. Record commit, command/scenario and result for verification.
Keep external ideas in research until implemented. Correct source maps when behavior changes.
