# Runtime contracts and lifecycle

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

See [architecture](../04-architecture/ARCHITECTURE.md) for production wiring.
The current Engine interface is in
[sdk/runtime-sdk/Engine.kt](../../sdk/runtime-sdk/src/main/java/com/halo/sdk/runtime/Engine.kt).
Separate Process and agent interfaces exist; do not substitute illustrative signatures.

The app starts the phone engine/speech path; the older microkernel path is retained but
not part of normal startup. Discovery and runtime device sessions have different callers/modes.

Buses, snapshots, schedulers and recovery contracts do not establish a durable workflow
scheduler, per-second attention scoring, lossless crash recovery, isolated plugins or
universal privacy firewall. The former specification mixed these proposals with real
infrastructure. Future work must state ownership, lifecycle, cancellation and evidence.
