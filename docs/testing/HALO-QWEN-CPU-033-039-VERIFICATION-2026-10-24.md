# Installed Qwen CPU residency — #33 / #39

Physical phone `f01fe673`, 23124RA7EO / API35. Exact installed Qwen3 0.6B dynamic-int4 SHA256 `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`, 344671744 bytes. LiteRT-LM 0.18.0; explicit requested/effective CPU, context 2048. No downloads, saved selection/backend/role/tuning changes, GPU request or runtime recommendation promotion.

Installed app `83110b9e9313ff5748d96bc5670557f3e2555a2fcbeaf85f066faf8d561f07db`; test APK `38772c18a50d5897edf34629dfc022f1687ad3ae7762495014a737041c860b76`; sourceStamp includes main `3013a7ab5d33ea9a799f7e9558a0e9b4bc7694b0` and both APK identities. These packages predate the separate Canvas clear batch.

Run `a2f5ffd3f93a4db0b46784d874855c90` passed **3/3 strict sentinel turns** on one resident engine with separately closed conversations. Native instrumentation passed one case in 97.68s. Turn duration 8433 / 18609 / 6092ms; first-token latency 7538 / 5352 / 5290ms. This is a small control experiment, not a latency distribution or voice benchmark.

Sampled peak process PSS 1415455KiB; sampled peak native heap 675369872 bytes. Following successful engine teardown, process PSS 458790KiB and native allocated heap 20536928 bytes. Platform low-memory flag was false at recorded stages; GPU memory was NOT_VISIBLE. PSS/native-heap samples are not RSS, continuous peaks, utilization or energy measurements. Native heap reclamation does not establish all process pages returning to their original level.

[Content-free raw evidence](artifacts/HALO-QWEN-CPU-033-039-2026-10-24/) retains exact-run identity, sentinel match booleans, timing and memory stages. Historical append-only evidence must not be pooled with this run. Installed-artifact fingerprint verification is a prerequisite, not model quality evidence.

The separate English role suite, requested three repetitions, reached the **600s host deadline**: RUNNER_INTERRUPTED, process exit124; the wrapper correctly failed and force-stopped its test process. Exact run `c326f7b774d7433896bfcd30ac7bfc2a`, fixture suite2, same installed app/test/model identities. Host sourceStamp includes `3013a7ab...-dirty` because the uninstalled Canvas patch was present in the checkout; the installed app was still the preceding `83110...` package, not that patch.

Three additional FAST cases completed before interruption: `fast-add-0` passed (71581ms), `fast-copy-0` failed strict answer matching (77422ms), and `fast-first-0` failed matching (91490ms). `fast-count-0` started without a recorded finish. This partial case evidence is retained; the completed-run report correctly classifies every role NOT_TESTED with NO_COMPLETED_MATCHING_RUN. It must not be pooled into a complete three-repetition result or described as a qualified role/model failure. Selection evidence was not saved and no recommendation is promoted. These provider timings are not comparable benchmarks against the direct native sentinel probe: workloads and provider paths differ.

No role, language, intent accuracy, actual spoken action, serial-versus-concurrent voice latency, thermal/energy or accelerator qualification is claimed. #33 and #39 remain OPEN.
