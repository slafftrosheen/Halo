# Native speech admission cancellation #33

Base main `8739c2a6cd67de457f131253bdb7f8573444e9fc`. Existing DAG, native resource coordinator, model/native owners, voice routing and cancellation remain authoritative. No new speech engine or measured performance claim.

- [x] Cover the entire Supertonic acquire attempt with its existing non-cancellable native-before-resource cleanup.
- [x] Remove Kokoro cleanup dependence on an acquire-success flag; partial acquisition must use the same owner cleanup.
- [x] Author actual-provider, actual-resource-manager cancellation regressions before validation, with bounded fixture packs and coordinator admission blocked before native initialization.
- [x] Read-only source review found no concrete blocker; first focused app compile/test gate passed 37 cases in 5m42s, zero failures/errors/skips.
- [x] Publish verified source/results, keep physical latency, backend placement and spoken acceptance open.

Source audit confirms Supertonic acquired before its try/finally; Kokoro set a success flag only after suspended acquire returned. Both could leave generic compute.tts behind when cancellation happened while the coordinator mutex was occupied. Failed native teardown still prevents resource release, preserving existing ownership safety.
