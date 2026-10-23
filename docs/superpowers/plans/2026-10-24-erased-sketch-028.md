# Persist a fully erased existing sketch — #28

Base main `a71bbf42df51784dcb71549e5ae56b00d37b79cc`, fetched0/0 clean before this source batch. Read-only audit confirmed three independent nonempty-stroke gates prevented saving a fully erased existing sketch, despite the canonical Ink editor producing valid empty documents.

- [x] Allow valid empty edits through the existing sketch dialog, ViewModel and canonical transactional repository. Keep empty new-card prevention, corrupt-input rejection, exact-source checks, node identity and draft publication ownership.
- [x] Author actual final-stroke erasure/reopen, stale-source/draft retention, corrupt/empty-new rejection and recovered-empty dialog callback/cancel regressions before any build.
- [ ] Independent source review, then one focused desktop gate. Do not rebuild APKs for unchanged native pointer tests.
- [ ] Publish source/evidence to main. Keep physical pen/palm/tablet/large-session acceptance open.

Human drawing/erasure remains an existing local gesture operation; no new assistant tool, permission, provider or storage owner. Existing assistant text-edit tools explicitly exclude SKETCH nodes; this correction does not silently expand their authority.
