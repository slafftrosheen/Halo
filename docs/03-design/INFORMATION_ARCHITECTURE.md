> Status: SUPERSEDED DESIGN DRAFT. Classified 2026-09-18.
>
> Legacy design intent, not implemented behavior. The user-provided 2026-09-18 workspace mockup supersedes conflicting dark-only, four-tab and old orb directions. Examples here are illustrative, not a compiled component API.
>
> [Current reference](HALO-WORKSPACE-DESIGN.md).

# Information Architecture

Halo is optimized for doing one thing immediately, not information density.

## Primary Navigation (Bottom Tabs)
* **Home**: System status (battery, active provider, current context).
* **Activity**: Chronological timeline of everything the assistant has done.
* **Skills**: List of capabilities (Memory, Translate, Navigation, GitHub, Calendar, etc.) that can be toggled and configured.
* **Settings**: Core configuration.

## Settings Hierarchy
* **AI Engine** (Providers, Models, Fallbacks)
* **Devices** (Health, RSSI, Firmware, Capabilities)
* **Voice** (Wake Word, STT, TTS, Language)
* **Privacy** (Network logging, sensor toggles, telemetry rules)
* **Automation** (IF/THEN rules)
* **Plugins** (Installed plugins and permissions)
* **Developer Mode** (Runtime health, latency, profiling, logs)

## Search & Command Palette
Universal search accessible via swipe.
Capable of finding memories, settings, providers, and executing commands (e.g., `> Restart Runtime`).
