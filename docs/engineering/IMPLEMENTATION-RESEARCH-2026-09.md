# Implementation and design research

Status: RESEARCH / PROPOSALS. Sources consulted 2026-09-18.
This is a selected live GitHub/web review, not an exhaustive survey or an implemented dependency list.
Default-branch pages can change; pin commit/release and review license before copying code.
The user's [mockup](../03-design/HALO-WORKSPACE-DESIGN.md) remains the design reference.

## References and concrete applications

| Primary source | Observed pattern | Proposed application to Halo | Validation / limits |
|---|---|---|---|
| [Now in Android architecture](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md) | Reactive state and unidirectional data flow with clear data/UI responsibilities | Explicit state ownership, route-level state and reusable content composables; simplify competing presence projections | Adopt useful patterns within existing runtime boundaries, not its entire module layout |
| [Now in Android modularization](https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md) | Feature/core boundaries and a design-system catalog | A Halo component/state gallery covering both themes, device states and the presence renderer | Add modules only when isolation/reuse justifies build cost |
| [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) and [skills documentation](https://github.com/google-ai-edge/gallery/blob/main/skills/README.md) | On-device model experiences and agent-skill concepts | Study local model setup, availability and constrained action presentation | Example capability claims are upstream claims, not evidence Halo runs those models or skills; assess ABI/device/model/license costs |
| [Stream Android AI components](https://github.com/GetStream/stream-chat-android-ai) | Compose components for streaming text, Markdown and AI chat state | Compare streaming answer stability, message/action card behavior and speech-input states | Evaluate patterns before adding SDK dependency; retain Halo's provider-neutral runtime |
| [LiveKit turn handling](https://docs.livekit.io/agents/logic/turns/) and [agent activity source](https://github.com/livekit/agents/blob/main/livekit-agents/livekit/agents/voice/agent_activity.py) | Separate endpointing, interruption and speech scheduling concerns | Distinguish stop speaking, cancel task and stop recording; test echo/false interruption and interrupted-turn context | Upstream server/Python code is a behavioral reference, not a drop-in Android/offline implementation |
| [Haze](https://github.com/chrisbanes/haze) | Compose background-blur library | Prototype subtle glass depth in one shared surface | Benchmark phone GPU/frame/power cost; verify release compatibility/license and retain an opaque fallback |
| [Android AGSL](https://developer.android.com/develop/ui/views/graphics/agsl) | RuntimeShader support on Android 13+ | Optional cell lighting/refraction/halo prototype | Halo minSdk is 26; Canvas fallback is required; API availability does not prove performance |
| [Google expressive-design research](https://design.google/library/expressive-material-design-google-research) | Research-driven use of shape, hierarchy and expressive emphasis | Make one clear primary action and recognizable state changes within the user's pastel/cell direction | Do not copy a Material skin or assume Google's findings validate Halo without usability testing |
| [Adaptive Compose guidance](https://developer.android.com/develop/ui/compose/build-adaptive-apps) | Layouts adapting to available space | Notebook list/detail and device detail layouts beyond narrow phone mockups | Preserve keyboard, navigation and 200% text behavior |
| [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) and [coroutine exception handling](https://kotlinlang.org/docs/exception-handling.html) | Consistent naming/layout and explicit coroutine failure/cancellation semantics | Small named functions, explicit lifetimes, cancellation propagation, typed outcomes and focused tests | Existing code has exceptions; treat this as proposed cleanup criteria, not current compliance |
| [OpenRouter tool calling](https://openrouter.ai/docs/guides/features/tool-calling) / [Ollama tool calling](https://docs.ollama.com/capabilities/tool-calling) | Provider-specific tool messages and result feedback | Add wire-contract tests and real capability bridges to the existing session loop | Do not advertise tools merely because upstream supports them; Halo Ollama currently advertises text/streaming only |
| [Android voice service](https://developer.android.com/reference/android/service/voice/VoiceInteractionService) / [AppFunctions](https://developer.android.com/ai/appfunctions) | Platform assistant lifecycle and authorized app-function entry points | Assistant-role spike and capability-specific interoperability | Check caller eligibility, feature availability and permissions on actual devices; no universal OEM-assistant parity claim |

## Candidate direction: a presence that explains work

Original proposal for Halo, inferred from the user's reference:
the cell cluster should be a legible summary of attention and activity, while nearby
action cards provide facts and control. The same cell/category vocabulary can connect
Home, Activity and Notebook without filling every screen with animation.

A useful differentiator is continuity: capture a thought, keep its provenance, turn it
into an explicit task, show its actual result, and find it later in Notebook.
Visual identity and dependable memory/actions should reinforce each other.

## Bounded experiments

1. **Presence renderer:** same deterministic states in layered Canvas and optional AGSL.
   Compare screenshot fidelity, frame timing, thermal/power behavior and reduced motion.
   Prefer the simpler implementation unless measured benefit justifies complexity.
2. **Two-theme catalog:** Home composer, quick-action tiles, timeline card, notebook note,
   device row, approval sheet and presence at multiple sizes. Compare to user reference,
   validate contrast/font scaling before migrating screens.
3. **Task receipts:** one Android action through tool -> authorization -> capability ->
   outcome -> Activity card. Inject timeout/cancel/duplicate calls and uncertain handoff.
4. **Voice interruption:** measure turn latency and false barge-in; preserve spoken versus
   unspoken answer state. Separate playback stop from action cancellation.
5. **Notebook continuity:** durable Quick Note -> search/edit/delete -> contextual retrieval.
   Add files/Canvas/project boards only with their own working contracts.

These are proposed experiments, not work performed during this audit.

## Implementation/style policy to carry forward

Use existing entry points, exact interfaces and local conventions first. Prefer small
reviewable changes, explicit data ownership and structured concurrency. Avoid adding an
agent framework just to obtain a loop already present. Keep presentation math out of
runtime logic and platform actions out of composables/providers.

For each adopted external pattern record source URL, inspected revision, license, Android
API/dependency compatibility, intended benefit and acceptance measurement. Recheck upstream
at implementation time. No external code/library was installed or copied in this docs task.
