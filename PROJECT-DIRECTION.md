# Halo project direction

Status: DESIGN TARGET. Updated 2026-09-18.

Halo aims to be a portable cognitive runtime for wearable computing. Android is the
current app host. Local processing, interchangeable providers, device abstractions,
inspectable actions and the pixel/glass identity guide development.

## Truth and intent

Current source and its wiring determine what exists. [CURRENT-STATE](CURRENT-STATE.md)
maps that implementation. Tests and physical evidence have distinct verification scopes.
The [constitution](CONSTITUTION.md) records engineering targets, not universal conformance.
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

## Visual direction — user mockup, 2026-09-18

Use the [user mockup and design guide](docs/03-design/HALO-WORKSPACE-DESIGN.md):
airy light and charcoal dark themes, a presence made of translucent spatial cells,
pastel category surfaces, and Home/Activity/Notebook/Devices/Settings navigation.
Retain the pixel identity; any halo is soft depth/light, not a mechanical ring or gauge.
This supersedes
dark-only/four-tab design targets. It is planned design, not current app behavior.
Clear actions, state honesty, accessibility and measured performance remain targets.

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
