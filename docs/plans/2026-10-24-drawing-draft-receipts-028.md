# Distinguish equal-content drawing draft replacements (#28)

Base: fetched main `f5610e490708eeb0ae60a6ddffb3a9b182adc206`, clean0/0 before editing.

Confirmed gap: repository compare-and-clear currently compares the decoded draft data class. Two separate editor callbacks with identical source/page/ink produce equal values. A delayed old Save/Cancel can therefore clear a newer identical draft, including an A→B→A content sequence. Existing regressions cover changed content, not this case.

Add an opaque per-new-draft UUID receipt to the existing CanvasDrawingDraft. Current sketch and annotation callbacks already construct a fresh draft per callback; source rebinding copies keep that receipt. Persist it in canvas-drawing:v2. Continue reading/writing legacy canvas-drawing:v1 without minting an identity on decode. Reject missing/malformed v2 identities and unknown versions. No Room migration, new store, timestamps-as-version, UI, schema/approval engine or assistant operation. Existing full source/payload checks and Room transaction compare-and-clear remain canonical.

This receipt identifies a newly emitted draft snapshot, not every repository write: retrying the same receipt/snapshot is idempotent, and Kotlin copies intentionally retain identity unless constructing a new snapshot. This fixes identical-content replacement cleanup; it does not claim a source-node monotonic revision protocol, every ABA source edit, a process-death UI test or physical stylus qualification.

Complete production/regression source and review before one focused app regression +both APK gate. Verify v1/v2 roundtrip, invalid/missing receipts, different new receipts with identical payload, legacy-versus-current conflict, exact-owner cleanup, real ViewModel Save/Cancel, and file-backed database close/reopen. Install/readback both APKs, run one isolated native disk-owner reconstruction case, resume MainActivity. Publish implementation/evidence, keep #28 open for original physical/tablet/long-session/process-death acceptance. No repeated unrelated model/embedding gates or CI/Actions.
