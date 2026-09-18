# Feature requirements and implementation boundary

Status: CURRENT SOURCE MAP / PLANNED. Reviewed 2026-09-18.

| Feature | Current code | Remaining target |
|---|---|---|
| Personal memory | In-memory cognitive stores; separate Room Memory entity/DAO/repository | Integrate durable searchable/editable/deletable facts, provenance and export |
| Reminders | AndroidMemoryCapability exact-alarm scheduling with simple duration parser | Clarification, exact-alarm access handling, CRUD, reboot/timezone recovery |
| Automation | Named engine module/contracts | Persisted IF/THEN time/location/device triggers and auditable bounded execution |
| Notification summarization | host-android system NotificationListener is an empty class | Registered listener service, scoped permission, filtering, summary/delivery |
| Agentic tools | CognitiveSession loop and echo/time tools | Typed bridges to useful phone/device capabilities |
| Notebook | No current AppShell route or integrated workspace repository | Persistent notes, Inbox, conversations/files, memory management, explicit Working Set and local search per user blueprint |

Source maps: [architecture](../04-architecture/ARCHITECTURE.md),
[persistence](../04-architecture/DATABASE.md), [providers](../ai/AI_PROVIDER_ARCHITECTURE.md).
The feature list is not a statement that the desired flows are implemented.

Notebook scope: [user blueprint](../02-product/NOTEBOOK-WORKSPACE-BLUEPRINT.md) and
[delivery slices](../02-product/NOTEBOOK-DELIVERY.md). Canvas/projects/hybrid retrieval
are beta targets; basic workspace use must not require an AI provider.
