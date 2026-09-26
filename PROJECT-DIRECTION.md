# Halo project direction

Status: DESIGN TARGET. Updated 2026-09-18.

Halo aims to be a portable cognitive runtime for wearable computing. Android is the
current app host. Local processing, interchangeable providers, device abstractions,
inspectable actions and the pixel/glass identity guide development.

## Truth and intent

Current source and its wiring determine what exists. [CURRENT-STATE](CURRENT-STATE.md)
maps that implementation. Tests and physical evidence have distinct verification scopes.
The [constitution](.agents/AGENTS.md) records engineering targets, not universal conformance.
Specifications describe desired changes; historical reports do not override code.

References to missing ARCHITECTURE-GUARDRAILS files have been removed. No unseen guardrail
document or old completion table should be treated as evidence.

## Requested direction

1. Extend the pixel orb with a halo, effects and richer real-state behavior.
2. Apply a coherent interface language to every page.
3. Connect dependable phone/wearable assistant actions to the existing runtime.
4. Check current upstream docs and implementations, then close documented gaps.

See [next phase](docs/14-roadmap/NEXT-PHASE-2027.md). External inspiration remains a
proposal until implemented and validated.

## Visual direction — Halo Signal Face, 2026-09-26

RUNTIME/UI-024 supersedes the 2026-09-18 spatial-cell presence direction.

Halo Presence is a circular semantic display:
- Outer Halo: Communicates state and activity through restrained colour, luminance and semantic motion.
- Inner Pixel Display: Communicates meaning through a large, immediately recognizable monochrome pictogram.

The visual language intentionally avoids anthropomorphic AI eyes, abstract morphing blobs, particle clouds, and decorative radial gauges. The same signal-face vocabulary is designed to scale across Android, wearables, widgets, and physical embodiments.

Home, Activity, Notebook, Devices and Settings are current primary destinations. Notebook has
an initial NB-01 persistent notes flow; its memory, Working Set, files, conversations and Canvas
surfaces remain planned.
Console, Protocol
Lab and Runtime Inspector are proposed destinations, not current AppShell routes.

Notebook is the proposed persistent cognitive workspace and user-facing management
surface for notes, conversations, files, memory, artifacts and working context.
Workspace storage is distinct from runtime memory; promotion and context selection
are explicit. Follow the [user blueprint](docs/02-product/NOTEBOOK-WORKSPACE-BLUEPRINT.md)
and [delivery slices](docs/02-product/NOTEBOOK-DELIVERY.md).

## Engineering direction

Extend working paths before adding orchestration. Preserve host/provider/device boundaries,
separate inference from assistant handoff, and retain raw protocol provenance. Describe
capability limitations and code contradictions explicitly. The old PR completion table
has been removed because reused identifiers/checkmarks were not current evidence.
