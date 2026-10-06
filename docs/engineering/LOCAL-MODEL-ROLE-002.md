# LOCAL-MODEL-ROLE-002

Candidate expansion, durable role evidence and measured recommendations depend on landed ROLE-001 (2b4898d1, lifecycle corrections a295c54c).

## Artifact inspection

Both immutable artifacts were explicitly downloaded outside the repository on 2026-10-06. Full SHA-256 and byte counts matched the requested pins. Inspection used litert-lm-builder 0.17.1 and litert-lm-peek; TFLite input tensor inspection confirmed KV dimensions. Runtime stays at 0.17.1.

| Candidate | Revision | Bytes | SHA-256 | Context/KV |
|---|---|---:|---|---:|
| SmolLM2 135M | 749fc23bc8d7ef89db031d075004289c88f41c2f | 142819328 | ccdc5c85735743f081b7d44ca309cab569f76c0f2f0e8e163449a63721969c37 | 4096 |
| SmolLM2 360M | 507c99cfe6541ba2bcd84818786f7b025935e5e1 | 373719040 | 8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e | 8192 |

Both Apache-2.0 text artifacts contain generic tf_lite_prefill_decode sections. 135M uses format 1.5.0, HF tokenizer and stop token ID 2. 360M uses format 1.4.0, SentencePiece, start <|im_start|> and stop <|im_end|>. Both embed ChatML user/assistant templates and a system newline prefix. CPU is the conservative preference. Inspection does not prove Android execution or qualify GPU. Native tools, vision, audio and reasoning capabilities remain unclaimed; HALO_PROMPT is independently measurable.

## Evidence boundaries

Schema 25 adds content-free local_role_qualifications history. One completed physically validated suite writes one snapshot, including ordinary role failures. Canceled, busy, unavailable and artifact-changed runs are discarded. Latest unstable evidence excludes recommendations, as does critical memory pressure.

Exact current identity includes provider/model, full SHA, coarse device fingerprint, runtime/version, compatibility epoch, backend, context, threads, GPU layers and suite version. Identity changes require retesting; history is preserved. Suite version and compatibility epoch begin at 1. Material probe changes require a suite bump; recommendation compatibility changes require an epoch bump.

The pure SDK policy requires CHAT 3/3 plus three reliable warm samples for FAST; CHAT 3/3 for CHAT; CHAT and REASONING 3/3 for REASONING; complete measured tool continuation for AGENTIC. NATIVE and HALO_PROMPT remain distinct, with NATIVE preferred after eligibility gates. VISION projects verified Vision Lab evidence only.

One measured candidate is labelled Only measured candidate. Comparable alternatives use Best measured on this device. No universal score or backend averaging. Explicit Use revalidates physical identity and current eligibility, then invokes existing selectTarget or setRoleBinding authority. Browsing, downloading and qualification never apply suggestions.

Candidate, recommendation, assignment and residency are separate. Evaluation purpose is only a UI hint. Upstream benchmarks are not Halo measurements; family and parameter count are not competence.

## Staged external candidates

SmolLM3-3B, Phi-4-mini-reasoning and Qwen3-4B remain STAGED_EXTERNAL_CANDIDATE documentation only. Artifact/runtime compatibility, memory pressure and two-phone evidence are required before catalog admission. No acquisition path or defaults change is added.

## Physical acceptance

After merge, run the identical source-stamped build on the development phone and Samsung S22 Ultra. Start with Qwen 0.6B and explicitly acquired SmolLM2 135M/360M; add Qwen 1.7B and DeepSeek 1.5B if storage/time permit. Copy SHA prefix, backend/context/config, load time, cold TTFT, three warm samples/median, CHAT/REASONING counts, tool protocol/initial/continuation and bounded memory pressure.

Physical winners and following batch remain undecided. No automatic downloads, new engine owner, speculative prewarm, GPU admission changes, HeyCyan changes or production EWMA observations are introduced.


## Automated verification

The requested nine-task serial gate passed: 709 tests, zero failures/errors/skips, debug APK assembled. Coverage includes pure recommendation quality/identity/resource gates, explicit apply authority and stale refusal, pinned candidate integrity and canonicalization, Room 24-to-25 migration with benchmark preservation, complete failure persistence versus canceled/changed discard, and existing role-suite/health/configuration regressions. Physical phone qualification remains pending.
