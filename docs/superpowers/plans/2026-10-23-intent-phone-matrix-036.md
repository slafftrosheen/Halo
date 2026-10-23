# Executable native advisory classification matrix — #36

Base main `ca4c0e9d298cd2316eae6fb24e489a1711c091fa`; app already installed and verified on f01fe673. The existing 48-row EN/RU/LV/PL matrix is a manual document; no executable physical classifier harness exists. Add test-only code/assets, using the production LocalIntentModelRouter and installed exact verified candidate. Reuse the current app APK; build only instrumentation after source/review completion.

- [x] Audit production advisory admission, existing installed-artifact guards and prepared matrix.
- [x] Materialize all 48 synthetic final-text cases in versioned test assets; validate unique IDs, language balance and expected decisions.
- [x] Implement bounded physical entry point: exact SHA/length and small generative candidate, idle/no-other-resident admission, isolated classifier preferences, first cold observation and all48 final-text cases through production advisory admission only. Record actual warmth/backend; no explicit warm load, backend override or unload. Export case IDs/labels/timing/backend only, no raw responses/prompts or user content.
- [x] Record missing advice separately from wrong labels; interrupted/incomplete runs remain NOT_TESTED. Never infer actual ASR, tool execution, delivery, provider effects, permissions, role qualification or audible quality from this advisory-only harness.
- [x] Review complete source and compile instrumentation only: first gate PASS1m10s, production Kotlin UP-TO-DATE. Retain test-source manifest/log/hash before publication.
- [ ] Install against the unchanged installed app and run the exact candidate. Phone disconnected before this step; NOT_RUN, no semantic/latency claim. Correct observed production defects in a subsequent complete source batch, not by weakening expected labels.

No downloads, persistent role/model selection, cloud calls or tool operations. Do not evict another resident model; failed prerequisites are not a semantic model failure. Residency/teardown remains with the production provider and isolated test process lifecycle. Source review caught a check-then-mutate race in proposed explicit warm-load/cleanup calls; removed those calls before any compilation/run. Original #36 remains open for full keyboard/voice/action/low-RAM acceptance.
