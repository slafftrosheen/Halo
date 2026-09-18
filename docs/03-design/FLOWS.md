> Status: SUPERSEDED DESIGN DRAFT. Classified 2026-09-18.
>
> Legacy design intent, not implemented behavior. The user-provided 2026-09-18 workspace mockup supersedes conflicting dark-only, four-tab and old orb directions. Examples here are illustrative, not a compiled component API.
>
> [Current reference](HALO-WORKSPACE-DESIGN.md).

# UX Flows

Every interaction in Halo is mapped as a deterministic flow.

## 1. Reminder Flow
```text
Hold Button
       ↓
Speak ("Remind me to call Alex at 4")
       ↓
Speech Recognition
       ↓
Intent Engine
       ↓
Extract Time (16:00) & Entity (Call Alex)
       ↓
Store Reminder (Memory Engine)
       ↓
Generate Confirmation
       ↓
Speak Result ("Reminder set for 4 PM")
       ↓
Idle
```

## 2. Translation Flow
```text
Hold Button
       ↓
Speak ("Translate: Where is the train station?")
       ↓
Speech Recognition
       ↓
Translation Plugin / Provider
       ↓
Speak Result ("¿Dónde está la estación de tren?")
       ↓
Done
```

## 3. Ask Question Flow
```text
Wake Word ("Hey Brain")
       ↓
Speech Recognition
       ↓
Intent Engine
       ↓
Route Command
       ↓
Local LLM? (If offline or capable)
       ↓
Cloud? (If complex and permitted)
       ↓
Answer Generation
       ↓
TTS
       ↓
Done
```
