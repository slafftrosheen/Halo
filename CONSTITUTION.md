# Halo engineering principles

Status: DESIGN TARGET. Reconciled 2026-09-18.

These are intended constraints, not claims of repository-wide conformance.
Current code is the source of truth for behavior. Record deviations in the
[audit ledger](docs/engineering/DOCUMENTATION-AUDIT.md).

1. Make runtime behavior observable, testable and appropriately cancellable.
2. Extend existing cognition/execution paths before creating competing orchestrators.
3. Keep host/platform and protocol responsibilities behind explicit boundaries.
4. Resolve actions through capabilities and return structured outcomes.
5. Preserve request/intent identity through execution and reporting.
6. Check permissions, authorization and physical safety at execution boundaries.
7. Keep deterministic local command handling and optional inference providers.
8. Preserve raw evidence; distinguish vendor analysis, captures and physical acceptance.
9. Never promote guessed protocol behavior using synthetic fixtures as hardware proof.
10. Disclose external data transfer and capability limits.
11. Handle process death, cancellation, retries and uncertain side effects explicitly.
12. Prefer measurable user-flow improvements over unused abstractions.

## Current contracts

Use actual APIs in [runtime SDK](sdk/runtime-sdk), [host SDK](sdk/host-sdk),
[provider SDK](sdk/provider-sdk), [driver SDK](sdk/driver-sdk) and
[plugin SDK](sdk/plugin-sdk). The former universal CognitiveEngine signature and
dependency diagram did not describe all current interfaces.

The composition root is an object with process-scoped mutable state. Android dependencies,
native production code and clock calls exist. “No singletons”, “Kotlin only” and universal
cancellation/platform independence are not established properties.

## Wearable acceptance target

Input -> recognition -> observation -> interpretation/reasoning -> plan -> authorized
action -> outcome -> speech -> ready. A connected device or decoder does not prove this flow.
Record build, phone/OS, wearable/firmware and evidence for physical acceptance.

Substantial proposed contract changes use the [RFC process](docs/rfc/0000-rfc-process.md).
Correcting factual documentation does not require changing code to satisfy old prose.
