# Halo

Status: CURRENT SOURCE MAP. Reviewed 2026-09-24.

**Halo is a host-native personal cognitive runtime that connects a user's operating system, models, workspace and physical devices into one coherent assistant.**

## What makes Halo different?

Halo feels like one intelligence that follows the user across their operating system and the devices around them. 

- **Android** is our first host, not the architecture itself. Future hosts could include Linux and Windows through equivalent native adapters.
- **HeyCyan** is our first deeply tested wearable driver. Wearables are first-class interaction devices, but Halo is not tied to any individual hardware family. 
- **Native Local** is the default local inference provider. Curated Android models run through Google LiteRT-LM; GGUF/llama.cpp remains an expert/import compatibility path. Providers are replaceable reasoning backends, not the runtime itself.

None of these individual technologies define Halo. Core functionality remains useful without a mandatory cloud account, companion application, or subscription.

## Current Status
Check [CURRENT-STATE.md](CURRENT-STATE.md) for the exact capabilities of the current implementation.

## Project Navigation

- [Project Direction](PROJECT-DIRECTION.md)
- [Current Implementation State](CURRENT-STATE.md)

### Detailed Documentation
For further reading, see our [Documentation Index](docs/README.md), or jump directly to:

- **[Vision & Principles](docs/01-vision/VISION.md)**: Canonical product vision.
- **[Architecture](docs/04-architecture/ARCHITECTURE.md)**: Technical structure and design targets.
- **[Design System](docs/03-design/DESIGN-SYSTEM.md)**: Visual language and interaction guidelines.
- **[Roadmap](docs/14-roadmap/ROADMAP.md)**: Planned work and next steps.
- **[HeyCyan Protocol](docs/protocols/heycyan/IMPLEMENTATION_STATUS.md)**: Hardware reference implementation status.

**Note:** Executable code and actual wiring are the source of truth for implemented behavior. Specifications describe desired changes; historical reports do not override code.
