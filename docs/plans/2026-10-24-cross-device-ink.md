# Cross-device Ink implementation (#30)

Human authorization: implement source now; keep activation gated. Original device,
spoken, compatibility and release acceptance remains required. Baseline 32f02942.

## Constraints and design

The existing DeviceSessionManager remains the device owner. Ink has independent
input, display and processing owners and carries context only, never tool intents.
No remote pen event is an approval. The production activation policy defaults to
closed; all association, start, receive and durable mutation paths check it.

Use an explicit trusted certificate association and standard TLS with client
authentication in a host adapter. Never repurpose BuddyBot's raw WebSocket or
wearable content transport as an authenticated pen channel. Runtime contracts are
portable and nonblocking. Network I/O belongs to hosts, with bounded frames,
deadlines, cancellable sockets and no cloud dependency. Secrets stay outside the
device registry. A transport peer must match the explicit saved association.

Versioned envelopes bind source, session nonce, interaction, board, document,
sequence, timestamp and expected revision. Preview state is bounded, replaceable
and expires on stop/disconnect; only complete checked checkpoints may persist.
Exact duplicate checkpoint retries return the original receipt. Conflicts fail
without overwriting local changes. Durable content and deduplication metadata
must commit in one canonical Canvas transaction, including process-death recovery.

## Execution sequence

1. Portable endpoint/envelope/codec, bounded session owner, activation/trust and
   revision/checkpoint contracts. Tests: malformed/version/size, source/session
   binding, disabled policy, duplication/reorder/rate/stale/conflict and stop.
2. Host TLS channel, explicit trust and durable checkpoint adapters; canonical
   DeviceSessionManager/Canvas integration. Test mutual authentication, framing,
   cancellation and process-death/idempotent Canvas readback.
3. Android pen capture and Devices controls, context binding and registered
   same-domain functional operations. Preserve local drawing when disconnected.
   Host adapters expose only measured platform capabilities. Windows/Linux
   compatibility stays unqualified without exact hardware evidence.
4. Complete regression sources before focused serial build/test. Review whole
   batch, repair findings, publish verified source to main. No repeated APK/model
   campaigns. Keep #30 open until physical acceptance is demonstrated.

## Shared interfaces / review focus

Envelope codec -> session: immutable validated snapshots, no unbounded allocation.
Session -> checkpoint store: exact atomic CAS and original retry receipt.
TLS host -> session: verified certificate fingerprint is host evidence; never a
wire-supplied identity assertion. Association -> Devices: trust is distinct from
connection and activation. Canvas -> context: revision/interaction references,
not an execution capability. Stop/gate revocation -> all: serialize before writes,
clear ephemeral state and close transport. No UI or assistant bypass.

Source implementation and follow-up review are complete. Verification is in
progress; the verification report records the actual build, regression and phone
results. Production activation and original physical acceptance remain gated.
