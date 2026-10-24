# #30: [Roadmap][Halo Ink 3/3] Cross-device Ink — tablet/desktop pen input into Halo sessions

Original: https://github.com/slafftrosheen/Halo/issues/30 · updated 2026-10-13T12:16:15Z. Full source is retained in source-issues.json.

No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.

### I30-P01: Pair and draw

First verify the production activation gate stays closed. In the explicit qualification host configuration, exchange public device files directly between an Android tablet and a second host; confirm trust locally on both. Start one exact saved sketch through the approved current interaction, export/open its connection file, draw pressure strokes and erase, and read back the remote editable sketch.

**Pass evidence:** No pairing/receive/write path bypasses the gate. Mutual TLS authenticates exact approved certificates; input/display/processing owners are distinct; strokes converge once and remain editable. A fixture loopback pass is not two-device acceptance.

Status: NOT_TESTED. Evidence: pending.

### I30-P02: Transport abuse

Use the host test harness to repeat/reorder/drop complete checkpoints and previews, send oversized/future/stale/source/session/interaction/document mismatches, flood previews, and use an untrusted or replaced certificate. Cancel while another checkpoint holds the owner lock.

**Pass evidence:** No unauthenticated injection or tool invocation; bounded frames/rate/queues; duplicate gets the original receipt; stale/conflicting content is rejected; incomplete preview expires; cancellation closes sockets and removes session/context.

Status: NOT_TESTED. Evidence: pending.

### I30-P03: Recover/conflict

Disconnect during a contact and after commit before receipt; sleep/wake and restart both hosts. Keep editing locally, explicitly resume using a fresh session, create simultaneous local editor/recovery edits, and repeat identical IDs under two trusted hosts.

**Pass evidence:** Local completed edits remain recoverable; no automatic external-action replay or duplicate stroke; explicit revision conflict instead of overwrite; recovery is scoped to authenticated host; revoked/expired grants cannot resume silently.

Status: NOT_TESTED. Evidence: pending.

### I30-P04: Voice and lifecycle

Select on one endpoint and speak on another in the same authorized interaction. Retry in a different interaction, after edit, after Stop/forget, after background and after process death. Inspect Devices capabilities and use accessible Stop.

**Pass evidence:** Only the exact current authorized session/region resolves; pen input never grants app/tool/cloud permission. Connected is separate from started/saved; local drawing remains available after disconnect.

Status: NOT_TESTED. Evidence: pending.

### I30-P05: Platforms/performance

Build each native host adapter on its actual target. Test one exact Windows pen and one explicitly selected Linux evdev pen, recording driver/architecture. Investigate each Huion/OpenTabletDriver/Pi configuration independently. Measure end-to-end pen-to-preview/commit latency, memory/queue ceiling, loss/duplication, 30-minute thermal and battery use on tablet + second host.

**Pass evidence:** Compatibility records distinguish SOURCE_ONLY/NOT_TESTED/PASS/FAIL/UNSUPPORTED. Preserve supported pressure/eraser; never claim Windows/Linux/Huion/Pi hardware support from Android, source review or loopback tests. Attach actual measurements and predeclared budgets.

Status: NOT_TESTED. Evidence: pending.

## Requirement-by-requirement review

- [ ] **I30-R01** (original line 9, Supported concepts / target journeys): - Android tablet runs Halo Ink UI while a separate HaloLinux/Pi or desktop node owns reasoning and tools.
- [ ] **I30-R02** (original line 10, Supported concepts / target journeys): - USB pen tablet on Windows/Linux enters ink through a native host adapter; Halo receives portable ink events rather than raw OS HID packets.
- [ ] **I30-R03** (original line 11, Supported concepts / target journeys): - User sketches or annotates on tablet while listening/speaking through HeyCyan/headphones; a single cognitive turn correlates *voice intent*, *selected region* and *session identity*.
- [ ] **I30-R04** (original line 12, Supported concepts / target journeys): - Connectivity interruption preserves local strokes; user can explicitly resume/sync without replaying external actions.
- [ ] **I30-R05** (original line 15, Scope): - [ ] Define `InkEndpoint` and `InkSession` capabilities in the existing Runtime/SDK device registry; identify input, display and processing owners separately.
- [ ] **I30-R06** (original line 16, Scope): - [ ] Design a versioned `InkEventEnvelope` (source device, session/interaction ID, document/board ID, stroke IDs, sequence number, timestamps, revision, bounded payload). Do not pass Android `MotionEvent` across host boundaries.
- [ ] **I30-R07** (original line 17, Scope): - [ ] Implement secure opt-in discovery/pairing or explicit trusted-endpoint association through the existing DeviceSessionManager; prevent unauthenticated LAN/USB peers from injecting events.
- [ ] **I30-R08** (original line 18, Scope): - [ ] Establish encrypted transport/handshake compatible with local-first operation; no required vendor cloud or central backend. Reuse an existing Halo transport if it meets safety requirements rather than creating an independent socket protocol.
- [ ] **I30-R09** (original line 19, Scope): - [ ] Handle input jitter, pen-up/down order, dropped/repeated events, rate-limits and flow control. Separate ephemeral live preview from durable committed strokes.
- [ ] **I30-R10** (original line 20, Scope): - [ ] Add idempotent synchronization/checkpoint and explicit conflict/revision handling. Reconnection and process death **must not duplicate strokes or authorize any tool invocation**.
- [ ] **I30-R11** (original line 21, Scope): - [ ] Expose per-device input capabilities (pressure, tilt, eraser, hover, display geometry), observed status and connection lifecycle on the Devices page.
- [ ] **I30-R12** (original line 22, Scope): - [ ] Support Android stylus first, then Windows/Linux pen/HID adapters. Investigate OpenTabletDriver and Huion compatibility per exact model/OS/architecture; Pi 5 ARM64 compatibility is a test hypothesis, not a promise.
- [ ] **I30-R13** (original line 23, Scope): - [ ] Keep Ink-as-context and voice route bindings through the current CognitiveSession / Interaction ID boundaries; never infer permission to open unrelated apps from a pen event.
- [ ] **I30-R14** (original line 24, Scope): - [ ] Add explicit remote-session start/stop, session ownership, privacy/security diagnostics, accessible disconnect UI and deterministic fallback to local drawing.
- [ ] **I30-R15** (original line 27, Acceptance criteria): - A paired Android tablet can draw into a remote Halo Canvas session; gestures and stroke data converge exactly once and remain editable across reconnection.
- [ ] **I30-R16** (original line 28, Acceptance criteria): - Pressure/eraser values retain the correct meaning where the source hardware exposes them; unsupported hardware falls back gracefully.
- [ ] **I30-R17** (original line 29, Acceptance criteria): - A voice command from one endpoint can refer to a region selected on another endpoint **only** when both belong to the same authorized interaction/session.
- [ ] **I30-R18** (original line 30, Acceptance criteria): - Unauthorized/replayed/stale events are rejected; server-side access checks apply despite UI approval state.
- [ ] **I30-R19** (original line 31, Acceptance criteria): - Disconnect, sleep, wake, process death, host restart and simultaneous edits have explicit observable outcomes and no silent data loss or replayed tool actions.
- [ ] **I30-R20** (original line 32, Acceptance criteria): - Benchmarks and tests cover latency budget, sequencing, memory quotas, packet loss/duplication, authentication, sync conflict and battery use; run on a real Android tablet + at least one second host.
- [ ] **I30-R21** (original line 33, Acceptance criteria): - Android/Windows/Linux platform capabilities have explicit compatibility records. Don't call Huion, Windows or Pi support complete without physical validation.
