# HALO PROJECT DIRECTION (Authoritative)

**Status**: ACTIVE  
**Version**: 1.0  
**Last Updated**: 2026-08-10  
**Authority**: This document is the single source of truth for project direction. All agents, contributors, and automation must align with this document.

---

## Authority Hierarchy

When documents conflict, the following hierarchy determines authority (highest first):

1. **CONSTITUTION.md** (HPS-9000) — Immutable principles
2. **ARCHITECTURE-GUARDRAILS.md** — Engineering constraints
3. **docs/04-architecture/ARCHITECTURE.md** — System architecture
4. **docs/02-product/PRD.md** — Product requirements
5. **docs/07-platform/DRIVERS-SPEC.md** — Device platform specification
6. **docs/03-design/halo-glass/HALO-GLASS-DESIGN.md** — Visual/UI design system
7. **docs/14-roadmap/ROADMAP.md** — Execution roadmap
8. **Historical/archive documents** — Reference only, not authoritative

**Rule**: If a lower-priority document contradicts a higher-priority one, the higher-priority document wins. The lower-priority document must be updated or marked SUPERSEDED.

---

## Canonical Architecture

### What Halo Is

```
Halo = Portable Cognitive Runtime for Wearable Computing
```

- **The Runtime is the Product** — Not the Android app, not the UI, not any single device integration
- **Android is the First Host** — Development host only, not the product identity
- **Every Wearable = Complete Device Backend** — Implemented behind stable interfaces
- **Runtime = Device-Agnostic** — Core cognition runs independent of Android, BLE, or any transport
- **UI = Halo Glass** — Premium glassmorphic interface (Apple-quality, not Material 3)

### Two Parallel Tracks

```
                         HALO
                           │
             ┌─────────────┴─────────────┐
             │                           │
       DEVICE PLATFORM             HALO GLASS
             │                           │
       HeyCyan 100%               Apple-quality
       functionality              glass UI
             │                           │
             └─────────────┬─────────────┘
                           │
                      Alpha Product
```

**Track 1: Device Platform** — HeyCyan driver implementing 100% audited functionality  
**Track 2: Halo Glass** — Premium glassmorphic UI for cognition, runtime state, devices, capabilities, activity, diagnostics, configuration

Both tracks proceed in parallel. Neither blocks the other.

## Frozen Architecture (Guardrails)

Per **ARCHITECTURE-GUARDRAILS.md**, the following are **frozen** and must not change without hardware evidence:

- Cognitive layer (Observation → Interpretation → Reasoning → Planning → Execution → Response)
- Domain layer independence from Android (`android.*` never in cognition/runtime)
- Single cognitive pipeline (no forks, no bypasses)
- Event-driven architecture (SharedFlow event bus)
- Clean/Hexagonal dependency direction (UI → Application → Domain → Interfaces → Infrastructure)

**Any PR violating these guardrails will be rejected.**

---

## Design Direction: Halo Glass

**Authoritative Spec**: `docs/03-design/halo-glass/HALO-GLASS-DESIGN.md`

### Visual Language

```
dark • translucent • layered • quiet • precise • soft • spatial • high contrast • subtle depth • minimal
```

### Material System: Five Glass Levels

| Level | Name | Alpha | Use Case |
|-------|------|-------|----------|
| 1 | Background | ~55% | Full-screen backdrop |
| 2 | Standard | ~66% | Cards, sections |
| 3 | Elevated | ~75% | Modals, sheets |
| 4 | Strong | ~82% | Floating mic, critical UI |
| 5 | Interactive | ~90% | Buttons, controls |

### Color Palette (Authoritative)

```kotlin
// Base
Background       #080A0B
BackgroundSecondary #0D1012

// Glass (opaque equivalents for non-blur fallback)
Glass            #141719
GlassElevated    #1B1F20
GlassStrong      #222729

// Text
TextPrimary      #F5F7F6
TextSecondary    #A5ADA9
TextMuted        #6B7471

// Accent — Halo Green/Cyan Hybrid
Accent           #7DE2B1
AccentStrong     #54D99A
AccentMuted      #337DE2B1

// Semantic
Success          #7DE2B1
Warning          #F0B86B
Danger           #FF6B72
Info             #4EA8FF
```

**Forbidden**: Neon cyan, purple gradients, olive spruce, RGB rainbow, cyberpunk aesthetics.

### Typography

- **Display/UI**: Inter / SF Pro style
- **Diagnostics/Console**: JetBrains Mono
- **Hierarchy**: Display (32-40sp) → Headline (24-28sp) → Title (18-22sp) → Body (15-17sp) → Label (12-14sp) → Mono (12-14sp)

### Motion

- Functional only (no decorative motion)
- Durations: Immediate 120ms, Standard 200ms, Emphasized 300ms
- Spring: dampingRatio 0.8, stiffness 400
- Respects `reduceMotion` accessibility setting

---

## Navigation Structure (Authoritative)

### Bottom Navigation (Primary)

| Tab | Route | Purpose |
|-----|-------|---------|
| Home | `home` | Runtime entry point, state visualization |
| Activity | `activity` | Cognitive timeline (renamed from Timeline) |
| Devices | `devices` | Device management, capabilities |
| Settings | `settings` | Configuration, Developer Mode toggle |

### Developer Mode (Secondary)

Enabled via Settings → Developer. Adds navigation drawer items:

| Item | Route | Purpose |
|------|-------|---------|
| Console | `console` | Runtime, Engines, Blackboard, Providers, Logs, Performance |
| Protocol Lab | `protocol-lab` | BLE protocol exploration |
| Runtime Inspector | `runtime-inspector` | Live runtime graph visualization |

**Rule**: BLE Capture / Discovery is NOT a primary navigation item. It is a developer tool.

---

## Device Integration Rules

1. **Complete Audited Functionality** — Every device backend exposes 100% of its hardware/SDK capabilities
2. **Explicit Representation** — Unsupported functionality shown as "Unsupported", not hidden
3. **Protocol Honesty** — Inferred/assumed protocol behavior never presented as fact
4. **Capability Registry** — All device UI driven by `DeviceCapabilityRegistry`, not hardcoded lists

---

## Engineering Standards

- **Kotlin only** for production code
- **Interfaces over inheritance**
- **Constructor injection** everywhere
- **No global mutable state**
- **No singleton objects** except DI-managed services
- **All public APIs documented** with KDoc
- **Every suspend function** supports cancellation
- **No business logic** in Activities, Fragments, or Compose UI
- **StateFlow** for all UI state (no LiveData, no callbacks)
- **ViewModel → UseCase → Runtime → Repository → Engine** chain

---

## Current PR Sequence

| PR | Focus | Status |
|----|-------|--------|
| PR-017 | Documentation Reconciliation | ✅ COMPLETE |
| PR-018 | Halo Glass Design System | ✅ COMPLETE |
| PR-019 | Home / Runtime Visualization | ✅ COMPLETE |
| PR-020 | Navigation + Devices | ✅ COMPLETE |
| PR-021 | Console / Protocol Lab / Inspector | ✅ COMPLETE |
| PR-022 | Settings / Permissions / Error UX | ✅ COMPLETE |
| PR-023 | HeyCyan Device UI | ✅ COMPLETE |
| PR-024+ | HeyCyan Protocol Completion | ⏳ PENDING |

---

## Superseded Documents

The following documents are **SUPERSEDED** by this direction. They remain for historical reference only.

| Document | Superseded By |
|----------|---------------|
| `docs/03-design/DESIGN-SYSTEM.md` | `docs/03-design/halo-glass/HALO-GLASS-DESIGN.md` |
| `docs/03-design/DESIGN_SYSTEM.md` | `docs/03-design/halo-glass/HALO-GLASS-DESIGN.md` |
| `docs/03-design/TOKENS.md` | `docs/03-design/halo-glass/GLASS-MATERIALS.md` + `GLASS-COMPONENTS.md` |
| `docs/03-design/COMPONENTS.md` | `docs/03-design/halo-glass/GLASS-COMPONENTS.md` |
| `docs/ui/UI-ALPHA-SPECIFICATION.md` | `docs/11-ui/UI-ALPHA-SPECIFICATION.md` |

---

## Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-08-10 | — | Initial canonical direction |

---

**End of Document** — This document is the authority. When in doubt, refer here.