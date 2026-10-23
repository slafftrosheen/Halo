# Polish model-role qualification entry point — #39 / #36

Base main `c20de8278f857bd1586a6508046041452157bf34`, fetched0/0 clean. Phone absent. UI supports Polish, but LocalModelPhoneFixtures and the canonical host role runner reject PL. Complete the existing qualification workflow without implying any model supports Polish before it is measured.

- [x] Audit canonical Kotlin fixtures, native runner, host language allowlist, evidence version filter and suitability documentation.
- [x] Extend all15 FAST/CHAT/REASONING fixtures and history to Polish, preserving original EN/LV/RU prompt/expected-answer data. Explicit grammatical answer contracts prevent Polish inflection from causing arbitrary false negatives.
- [x] Version the extended fixture suite, update host acceptance/default languages and reject legacy or wrong-language evidence. Preserve EN-only production-store publication; PL remains language-scoped export evidence.
- [x] Author Polish fixture/scoring and host evidence/runner-language regressions before validation.
- [x] Run one focused source-complete gate: first run6m22s, five JVM cases/production/native-test compilation and host evidence regressions PASS; no APK assembly while the phone is absent.
- [x] Prepare validated source/evidence for direct main publication; keep real Polish inference, native-speaker quality, speech and hardware acceptance open.

No new UI/tool/provider/runtime owner, model download, role binding, stored inference setting or commerce/backend changes.
