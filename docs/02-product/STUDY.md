# Study Workflow

Next implementation plan: [Notebook editor and Study companion, RFC 0002](../rfc/0002-notebook-editor-and-study.md).
[Reference mining](../engineering/NOTEBOOK-STUDY-MINING.md) records the source review.
As of 2026-09-28, the existing Study tool constructs a temporary session and returns success text;
it does not persist or execute a tutoring workflow. The definition below remains intended behavior.

Status: DESIGN TARGET. Reviewed 2026-09-24.

**Study** is a guided-learning workflow that utilizes the Notebook and the Halo Runtime. It is a demonstration of Halo's generalized context and workspace architecture, not a separate standalone product or application.

## Core Definition
- **Notebook**: The persistent cognitive workspace.
- **Study**: A workflow built on top of the Notebook.

## Workflow Mechanics

The Study flow may consume context from any source:
- Camera
- Screen
- PDF / Files
- Images
- Notes
- Voice
- Web / Source material

It must not care where the source originated.

### Core Policy

The system should guide learning through a structured progression:

`ASK → HINT → EXPLAIN → ANALOGY → CHECK ATTEMPT → PARTIAL SOLUTION → SOLUTION`

### Language Preservation

The workflow respects linguistic differences:
`assignment language != explanation language != learner's strongest language`

Study proves the utility of a persistent, multimodal cognitive workspace.
