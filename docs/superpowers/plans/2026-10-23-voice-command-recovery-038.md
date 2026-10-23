# Voice Mode stale command recovery #38

Base main `6628274679ed74b0933fd2cb33a3ea9f5846eaae`, freshly fetched. Existing HomeViewModel, CognitiveSession, speech engine, playback and foreground/lifecycle owners remain authoritative. No new microphone session, provider route, permission, automatic cloud use or model qualification.

- [x] Audit queued microphone start/stop/mute/automatic continuation and replay against conversation/session changes.
- [x] Add generation fencing to the existing host command serialization, with final state/permission checks after waits. End/mute/conversation changes invalidate older starts; automatic continuation respects mute.
- [x] Preserve exact replay conversation and source identity across asynchronous waits; prevent old failures from stopping a newer session.
- [x] Author focused scheduler/interference/cancellation regressions and obtain source review before any gate.
- [x] Run a focused compilation/test gate, publish code and record physical 10-turn/echo/interruption/provider acceptance separately. Assemble/install when a device is available; no repeated packaging for test-only edits.
