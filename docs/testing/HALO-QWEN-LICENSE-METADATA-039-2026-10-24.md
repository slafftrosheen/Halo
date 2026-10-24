# Qwen publisher-declared license metadata — #39

Date: 2026-10-24. Base `30d9e5dfe3b084dcd0f389bc6e08d861a7b66177`, fetched 0/0 with a clean checkout before changes.

The pinned [conversion publisher README](https://huggingface.co/litert-community/Qwen3-0.6B/blob/a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76/README.md) declares `apache-2.0`, names `Qwen/Qwen3-0.6B` as its base and describes the relationship as quantized. Verified by a fresh unauthenticated HTTPS read of that immutable revision; the web reader could not open the page, so the raw publisher URL was read directly. The built-in Qwen entry previously left its existing license field unset despite the earlier source audit recording this declaration.

The catalog now records that declaration. This changes only one metadata field: no artifact URL, revision, SHA, byte size, context, backend, access, maturity, role or capability changes. Canonical built-in entries take precedence over same-SHA discovered/cache entries in `LiteRtModelManager`; this does not expose a duplicate candidate or promote it. Consumer projection still compares the full canonical entry; acquisition integrity and model qualification remain separate.

No new test mirrors a literal metadata constant. Existing catalog integrity/profile tests checked the surrounding identity/access invariants: **PASS**, exit 0, 54 seconds, 31 tasks (4 executed, 27 up-to-date), 21 cases (8 integrity, 13 catalog), zero failures/errors/skips. Exact changed-source hash matched after the gate; the source diff is the single license field. [Publisher observation, raw log, two JUnit XML files and source identity](artifacts/QWEN-LICENSE-METADATA-039-2026-10-24/) are retained. No APK assembly/install, inference, model download/campaign, actual typed/spoken/provider acceptance or CI/Actions. No new interactive operation or tool contract.

Keep #39 open. This is the conversion publisher's declared license, not a legal/distribution clearance or confirmation that required notices have been packaged. Whisper/Silero/Supertonic conversion/export provenance and full device/role/language/onboarding acceptance remain unresolved as recorded in the qualification matrix.
