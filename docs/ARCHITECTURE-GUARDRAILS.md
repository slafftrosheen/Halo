# ARCHITECTURE GUARDRAILS

To ensure Halo reaches Alpha reliably, the following architectural guardrails are strictly enforced. All engineers and contributors must abide by these rules.

## 1. Feature Freeze
- **No new cognitive subsystems**: We are no longer adding architecture. The cognitive layer is frozen.
- **Defer grand ideas**: Ideas such as autonomous agents, robotics, plugin marketplaces, advanced learning, and executive optimization belong in the `POST-ALPHA-BACKLOG.md`. Do not implement them now.

## 2. Engineering Priorities
Every pull request must explicitly improve one of the following:
- **Stability**
- **Reliability**
- **Observability** (e.g., Diagnostics, Timelines)
- **Usability** (e.g., UI polish, interaction states)
- **Maintainability**
If a PR does not directly map to one of these, it will be rejected.

## 3. The Android Host Boundary
The phone is merely the **development host**, not the final product. 
- **No phone-first workflows**: Do not optimize Alpha around smartphone gestures, complex visual interactions, or features that assume a user is looking at a screen.
- **Wearable readiness**: Ask yourself: *Can this subsystem execute actions originating from smart glasses? Can it operate completely without the Android UI?* If not, the implementation is flawed.
- **Strict Separation**: Android-specific dependencies (`android.*`) must never leak into the cognition or runtime modules. They belong strictly in `host-android` and app-level layers.

## 4. The Singular Cognitive Pipeline
All interactions—whether passive wake-word, manual push-to-talk, or developer text injection—must converge into a single, unified cognitive pipeline:
`Observation -> Interpretation -> Reasoning -> Planning -> Execution -> Response`
- **Never fork reasoning**
- **Never bypass interpretation**
- **Everything starts as an `Observation`**

## 5. Architectural Quality
- **No Dead Code**: Remove unused files immediately.
- **No Placeholder Code**: Prohibit `TODO()`, `NotImplementedError()`, or `println()` statements. Use structured `logger.info`.
- **Downward Dependency**: `Android Host -> SDK -> Runtime -> Engines -> Libraries -> Ontology`. Never violate this directed acyclic graph.
- **Single Ownership**: Concepts (like `Goal`, `Plan`) have exactly one module owner. Do not duplicate domain models.
