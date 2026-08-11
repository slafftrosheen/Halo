# Plan - Fix Compilation Errors in Halo App

The `:app` module is currently failing to compile due to numerous unresolved references, incorrect imports, and API usage errors. This plan addresses these issues across dependency injection, drivers, UI material systems, components, and screens.

## User Review Required

> [!IMPORTANT]
> **HeyCyanHost Implementation**: `HeyCyanHost` is currently missing from the `:drivers:heycyan` module despite being referenced in `HostFactory.kt`. I will implement a basic version that wraps `RealHeyCyanDriver` to satisfy the `WearableHost` interface.

> [!NOTE]
> **HaloGlassTokens Refactoring**: Many components reference `HaloGlassTokens` for spacing and shapes, but the current implementation uses separate objects (`HaloSpacing`, `HaloGlassShapes`). I will consolidate these into a `HaloGlassTokens` object to minimize changes in component files.

## Proposed Changes

### Runtime & Dependency Injection

#### [MODIFY] [EngineGraph.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/di/EngineGraph.kt)
- Fix import for `EpisodicMemoryStore` (move from `com.halo.engines.memory` to `com.halo.engines.memory.store`).

#### [NEW] [HeyCyanHost.kt](file:///C:/Users/Slaff/Halo/drivers/heycyan/src/main/java/com/halo/drivers/heycyan/HeyCyanHost.kt)
- Implement `WearableHost` interface using `RealHeyCyanDriver`.
- Provide `AudioSource` and `AudioOutput` implementations delegating to the driver.

---

### Glass UI Material System

#### [MODIFY] [HaloGlassTokens.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/material/HaloGlassTokens.kt)
- Define `HaloGlassTokens` object that provides aliases to `HaloSpacing`, `HaloGlassShapes`, and `HaloGlassElevation` properties (e.g., `val md = HaloSpacing.md`).

#### [MODIFY] [HaloGlassMaterial.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/material/HaloGlassMaterial.kt)
- Fix imports for `@Immutable`, `Brush`, and `Color`.
- Fix hallucinated `BrushBorderBrush` and other incorrect Compose API calls.

---

### Glass UI Components

#### [MODIFY] [HaloGlassSurface.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloGlassSurface.kt)
- Fix Compose imports: `background`, `BorderStroke`, `graphicsLayer`, `shadow`, `alpha`, `fillMaxSize`, `Box`, `padding`.
- Replace legacy `androidx.ui.geometry` with `androidx.compose.ui.geometry`.
- Fix drawing logic (`drawBehind`, `drawRect`) to use correct Compose APIs.

#### [MODIFY] [HaloGlassButton.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloGlassButton.kt)
- Fix imports for `Box`, `Text`, `padding`, `Alignment`, `sp`, `FontWeight`.
- Fix `mutableStateOf` import and usage (ensure it uses `remember { mutableStateOf(...) }` correctly).
- Fix reassignments of `val` by using `MutableState` properly or fixing variable declarations.
- Fix `pointerInput` and `detectTapGestures` imports.

#### [MODIFY] Glass Components List
Fix imports and `HaloGlassTokens` references in:
- [HaloGlassCard.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloGlassCard.kt)
- [HaloGlassChip.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloGlassChip.kt)
- [HaloGlassDialog.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloGlassDialog.kt)
- [HaloGlassDivider.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloGlassDivider.kt)
- [HaloGlassMetric.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloGlassMetric.kt)
- [HaloGlassNavigation.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloGlassNavigation.kt)
- [HaloGlassSection.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloGlassSection.kt)
- [HaloGlassSheet.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloGlassSheet.kt)
- [HaloStateOrb.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloStateOrb.kt)

---

### App UI Screens & ViewModels

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/Slaff/Halo/app/src/main/java/com/halo/app/ui/screens/settings/SettingsScreen.kt)
- Fix imports for `SettingsViewModel` and `collectAsState`.
- Fix icon references (e.g., `Icons.Default.Mic` instead of `Voice`).
- Fix `HaloGlassChip` usages.

#### [MODIFY] Screen & ViewModel Fixes
- Fix data class redeclarations (`ConsoleUiState`, `TimelinePreviewEvent`) in ViewModels and Screens.
- Fix parameter mismatches in ViewModel updates.
- Fix `collectAsState` usage in `ActivityScreen`, `DevicesScreen`, `RuntimeInspectorScreen`, and `ProtocolLabScreen`.

## Verification Plan

### Automated Tests
- Run `:app:assembleDebug` to verify that the module compiles successfully.
- Run existing unit tests in `:app` and relevant modules.

### Manual Verification
- Deploy the app to a simulator/device to verify UI rendering of Glass components.
