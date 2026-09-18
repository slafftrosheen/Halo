# Halo current state

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.
Static source review only; no fresh build, instrumented test or hardware run is claimed.

[ManualDependencyGraph](app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt) is
the process composition root. [App HaloRuntime](app/src/main/java/com/halo/app/HaloRuntime.kt)
starts the phone Engine/EventBus/Blackboard and speech components. Its source explicitly
excludes the older microkernel/ProcessManager path from normal phone startup.

| Area | Present in code | Limit |
|---|---|---|
| UI | Four primary tabs, device details, intelligence, voice, permissions, pairing | Console/lab/inspector are not AppShell routes; whole-app accessibility/performance not established |
| Orb | Canvas shapes, pixel ring, colors, glyphs and infinite motion | No measured audio/task-progress input or explicit reduced-motion control |
| Cognition | Engine pipeline and CognitiveSession streaming/tool rounds | General model-to-Android capability bridge is not wired |
| Model tools | Echo and current time registered in app graph | Not general phone/device actions |
| Providers | llama.cpp, Ollama, OpenRouter inference; Android assistant handoff; Sherpa and Android TTS speech | Support is adapter/model-specific; handoff is not inference |
| Speech input | HostGraph injects AndroidAudioSource | HeyCyanAudioSource existence does not prove glasses feed the phone speech loop |
| Memory | In-memory cognitive/conversation stores; separate Room Memory schema/DAO/repository | Room-backed CognitiveSession not established |
| Reminders | Exact-alarm adapter with simple duration parsing | Complete CRUD, robust ambiguity, permission/reboot recovery not established |
| Notifications/routines/privacy | Named modules/contracts | Operational summarizer, full automation engine and universal network firewall not established |
| Plugins | In-process registration/capability contribution/dispatch | No security sandbox or downloadable plugin installer established |
| Devices | Persisted identities/session handling, HeyCyan and BuddyBot paths | Hardware reliability and capability consistency need evidence |
| Credentials | OpenRouter key written through SharedPreferences | No application-level encryption in this storage path |
| CI | Workflow declares lint/test/build/upload | Current success not established; detekt/ktlint configuration mismatch |

See [architecture](docs/04-architecture/ARCHITECTURE.md),
[providers](docs/ai/AI_PROVIDER_ARCHITECTURE.md),
[persistence](docs/04-architecture/DATABASE.md),
[security](docs/12-security/THREAT_MODEL.md),
[HeyCyan](docs/protocols/heycyan/IMPLEMENTATION_STATUS.md) and
[audit ledger](docs/engineering/DOCUMENTATION-AUDIT.md).

Read [release readiness](RELEASE_READINESS.md) separately. Code presence is not measured
latency, power consumption, passing tests or physical acceptance.
