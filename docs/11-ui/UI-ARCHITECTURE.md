# UI Architecture Specification

**Status**: ACTIVE  
**Version**: 1.0 (Halo Glass)  
**Last Updated**: 2026-08-10  
**Authority**: Referenced by PROJECT-DIRECTION.md

## Overview

Defines presentation layer architecture for Halo Glass, the premium glassmorphic interface.

## Core Principles

1. **Glass-First**: All UI uses Halo Glass material system. No raw Material3 in screens.
2. **StateFlow-Driven**: ViewModel → StateFlow<UiState> → Composable pattern.
3. **Functional Animation Only**: Motion communicates state/feedback. No decorative motion.
4. **Developer Mode Separation**: Advanced tools behind Settings → Developer.
5. **Capability-Driven Device UI**: Driven by DeviceCapabilityRegistry.

## Layered Architecture

```
Presentation (Screens → ViewModels → UI State)
Application (Use Cases → Command/Query Bus)
Domain (Runtime → Engines → Blackboard)
Interface (Device Interfaces → Providers)
Infrastructure (BLE Drivers → SDKs → Hardware)
```

**Dependency Rule**: Downward only. Presentation never calls domain directly.

## Screen Structure

```kotlin
@Composable
fun ScreenName(viewModel: ScreenViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    if (uiState.isLoading) { LoadingScreen() }
    if (uiState.hasError) { ErrorScreen(uiState.error) }
    if (uiState.isEmpty) { EmptyScreen(uiState.emptyType) }
    ScreenContent(uiState)
}
```

**Required UI State**: isLoading, hasError, isEmpty, error, emptyType

## Component Hierarchy

```
HaloGlassSurface
    ��� � � ↓
HaloGlassCard
    ��� � � ↓
HaloGlassSection
    ��� � � ↓
HaloGlassControl (Button, Chip, etc.)
    ��� � � ↓
HaloGlassContent (Text, Icon, etc.)
```

## Navigation Rules

### Primary Navigation (Bottom Bar)
1. Home (`home`) - Runtime entry point
2. Activity (`activity`) - Cognitive timeline
3. Devices (`devices`) - Device management
4. Settings (`settings`) - Configuration + Developer Mode

### Secondary Navigation (Drawer - Dev Mode Only)
- Console (`console`) - Runtime diagnostics
- Protocol Lab (`protocol-lab`) - BLE exploration
- Runtime Inspector (`runtime-inspector`) - Live runtime graph

## Theming

### Glass Materials (5 Levels)
- Background (~55% alpha) - Full-screen backdrop
- Standard (~66%) - Cards, sections
- Elevated (~75%) - Modals, sheets
- Strong (~82%) - Floating mic, critical
- Interactive (~90%) - Buttons, controls

### Tokens
- Spacing: `HaloSpacing` (2,4,8,12,16,24,32,48 dp)
- Shapes: `HaloGlassShapes` (12,18,24,32 dp radii)
- Elevation: `HaloGlassElevation` (0,4,8,12,20,32 dp)
- Motion: `HaloGlassMotion` (120,200,300 ms)

### Colors (HaloGlassColors)
- Neutrals: #080A0B, #0D1012, #141719, #1B1F20, #222729
- Text: #F5F7F6, #A5ADA9, #6B7471
- Accent: #7DE2B1 (primary), #54D99A (strong)
- Semantic: Success/#7DE2B1, Warning/#F0B86B, Danger/#FF6B72, Info/#4EA8FF

## Performance

- <16ms recomposition for Home screen
- Stable 60 FPS on target devices
- Cold start < 2 seconds
- Memory: No leaks, proper disposal

## Accessibility

- Contrast: ≥4.5:1 text, ≥3:1 components
- Font scaling: Supports up to 200%
- Motion: Respects reduceMotion system setting
- TalkBack and Switch Access compatible

## Testing

- Unit tests: ViewModels with coroutine testing
- UI tests: Critical paths and navigation flows
- Screenshot tests: Glass rendering and animation keyframes

## Migration Path

Legacy → Halo Glass:
- `HaloCard` → `HaloGlassCard`
- `HaloSection` → `HaloGlassSection`
- `HaloButton` → `HaloGlassButton`
- `HaloTag`/`HaloStatusChip` → `HaloGlassChip`
- `HaloFloatingMic` → `HaloStateOrb`
- `HaloMetric` → `HaloGlassMetric`
- `HaloDivider` → `HaloGlassDivider`

## Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-08-10 | — | Initial Halo Glass UI Architecture |

**End of Document** — Works with `docs/03-design/halo-glass/HALO-GLASS-DESIGN.md`