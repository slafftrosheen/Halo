# Text, Study, and Workspace request routing

The user reported Gemma answering voice requests while typed, Study, and Workspace requests failed with HTTP 429. A matched live Gemma voice/text pair has not yet been captured, so an account quota or upstream cause has not been established.

## Observed source and device evidence

- Home published a keyboard observation to interpretation and separately submitted the same input directly with optional tools. The device trace showed interpretation choosing direct response, but the outgoing text request included one tool and OpenRouter's `require_parameters` endpoint filter.
- Voice uses the interpreted route and streaming provider transport. Study and Notebook assistance already request no tools, but the shared standalone router used non-streaming generation.

## Changes

- Ordinary Home text now enters interpretation once. The existing intent owner chooses conversation, native action, or agentic execution; Home no longer races it with a second provider submission.
- Host conversation identity and operating mode survive interpretation. Explicit image requests retain their single direct, tool-free submission and existing attachment lifetime handling.
- Tool-free remote standalone requests use the provider's advertised streaming transport. This covers Study and Notebook assistance without changing model selection, output budgets, source permissions, or review/save ownership. Local and tool-enabled requests keep their existing execution path.
- Standalone streaming requires a validated terminal response. Failure after any text/tool output, including an invalid completion, cannot trigger fallback. Real provider 429 responses remain failures.

## Acceptance

The serial gate covers 740 tests: AI routing 79, interpretation 12, cognition 243, and Android app 406; no failures, errors, or skips. The final command is `:engines:ai:test :engines:interpretation:test :runtime:cognition:test :app:testDebugUnitTest :app:assembleDebug`, with one worker, no parallel execution, no persistent daemon, and a 2 GB heap.

The debug APK built successfully and installed with `adb install -r` on the connected Samsung phone on 2026-10-09. MainActivity launched and the app process was present. APK SHA-256: `a2630f1150ec99c405b7c1392ca7bd933c15495b8791fc2ef20f1ff7b73e3468`.

Live Gemma success for typed Home, Study, and Workspace remains pending the user's retry on the updated phone app. Transport parity is a repair candidate for the reported cross-surface failure, not proof of the upstream 429 cause.
