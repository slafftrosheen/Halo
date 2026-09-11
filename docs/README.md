# Halo Documentation

**Documentation index — refreshed 2026-09-11**

Halo is a Portable Cognitive Runtime for wearable computing. Android is the first host; the runtime, device boundaries, cognition, and provider contracts are the durable architecture.

## Authority hierarchy

When documents disagree, use this order:

1. **[PROJECT-DIRECTION.md](../PROJECT-DIRECTION.md)** — canonical product direction
2. **[CONSTITUTION.md](../CONSTITUTION.md)** / HPS specifications — immutable engineering principles
3. **[ARCHITECTURE-GUARDRAILS.md](ARCHITECTURE-GUARDRAILS.md)** — architectural constraints
4. **[04-architecture/](04-architecture/)** — runtime architecture
5. **[03-design/halo-glass/](03-design/halo-glass/)** — authoritative visual language
6. **[07-platform/](07-platform/)** and **[protocols/](protocols/)** — device/platform contracts
7. **[11-ui/](11-ui/)** — presentation architecture and screen guidance
8. **[14-roadmap/](14-roadmap/)** — execution planning
9. **archive/** — historical reference only

## Start here

- [PROJECT-DIRECTION.md](../PROJECT-DIRECTION.md) — why Halo exists and what must remain true
- [ARCHITECTURE-GUARDRAILS.md](ARCHITECTURE-GUARDRAILS.md) — rules for extending the runtime
- [04-architecture/ARCHITECTURE.md](04-architecture/ARCHITECTURE.md) — system architecture
- [03-design/halo-glass/HALO-GLASS-DESIGN.md](03-design/halo-glass/HALO-GLASS-DESIGN.md) — visual system
- [11-ui/UI-ARCHITECTURE.md](11-ui/UI-ARCHITECTURE.md) — Compose/UI boundaries
- [protocols/README.md](protocols/README.md) — evidence-backed device protocol work

## Documentation domains

| Area | Purpose |
|---|---|
| `01-vision` | Vision, mission, platform DNA |
| `02-product` | Product requirements and MVP scope |
| `03-design` | Halo Glass visual system |
| `04-architecture` | Runtime architecture and engineering boundaries |
| `05-runtime` | Runtime lifecycle and APIs |
| `06-cognition` | Reasoning, memory, cognitive models |
| `07-platform` | Drivers, devices, host/platform boundaries |
| `08-providers` | AI provider integrations |
| `09-plugins` | Plugin architecture |
| `10-features` | Functional capabilities |
| `11-ui` | UI architecture and screen specifications |
| `12-security` | Security, privacy, permissions |
| `13-engineering` | Engineering practices and diagnostics |
| `14-roadmap` | Milestones and release planning |
| `15-brand` | Brand and visual identity |
| `protocols` | HeyCyan and other device protocol evidence |
| `specifications` | Formal HPS specifications |
| `archive` | Superseded material; reference only |

## Current implementation reality

The repository is an active implementation, not a literal realization of every historical specification. New documentation must describe behavior that exists or clearly label planned behavior as such.

In particular:

- The cognitive runtime is the product boundary; Android remains a host adapter.
- Device interaction goes through runtime/device abstractions rather than UI code calling vendor SDKs directly.
- AI inference and external-assistant handoff are separate provider contracts.
- Local inference remains a first-class path; network providers are optional adapters.
- HeyCyan protocol claims must remain evidence-backed. Unverified commands are not presented as supported capabilities.
- Developer diagnostics are intentionally separate from the primary user navigation.
- UI state should represent real runtime/device state rather than simulated controls.

## Documentation hygiene

Prefer updating the authoritative document instead of creating another competing specification. Mark incomplete work as **PLANNED**, **LIMITED**, **BLOCKED**, or **UNVERIFIED** rather than presenting it as shipped.

Historical documents may remain in place for forensic context, but they must not be used as implementation authority when superseded.
