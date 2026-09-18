> Status: SUPERSEDED DESIGN DRAFT. Classified 2026-09-18.
>
> Legacy design intent, not implemented behavior. The user-provided 2026-09-18 workspace mockup supersedes conflicting dark-only, four-tab and old orb directions. Examples here are illustrative, not a compiled component API.
>
> [Current reference](HALO-WORKSPACE-DESIGN.md).

# Halo Design Language (HDL)

Every major platform has a defining design language (Apple's HIG, Google's Material). Halo uses HDL. 
HDL governs not just UI, but interaction, behavior, audio, voice, motion, privacy, and vocabulary.

## Ten Design Laws
1. **Every interaction must complete within 3 user actions.** Maximum: Open -> Tap -> Done.
2. **Never ask something the system can infer.** If the assistant knows the connected microphone, don't ask.
3. **No dead screens.** Every screen answers one question (e.g., Home: "What is happening?").
4. **No decorative UI.** Every pixel must communicate State or Action.
5. **No dialogs unless destructive.** Use sheets, inline editing, and expandable rows instead.
6. **No feature starts hidden.** Power users love hidden menus; everyone else hates them. Keep it flat.
7. **Every action is reversible.** Delete memory? Undo. Remove plugin? Undo.
8. **No loading spinners.** Always communicate progress (e.g., `Connecting... ██████░░░`).
9. **Everything is searchable.** Settings, Plugins, Devices, Logs, Everything.
10. **The interface never explains AI.** It explains outcomes. Not "LLM generating response...", instead "Thinking...".

## Halo Sound Language
Every sound means something. No science fiction, no Alexa noises. Think Leica camera.
* **Listening**: Small rising tone.
* **Thinking**: One soft click.
* **Done**: Short confirmation.
* **Error**: Low double tone.
* **Wake word**: Tiny pulse.
* **Disconnected**: Descending tone.

## Halo Haptics
Wearables have buttons and vibration. Define a strict vocabulary:
* `•`: Success
* `••`: Notification
* `•••`: Urgent
* `• • •`: Disconnected

## Halo Motion Language
Animations always communicate physics (weight, momentum, inertia). Nothing bounces, spins, or wiggles.
* **Pages**: Slide.
* **Sheets**: Rise.
* **Dialogs**: Fade.
* **Cards**: Expand.

## Halo Terminology
Never invent synonyms. Use a strict vocabulary consistently across the entire platform.

| Internal Term | Meaning |
|---|---|
| **Session** | One assistant interaction |
| **Memory** | Persistent user-approved information |
| **Activity** | Completed actions |
| **Workflow** | Execution pipeline |
| **Skill** | User capability |
| **Provider** | AI backend |
| **Driver** | Hardware adapter |
| **Engine** | Runtime service |
| **Runtime** | Halo kernel |
| **Context** | Live environment snapshot |

## Philosophy of AI
The assistant never says "I think...". 
Instead: "Based on your calendar...", "According to GitHub...", "Using local memory...".
Source first. Opinion last.

## Halo Trust Scale
Every answer has a trust scale. People trust systems that admit uncertainty.
* Verified
* Likely
* Generated
* Unknown

## Halo Capability Model
Instead of standalone "features", everything is a capability: Speech, Translation, Vision, Memory, Scheduling, Automation, Search, Reasoning, Recognition. Plugins, Devices, and Providers all supply capabilities through the same abstraction.

## Runtime & Boot Language
* **Runtime**: Logs should be readable. (`Voice Engine: Ready`, `Provider: Connected`, `Plugin: Loaded`).
* **Boot Screen**: No splash screen. Ever. Open immediately to `Halo | Runtime Ready | Connected | HeyCyan G1`.

## Privacy & Debug UI
* **Privacy Language**: No legalese. (`Microphone: Currently Active | Reason: Wake word detection`).
* **Debug UI**: Aircraft systems inspiration. Operational status only. (`SYSTEM: ██████████ Healthy | Voice: Ready | Latency: 112 ms`).

## Halo's Secret Weapon: Inspectability
Everything is inspectable. Every screen, action, and AI output has an "Inspect" option showing exactly what happened. 
Example for an AI answer:
* **Provider**: Qwen 3 8B
* **Execution**: Local
* **Context**: 3 memories
* **Network**: No
* **Latency**: 1.2 s

Halo produces evidence, not magic. If someone asks, "Why did the assistant do that?", it should be reproducible and understandable.
