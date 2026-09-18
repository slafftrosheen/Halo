# Cognitive implementation map

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

The production phone lifecycle is [app HaloRuntime](../../app/src/main/java/com/halo/app/HaloRuntime.kt).
Read it with [ManualDependencyGraph](../../app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt).

[runtime/cognition](../../runtime/cognition) contains blackboard, working memory,
context, conversation, tools and session contracts/implementations. Interpretation,
reasoning, planning and execution also have separate engine modules.

CognitiveSession already provides streaming and bounded tool-call rounds. Its current
app wiring uses InMemoryConversationStore and echo/current-time tools.
Blackboard is not the sole owner of all application state: provider selection, speech,
conversation, device sessions and presentation have separate owners.

The older microkernel/ProcessManager path is retained but is not started in the phone
product loop. Old diagrams presenting it as the sole production owner are superseded.
[Provider architecture](../ai/AI_PROVIDER_ARCHITECTURE.md) separates inference from handoff.
