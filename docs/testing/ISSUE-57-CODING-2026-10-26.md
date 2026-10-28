# Issue 57 source checkpoint — 2026-10-26

Issue: https://github.com/slafftrosheen/Halo/issues/57

Coding only. Build/tests, providers, ADB, hardware, process-death injection, real spoken flows,
TalkBack, layouts and translated-copy qualification are **NOT_RUN**. No acceptance PASS or issue
closure is implied, and issue 55's Android acceptance has not been re-established in this batch.

## Canonical source map

| Object/selection | Existing owner and identity | Reference semantics |
| --- | --- | --- |
| Note | Notebook document blocks; saved revision; attachment-aware Workspace version | Existing live reference blocks remain links, distinct from intentional text copies. |
| Saved note paragraph/range | Existing `DocumentSelection`, canonical block ID, UTF-16 start/end, source revision | New portable `StudyTextAnchor` records identity/offsets only. No copied paragraph or second store. |
| File/image/PDF | Workspace object/file identity, availability and content version/hash | Existing URI/managed-file owner and verified vision gates remain authoritative. A file name is not extracted PDF text. |
| Canvas card/Ink region | Existing Canvas revision/node/edge owners, Ink bookmark context and source version | Existing region snapshot and source chain retained. A real selected/verified region is required; no guessed page geometry or pen data. |
| Studio asset | Existing Studio project/output revisions and Workspace file lineage | Existing Save As/export/handoff path retains the original source. |
| Study | Canonical Study session/sources JSON and existing bounded local journey | Exact note anchors now persist/read back in source JSON and source-version fingerprints; scoped active-lesson tools remain the execution owner. |
| Conversation attachment/result | Existing media lease/Workspace binding and persisted turn/run identity | Copied answer notes retain their existing result provenance; no new automatic memory. |
| Personal memory | Existing opt-in candidates/Keep, Memory records/revisions, provenance and scoped retriever | Correction/forget remain canonical writes. Ranked snippets are revalidated before becoming new context. |

## Implementation by work package

**A — source map:** The owners above were inspected before extending existing source JSON, Working
Set rows and run receipts. No global database, new media store, extractor or orchestration engine.

**B — anchors:** Saved note selections use canonical block identity and exclusive UTF-16 offsets.
The editor captures its actual selection, flushes pending source edits, checks that the selected block
and range did not change, then starts Study with that exact anchor. The existing resolver returns only
the anchored text and exact locator. Ranges are bounded; invalid offsets/surrogate boundaries and stale
revisions refuse. Session JSON roundtrips anchors. Continue preserves anchors; restart refuses a stale
anchor instead of silently expanding to the whole note. Existing PDF/Ink/Canvas semantics remain
capability-bound. Raw PDF paragraph text extraction is still explicitly unsupported where no extractor
exists; the genuine saved-note selection route is implemented without pretending PDF OCR exists.

**C — assembly/receipts:** The existing `halo_workspace_select` accepts additive exact note block/range
operands. It stores only a versioned selection reference in the existing Working Set row. Query and
dereference validate current saved source/offsets; stale selections yield no content, never whole-note
fallback. Actual used-source references carry the same anchor through bounded context entries,
candidates, `ContextUsed` journal persistence, receipt readback and the existing receipt tool. The source
inspector labels selected passages while keeping internal block IDs in structured metadata. Existing
budgets and untrusted-data framing remain. Working Set and acquired Workspace references now check
per-object permissions at assembly as well as dereference.

AI inspection of historical receipts also receives the actual target location. It checks each source
and memory ID before metadata dereference, after resolution and at tool return. Excluded or remote-blocked
entries are omitted entirely, including titles, versions and anchors; `truncated` indicates omitted
coverage. Manual local source-history inspection retains a separate metadata-only path. The receipt
tool's closed result schema explicitly includes its nullable, bounded block/range anchor. Canonical
metadata versions remain readable for manual navigation; AI exclusion applies at context/query/
dereference/export boundaries rather than hiding the object.

The receipt tool paginates source and memory collections independently using optional `sourceOffset`
and `memoryOffset`. Its closed result schema includes both current offsets/counts and nullable next
offsets. The complete serialized JSON envelope, including escaped metadata and continuation fields,
is bounded to 3800 characters below the registry output limit. Exact versions/anchors are never cut;
one individually oversized source explicitly rejects and points to local history inspection. Pages
reflect current permissions/metadata rather than an immutable cross-call snapshot. The native source
inspector retains its existing 20-source projection. This integration change was source-reviewed only.

An inline excerpt cannot inherit a newly selected range at the same document revision unless it
matches the canonical selected snapshot. Assembly checks current anchor identity for both acquired
and Working Set references, including whole-object to selected-range changes. It omits mismatches
without replacing old content with another selection.

**D — handoffs:** Selected passage → canonical Study session persists through the existing editor and
Study owners, and saved Study tool readback exposes the anchor. Study selection tokens now bind the
active saved session and its exact sources. The Android context adapter validates those references
rather than silently exempting and dropping them, and rechecks Workspace/Study after asynchronous media
preparation. The default graph still keeps Study out of generic source injection: choosing a lesson
navigates to its native owner; the active-lesson tools provide its real operations. No lesson content is
silently attached to unrelated Chat. Existing Workspace/Canvas/Studio handoffs, consent and receipts
remain in their domain owners.

**E — memory:** The shared retriever re-reads candidates after asynchronous semantic scoring and omits
corrected/forgotten/scope/source-metadata changes instead of attaching an old embedding score to a new
fact. Query callbacks check the memory and linked source permissions. Recall carries actual execution
location; its host reads the current record and creates a content/version-bound hit. Immediately before
context insertion, dereference checks record version, status, expiry, scope/kind, sensitivity and current
permissions again. Parent DI wires the same canonical object policy into query and dereference. No
inferred memory is promoted; prior user files are untouched by forget/revocation.

**F — UX/privacy:** Existing compact chips, open-source links and stale-source recovery stay in place.
New selected-passage label and recovery messages have dedicated EN/RU/LV/PL fallback copy. The source
inspector can distinguish a selected passage from a whole source. Per-object `EXCLUDE_FROM_AI` always
denies; Private always denies remote; explicitly saved `LOCAL_ONLY` remains remote-denied after Full
Power selection. This precedence is required by existing Kids project Keep, which explicitly sets
LOCAL_ONLY, and by Full Power's disclosed-category consent contract. Unset object defaults retain
existing profile behavior. No provider permission or media consent is inferred from a mode switch.

## Deferred qualification sources

- `ContextBridgeSourceRegressionTest`: selected note range readback, stale-source non-broadening,
  summary retry across owner recreation with no local journey link, preservation of later Notebook
  edits, and explicit per-object Local only/Exclude/Private precedence.
- `ContextSourceReceiptsTest`: durable selected-anchor receipt readback after a source revision change.

Written only; **not run**. The issue's real cross-tool chains, process death, revocation during a slow
provider call, cancellation/replay, provider/network failures, three UI/typed/spoken routes and zero
outbound Private negative case require later qualification. Physical Ink/stylus checks require actual
supported hardware; no synthetic hardware result is claimed.

No build, test execution, ADB, download, commit, push or issue mutation was performed in this batch.
