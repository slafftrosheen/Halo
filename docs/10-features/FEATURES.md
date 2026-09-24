# Feature requirements and implementation boundary

Status: CURRENT SOURCE MAP / PLANNED. Reviewed 2026-09-24.

| Feature | Current code | Remaining target |
|---|---|---|
| Notebook | Implemented AppShell route (NB-01 notes/search/lifecycle flow). | Expand to full inbox, conversation, and files integration; implement explicit Working Set and robust local search. |
| Personal memory | Wired in-memory cognitive stores; declared Room Memory entity/DAO. | Reconcile in-memory vs Room architectures. Implement durable, searchable, editable, and exportable facts with provenance. |
| Reminders | Wired `AndroidMemoryCapability` exact-alarm scheduling with simple duration parser. | Handle scheduling permissions gracefully, implement CRUD, reboot/timezone recovery, and complex ambiguity resolution. |
| Automation | Declared named engine module/contracts. | Target: Persisted IF/THEN time/location/device triggers and auditable bounded execution. |
| Notification summarization | Declared empty `NotificationListener` class in `host-android`. | Target: Registered listener service, scoped permission, intelligent filtering, and contextual summary delivery. |
| Agentic tools | Wired `CognitiveSession` loop; implemented basic echo/time tools. | Target: Typed bridges to complex phone/device capabilities beyond basic utilities. |

*Definitions:*
- **Implemented**: Type exists and has a reachable execution path in the current composition.
- **Wired**: Configured in DI or system startup, but might have incomplete capability.
- **Declared**: Type/interface exists but is not wired or reachable.
- **Tested**: Covered by automated tests.
- **Hardware-verified**: Proven on physical devices.
- **Target**: Intended future behavior.
