# Study Workflow

## FEATURE-069 — source-grounded Study (2026-10-08)

Study now chooses/imports up to four Workspace sources, resolves block-aware notes and bounded UTF-8 text, and sends one explicitly active image/PDF page only to exact verified vision targets under Workspace/cloud privacy policy. PDF support is lazy page-image rendering; PDF text extraction and OCR are not installed. Learner level, support style, independent languages and explicit source-only/general policy persist with per-source versions. Citations follow actual S1–S4 answer markers; model feedback remains unverified. Practice generation produces bounded, source-located drafts requiring Keep, and review preserves original provenance. Material/settings live in a lesson sheet; topic-only lessons remain available.

See [implementation, limits and validation](../engineering/STUDY-SOURCES-002.md). Older source-only/design statements below are historical context.


Next implementation plan: [Notebook editor and Study companion, RFC 0002](../rfc/0002-notebook-editor-and-study.md).
[Reference mining](../engineering/NOTEBOOK-STUDY-MINING.md) records the source review.
As of 2026-09-28, the Study tool persists a durable session before reporting success and refuses
when no session store is available. Guided stages, attempts, feedback and practice items are stored
in Room (`data/study/`), with the teaching policy in `study/StudyUseCases.kt`. The loop is
implemented in source only: it is not device- or test-verified, and source extraction breadth (PDF
and OCR) is still open. The definition below remains the intended behavior.

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
