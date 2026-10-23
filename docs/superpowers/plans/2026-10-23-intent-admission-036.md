# Intent admission and stale advisory recovery #36

Base `d3f8b29d80cee2fd17f40fb812fe81bf3e9cf9b3`, freshly synchronized. Reuse LocalIntentModelRouter and LiteRtLocalProvider; no new classifier, inference engine, role selection, authorization or provider route.

- [x] Move nonblocking advisory admission/resident checks under the existing LiteRT load mutex, sharing the canonical generation body.
- [x] Revalidate explicit classifier settings and actual provider/model/tool-free reply identity before accepting a standalone label.
- [x] Author real provider/fake-runtime contention/residency/cancellation and router reply regressions, then source review before a gate.
- [x] Run focused provider/app compilation and regressions; publish truthful source evidence. Keep full physical multilingual/action-loop/Polish UI acceptance separate and OPEN.
