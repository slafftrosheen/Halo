# Issue 40 completion — implementation plan

Original package base: 0513fb53d30583948c3e1d019896fbd7bbce82b4. Integration base: 3bd7d934095c54ce956ce06bf98281a1ef325b61 (Ink and consumer-catalogue changes retained). Owner request: finish source and close the implementation issue; no builds, test execution or CI.

1. Introduce bounded, local-only spend reservations and one-shot request consent. Unknown charges remain unknown on cancellation/failure; no invoice or provider cap claim.
2. Add direct BYO OpenRouter web lookup (free text route + explicit paid web plugin), safe API-derived citations, timestamp and reported cost. No automatic cloud fallback, no tools, no content or credentials in the spend ledger.
3. Wire search into the existing Home composer and costs into existing Usage settings. Respect local-only and Kids privacy; opening either UI performs no HTTP request.
4. Put an exact, expiring, single-use approval in front of WorkspaceImageService; regeneration and edit each require new approval. Recheck permissions and request binding immediately before dispatch.
5. Add a separate paid/unknown-price media-understanding opt-in to runtime target admission, including pinned targets, and show provider-native spend controls and manually refreshed account usage.
6. Add regression source for consent identity/expiry/replay, budget persistence/concurrency/unknown outcomes, malformed citations and HTTP errors. Perform source/diff/resource checks only; preserve device acceptance separately.

Review focus: cancellation after dispatch is potentially billable; settings/model/credential changes invalidate approval; missing citations cannot become a successful grounded result; unknown price is not zero; finite local limits cannot silently lose accumulated reservations.
