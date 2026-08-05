# HPS-9000: Halo Engineering Manifesto

This document outlines the engineering culture, principles, and quality gates that separate Halo from a typical software project. Every pull request and architectural decision is judged against these principles.

## HPS-9010: The Ten Principles
1. **Runtime First:** No code directly depends on Android, specific devices, or external APIs. Everything depends on Runtime interfaces.
2. **Features are Workflows:** There are no "services" for specific tasks. Everything is a workflow: `Intent -> Workflow -> Capability -> Response`.
3. **Nothing Knows Too Much:** A Driver knows audio, not speech. A Provider knows reasoning, not reminders. Isolation is strict.
4. **Replaceability:** If a component cannot be replaced tomorrow without breaking the system, the architecture has failed.
5. **Inspectability:** Everything from latency to battery usage to network calls must be inspectable.
6. **Explainability:** Every action must be able to answer: Why? How? Using which provider? With what confidence?
7. **Local Before Fast:** Halo optimizes for Ownership over Latency. Local execution wins by default.
8. **Silence is Success:** The best interaction is the one that didn't unnecessarily interrupt the user.
9. **Users Own Their Intelligence:** Halo owns nothing. Everything is exportable and removable.
10. **Platform over Product:** Build the capability for others to build features, rather than hardcoding features.

## HPS-9020: Coding Constitution
Consistent lifecycle interfaces across the board:
* **Engine:** `start()`, `stop()`, `health`, `state`
* **Provider:** `metadata`, `execute()`, `cancel()`
* **Driver:** `capabilities`, `events`
* **Plugin:** `manifest`, `initialize()`

## HPS-9030: Engineering Metrics & Architecture Health
Halo tracks its own engineering quality. Every release publishes metrics on compilation time, APK size, battery usage, wake latency, cold boot, offline ratio, and privacy score. The Runtime continuously scores its own Architecture Health.

## HPS-9040: Quality Gates
Before a PR is merged, it must answer:
* Does this reduce coupling?
* Is it documented and testable?
* Can it be mocked?
* Does it leak Android or vendor SDKs?
* Is there a benchmark and an RFC?
* What is the privacy impact?

## HPS-9050: Architecture Decision Records (ADRs)
Every major decision is documented in an ADR (Problem, Decision, Alternatives, Tradeoffs, Consequences). Years later, the ADR explains the "why" so nobody has to argue.

## HPS-9060: The Halo Dictionary
Vocabulary is identity.
* *Not* Assistant App -> **Platform**
* *Not* History -> **Activity**
* *Not* Chat -> **Session**
* *Not* Extension -> **Capability Module**
* *Not* Settings -> **Command Center**

## HPS-9070: The Disappear Principle & Zero Dashboard
Every feature eventually disappears after it completes its task. There is no permanent clutter. Ultimately, the "Home screen" disappears entirely. Halo only appears when needed. The phone app is just for configuration, not usage—true ambient computing.
