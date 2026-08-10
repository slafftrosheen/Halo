# GLASS-COMPONENTS.md

**Status**: ACTIVE  
**Authority**: docs/03-design/halo-glass/HALO-GLASS-DESIGN.md

---

# Halo Glass Component Library

Each component uses the glass material system and follows the glass-first principle: no raw Material3 components in screens.

---

## HaloGlassSurface

Base glass surface component implementing the material system.

### Signature

```kotlin
@Composable
fun HaloGlassSurface(
    modifier: Modifier = Modifier,
    material: HaloGlassMaterial = HaloGlassMaterials.Standard,
    shape: Shape = HaloGlassShapes.large,
    content: @Composable () -> Unit
)
```

### Usage

```kotlin
HaloGlassSurface(
    material = HaloGlassMaterials.Standard,
    shape = HaloGlassShapes.large
) {
    // Content here
}
```

---

## HaloGlassCard

Glass card component with elevation variants.

### Signature

```kotlin
@Composable
fun HaloGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    material: HaloGlassMaterial = HaloGlassMaterials.Standard,
    shape: Shape = HaloGlassShapes.large,
    content: @Composable () -> Unit
)
```

### Variants

```kotlin
HaloGlassCard.Standard()  // Default
HaloGlassCard.Elevated()  // For modals
HaloGlassCard.Strong()    // For critical UI
HaloGlassCard.Interactive() // For buttons
```

---

## HaloGlassSection

Titled section for organizing content.

### Signature

```kotlin
@Composable
fun HaloGlassSection(
    modifier: Modifier = Modifier,
    title: String,
    action: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
    material: HaloGlassMaterial = HaloGlassMaterials.Standard
)
```

### Usage

```kotlin
HaloGlassSection(
    title = "Status",
    action = { /* Optional action */ }
) {
    // Section content
}
```

---

## HaloGlassButton

Interactive button with glass styling.

### Signature

```kotlin
@Composable
fun HaloGlassButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isPrimary: Boolean = true
)
```

### Variants

```kotlin
HaloGlassButton.Primary()   // Accent background
HaloGlassButton.Secondary() // Surface background
HaloGlassButton.Ghost()     // Minimal styling
HaloGlassButton.Danger()    // Destructive actions
```

---

## HaloGlassChip

Status indicators and labels.

### Signature

```kotlin
@Composable
fun HaloGlassChip(
    modifier: Modifier = Modifier,
    text: String,
    color: Color
)
```

### Variants

```kotlin
HaloGlassChip.Success()
HaloGlassChip.Warning()
HaloGlassChip.Danger()
HaloGlassChip.Info()
HaloGlassChip.Accent()
```

---

## HALO-GLASS-DESIGN.md is the authoritative design specification. All agents must reference this document for UI implementation.