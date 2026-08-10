Status: SUPERSEDED
Authority: docs/03-design/halo-glass/HALO-GLASS-DESIGN.md

# Halo Design System (HDS)

Halo should feel like professional lab equipment, not a standard Android app. Clean, quiet, intentional.

## 1. Color Philosophy
Almost monochrome.
* **Base**: `#0A0A0A`
* **Surface**: `#151515`
* **Divider**: `#252525`
* **Primary Text**: `White`
* **Secondary Text**: `Gray`
* **Accent**: One color only (e.g., Electric Cyan `#00E5FF`). Never multiple accents.

## 2. Typography
Huge and legible at a glance.
* **Display Large**: 36sp
* **Headline Large**: 28sp
* **Title Large**: 22sp
* **Body Large**: 18sp
* **Label Medium**: 14sp
Nothing smaller. Wearables aren't for reading essays.

## 3. Motion
Purposeful animations reflecting weight and momentum. No bouncing or overshooting. Duration: 150-220ms.

## 4. Primitives (The 12 Components)
Every screen is built from these primitives to ensure a relentless, recognizable consistency.
* **StateTile**: Displays a system state (e.g., "Connected", "Listening").
* **MetricRow**: Label + live value (e.g., "Latency: 148 ms").
* **ActionRow**: Tappable row with one primary action.
* **CapabilityChip**: Small, text-first indicators (e.g., "LOCAL", "VISION").
* **TimelineEntry**: Consistent activity/history item.
* **WorkflowCard**: Shows an automation in IF -> THEN form.
* **TrustBadge**: Indicates LOCAL, CLOUD, ENCRYPTED.
* **SectionHeader**: Large title with optional status text.
* **InlineLog**: Compact, monospace log snippet.
* **ProviderBadge**: Identifies the active AI backend.
* **PermissionState**: One-line permission summary.
* **HealthIndicator**: Visualizes runtime health without flashy gauges.

## 5. Adaptive Density
The interface adapts based on who is holding it (e.g., standard mode vs. Developer Mode), revealing more technical components (`InlineLog`, `MetricRow` for latency) without requiring a separate app.
# Halo Design System (HDS)

Halo should feel like professional lab equipment, not a standard Android app. Clean, quiet, intentional.

## 1. Color Philosophy
Almost monochrome.
* **Base**: `#0A0A0A`
* **Surface**: `#151515`
* **Divider**: `#252525`
* **Primary Text**: `White`
* **Secondary Text**: `Gray`
* **Accent**: One color only (e.g., Electric Cyan `#00E5FF`). Never multiple accents.

## 2. Typography
Huge and legible at a glance.
* **Display Large**: 36sp
* **Headline Large**: 28sp
* **Title Large**: 22sp
* **Body Large**: 18sp
* **Label Medium**: 14sp
Nothing smaller. Wearables aren't for reading essays.

## 3. Motion
Purposeful animations reflecting weight and momentum. No bouncing or overshooting. Duration: 150-220ms.

## 4. Primitives (The 12 Components)
Every screen is built from these primitives to ensure a relentless, recognizable consistency.
* **StateTile**: Displays a system state (e.g., "Connected", "Listening").
* **MetricRow**: Label + live value ("Latency: 148 ms").
* **ActionRow**: Tappable row with one primary action.
* **CapabilityChip**: Small, text-first indicators ("LOCAL", "VISION").
* **TimelineEntry**: Consistent activity/history item.
* **WorkflowCard**: Shows an automation in IF -> THEN form.
* **TrustBadge**: Indicates LOCAL, CLOUD, ENCRYPTED.
* **SectionHeader**: Large title with optional status text.
* **InlineLog**: Compact, monospace log snippet.
* **ProviderBadge**: Identifies the active AI backend.
* **PermissionState**: One-line permission summary.
* **HealthIndicator**: Visualizes runtime health without flashy gauges.

## 5. Adaptive Density
The interface adapts based on who is holding it (e.g., standard mode vs. Developer Mode), revealing more technical components (`InlineLog`, `MetricRow` for latency) without requiring a separate app.
