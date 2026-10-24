# #33 Auto backend correction

Base ec8f5150f, fetched main0/0 clean. The preceding warm-intent fixture on the phone stopped at CPU_CONFIGURATION before native initialization. Existing AUTO selected GPU when the per-artifact/runtime/device stability verdict was QUALIFIED. That verdict says nothing about paired CPU/GPU speed.

## Source and regressions

The existing qualification policy now leaves AUTO on CPU even for a saved QUALIFIED record and reports comparative performance unproven. Qualification remains intact. No setting migration or new evidence store. Explicit developer CPU/GPU choices retain their existing behavior. Catalog GPU requests retain prior stability eligibility because those preferences can express artifact compatibility; the existing mobile RAM gate and native artifact preflight remain unchanged. This does not claim CPU is faster, complete adaptive selection, or physical GPU placement. Existing workload evidence/ranking keeps its own owner.

New regressions recreate a durable QUALIFIED record and apply the actual eight-GB mobile gate, checking AUTO remains CPU without deleting qualification. All qualification states cover AUTO, explicit CPU and manual GPU. Existing catalog qualification and RAM tests cover the compatibility exception. Production and regression sources precede review and focused verification.

## Validation

Final read-only source review found no actionable findings. Single serial gate65492 **PASS6m45s** (488 tasks,23 executed/465 up-to-date): seven qualification tests plus three mobile-policy tests, zero failures/errors/skips; both APKs assembled. Two recorded source hashes still match. No repeated unrelated source/storage/embedding/speech tests; no CI/Actions.

Both APKs installed on f01fe673 /23124RA7EO /API35 and remote SHA readbacks matched: app `85e9040071ec5dcce4ec4e47dd752218b81f99fec5e6c7838286172f5d267d83`,288504360 bytes; test `fa12e204f8ed5d0ccc6dc25caf2ab22530a5981dfc9d95594aa7576d5b746e2e`,1963640 bytes. Selected-ID inventory passed one case54.466s with exactly the requested Qwen artifact. No role/backend/context/privacy preferences or downloads changed. Ordinary native provider journals remain owned by the existing provider.

Run `5b41fc71d9ea477f9200a522d3564d12` confirmed actual CPU/context4096 and exact SHA `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856` (344671744 bytes), LiteRT0.18.0. Explicit warm initialization3751ms. This replaces the former CPU_CONFIGURATION prerequisite with actual native execution on current configuration; it does not compare CPU/GPU speed.

**Warm intent qualification FAILED:** all48 prepared EN/RU/LV/PL calls started warm, all48 returned NO_ADVICE, zero matched labels, zero within2000ms. Observed upper-middle median2013ms/p95 nearest-rank2020ms include cancellation/cleanup. No completed generation diagnostics were recorded, so label accuracy remains NOT_TESTED, rather than zero semantic accuracy. This setup does not qualify the model for the existing intent deadline; no recommendation/selection evidence was saved. The fixture completed one JUnit case with one assertion failure in152.547s; host classifies INTENT_NOT_QUALIFIED and the outer helper exits1. It was not a host timeout or process crash.

Footprint sampled peak PSS1958583KiB/native1191708576 bytes at200ms, not exact RSS/GPU/energy. Shared provider retained native ownership; no fixture unload or teardown qualification. MainActivity resumed afterward. Actual speech/tool actions were not run. [Raw evidence](artifacts/HALO-AUTO-BACKEND-033-2026-10-24) includes failed native output and completed rows, filtered run footprints, installed APK identities, source hashes, desktop XMLs and terminal build log. Historical device-wide journals are excluded.

## Remaining acceptance

#33 remains open for matching paired benchmarks, baseline/concurrent real voice latency, thermal/energy/operation placement and complete safety acceptance. #36 warm prepared-string classification is independent of actual spoken recognition and tool authorization. Retain failed or partial native results without promotion.
