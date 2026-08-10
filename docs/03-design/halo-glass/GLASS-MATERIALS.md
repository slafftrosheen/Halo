# GLASS-MATERIALS.md

**Status**: ACTIVE  
**Authority**: docs/03-design/halo-glass/HALO-GLASS-DESIGN.md

---

# Halo Glass Materials Reference

Complete reference for the glass material system used across the Halo Glass UI.

---

## Material Levels

### 1. Background

The deepest glass level for full-screen backdrops.

```kotlin
HaloGlassMaterials.Background
// containerColor: Color(0x8C080A0B) ~55% alpha
// borderColor: Color(0x33FFFFFF) 20% white
// blurRadius: 20.dp
// elevation: 0.dp
```

**Use For:** Entire screen backgrounds

---

### 2. Standard

The workhorse glass level for most cards and sections.

```kotlin
HaloGlassMaterials.Standard
// containerColor: Color(0xAA141719) ~66% alpha
// borderColor: Color(0x4DFFFFFF) 30% white
// blurRadius: 30.dp
// elevation: 4.dp
// highlight: Top gradient
```

**Use For:** Cards, sections, lists

---

### 3. Elevated

For surfaces that need to appear lifted.

```kotlin
HaloGlassMaterials.Elevated
// containerColor: Color(0xBF1B1F20) ~75% alpha
// borderColor: Color(0x66FFFFFF) 40% white
// blurRadius: 40.dp
// elevation: 12.dp
// highlight: Top gradient
```

**Use For:** Modals, bottom sheets, dialogs

---

### 4. Strong

For critical focus elements like the floating mic.

```kotlin
HaloGlassMaterials.Strong
// containerColor: Color(0xD11B1F20) ~82% alpha
// borderColor: Color(0x80FFFFFF) 50% white
// blurRadius: 50.dp
// elevation: 20.dp
// highlight: Top gradient
```

**Use For:** Floating mic, critical alerts

---

### 5. Interactive

For tactile controls like buttons.

```kotlin
HaloGlassMaterials.Interactive
// containerColor: Color(0xE61B1F20) ~90% alpha
// borderColor: Color(0x99FFFFFF) 60% white
// blurRadius: 20.dp
// elevation: 8.dp
// highlight: Top gradient
```

**Use For:** Buttons, toggles, switches

---

## Color Tokens (HaloGlassColors)

```kotlin
// Base
val Background = Color(0xFF080A0B)
val Glass = Color(0xFF141719)
val GlassElevated = Color(0xFF1B1F20)

// Text
val TextPrimary = Color(0xFFF5F7F6)
val TextSecondary = Color(0xFFA5ADA9)

// Accent
val Accent = Color(0xFF7DE2B1)
val AccentStrong = Color(0xFF54D99A)

// Semantic
val Success = Color(0xFF7DE2B1)
val Warning = Color(0xFFF0B86B)
val Danger = Color(0xFFFF6B72)
```

---

## Token Objects (HaloGlassTokens)

```kotlin
// Spacing (HaloSpacing)
val xxs = 2.dp
val xs = 4.dp
val sm = 8.dp
val md = 12.dp
val lg = 16.dp
val xl = 24.dp
val xxl = 32.dp
val xxxl = 48.dp

// Shapes (HaloGlassShapes)
val small = RoundedCornerShape(12.dp)
val medium = RoundedCornerShape(18.dp)
val large = RoundedCornerShape(24.dp)
val xl = RoundedCornerShape(32.dp)
val pill = RoundedCornerShape(50%)
val circle = CircleShape

// Elevation (HaloGlassElevation)
val level0 = 0.dp
val level1 = 4.dp
val level2 = 8.dp
val level3 = 12.dp
val level4 = 20.dp
val level5 = 32.dp

// Motion (HaloGlassMotion)
val immediate = 120
val standard = 200
val emphasized = 300
```

---

**GLASS-COMPONENTS.md is the authoritative component reference.**