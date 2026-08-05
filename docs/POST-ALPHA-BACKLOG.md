# POST-ALPHA BACKLOG

This document is the repository for every great idea, grand architectural vision, and advanced feature that we are intentionally deferring to focus on achieving a stable Alpha.

If you have a brilliant idea for Halo, document it here instead of implementing it. 

## Deferred Features & Subsystems

### 1. Advanced Cognition & Reasoning
- **Advanced Learning Mechanisms**: Engine algorithms for adjusting weights or rewriting internal heuristics based on long-term usage.
- **Executive Optimisation**: Global optimization algorithms across multiple active agents.
- **Autonomous Agents**: Self-directed agents that run in the background without explicit user invocation (e.g., scraping news, summarizing emails overnight).
- **Advanced Planning**: Multi-step hierarchical task networks, temporal planning, and contingency fallback plans.

### 2. Memory & Context
- **Long-Term Semantic Memory**: Vector databases and knowledge graphs that persist across application restarts.
- **Episodic Memory Retrieval**: Allowing the system to recall past interactions natively ("What did we talk about yesterday?").

### 3. Ecosystem & Platform
- **Plugin Marketplace**: A system for third-party developers to publish and install new capabilities natively into the `CapabilityRegistry`.
- **Desktop Runtime**: Porting the Halo Microkernel to run natively on macOS/Windows as a background daemon.
- **Robotics Integration**: Exposing the `ExecutionBus` and `PerceptionBus` to robotic host interfaces.
- **Extended Provider Ecosystem**: Integrating 10+ different LLMs natively; Alpha will focus on a stable subset (e.g., Ollama/OpenAI).

### 4. Advanced Perception
- **Continuous Vision Processing**: A camera stream that constantly feeds `Observation(Vision)` into the cognitive bus.
- **Passive Environmental Audio**: Constantly parsing ambient sounds for context.

---
*Note: This backlog is a sign of discipline. A focused Alpha runtime is a prerequisite for all of the above.*
