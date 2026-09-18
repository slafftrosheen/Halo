> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# UI Alpha Specification

Status: ACTIVE
Version: Alpha v0.1
Priority: Critical

Purpose: Define every user-facing screen required for Halo Alpha, its responsibilities, expected behaviour, required runtime integration, and acceptance criteria.

No additional screens should be added before Alpha unless absolutely required.
All screens must use the existing design system and shared Halo UI components.

Every screen must have:
- ViewModel
- Immutable UiState
- StateFlow
- Loading state
- Error state
- Empty state

---

# Navigation

**Bottom Navigation**
- Home
- Activity
- Devices
- Settings

**Developer Mode enables:**
- Protocol Lab
- Runtime Inspector
- Console

---

# Screen 1: HOME

Purpose: Runtime entry point. Mission Control. Primary interaction surface.

## Layout
- **Header**: HALO branding + Connection status.
- **Orb**: Primary state indicator (HaloFloatingMic).
- **Contextual Text**: Real-time status message.
- **Intelligence**: Active AI provider info.
- **Device Status**: Battery and connection details.
- **Recent Activity**: Last few timeline events.

## Responsibilities
- Show runtime state.
- Allow manual microphone trigger.
- Display last interaction.
- Display current runtime status.
- Never be empty.

---

# Screen 2: ACTIVITY (Timeline)

Purpose: Visualize cognition and runtime history.

## Layout
- Chronological list of events from `RuntimeSnapshot.timeline`.
- Each event shows title, timestamp, and short description.
- Expandable items for full details (ID, status, full description).

## Acceptance
- Realtime updates.
- Expandable events.
- Real data from Blackboard/Events.

---

# Screen 3: DEVICES

Purpose: Manage wearable hardware connection and capabilities.

## Layout
- **Header**: Active device name and status.
- **Status**: Battery and connection type.
- **Capabilities**: Explicitly reported hardware features (Audio, Camera).
- **Discovery**: Action to find and connect to new glasses.

---

# Screen 4: SETTINGS

Purpose: Configuration and developer tools.

## Layout
- **HALO**: Assistant selection, Voice, Privacy.
- **DEVELOPER**: Developer mode toggle, Protocol Lab, Inspector, Console.
- **PERMISSIONS**: System permission status (Microphone, BLE, Location).

---

# Shared Components
- `HaloGlassSection`
- `HaloGlassCard`
- `HaloGlassChip`
- `HaloGlassButton`
- `HaloFloatingMic`
- `HaloGlassDivider`
