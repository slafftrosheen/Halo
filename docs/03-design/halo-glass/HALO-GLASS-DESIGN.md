# HALO-GLASS-DESIGN.md
**Status**: ACTIVE  
**Authority**: Single source of truth for Halo Glass design following PR-017/PR-018  
**Last Updated**: 2026-08-10  

---

# HALO Glass Design System

## 1. Design Philosophy

> **Halo uses a premium glassmorphic interface inspired by Apple's modern spatial/material design language, but it is not an Apple clone.**

### Visual Language
```
dark • translucent • layered • quiet • precise • soft • spatial • high contrast • subtle depth • minimal
```

### Core Principles
1. **Functional Glass** - Glass effects serve purpose, not decoration
2. **Performance First** - Blur must not compromise 60fps
3. **State Communication** - Visuals express runtime state clearly
4. **Accessibility** - All visuals meet WCAG 2.1 AA contrast

---

## 2. Material System: Five Glass Levels

| Level | Name | Alpha | Color | Blur | Elevation | Use |
|-------|------|-------|-------|------|-----------|-----|
| 1 | Background | ~55% | #080A0B | 20dp | 0dp | Full-screen backdrop |
| 2 | Standard | ~66% | #141719 | 30dp | 4dp | Cards, sections |
| 3 | Elevated | ~75% | #1B1F20 | 40dp | 12dp | Modals, sheets |
| 4 | Strong | ~82% | #1B1F20 | 50dp | 20dp | Floating mic, critical UI |
| 5 | Interactive | ~90% | #1B1F20 | 20dp | 8dp | Buttons, controls |

---

## 3. Color Palette

**Base:**
- Background: #080A0B
- Glass: #141719
- Glass Elevated: #1B1F20

**Text:**
- Primary: #F5F7F6
- Secondary: #A5ADA9
- Muted: #6B7471

**Accent (Halo Green/Cyan Hybrid):**
- Primary: #7DE2B1
- Strong: #54D99A
- Muted: #337DE2B1 (20% alpha)

**Semantic:**
- Success: #7DE2B1
- Warning: #F0B86B
- Danger: #FF6B72
- Info: #4EA8FF

**Borders:**
- Subtle: #33FFFFFF (20%)
- Standard: #4DFFFFFF (30%)
- Strong: #80FFFFFF (50%)

---

## 4. Blur Strategy

- **API 31+**: RenderEffect.createBlurEffect() - Full backdrop blur
- **API 29-30**: BlurMaskFilter (limited)
- **API 24-28**: Fallback = translucent + gradient highlight + noise + shadow

**Performance:** Max 2-3 glass surfaces with blur on screen, cache blur radius, test on target hardware.

---

## 5. Spacing & Shapes

```kotlin
object HaloSpacing {
    val xxs = 2.dp; val xs = 4.dp; val sm = 8.dp; val md = 12.dp
    val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp; val xxxl = 48.dp
}

object HaloGlassShapes {
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(18.dp)
    val large = RoundedCornerShape(24.dp)
    val xl = RoundedCornerShape(32.dp)
    val pill = RoundedCornerShape(percent = 50)
    val circle = CircleShape
}
```

---

## 6. Motion

| Type | Duration |
|------|----------|
| Immediate | 120ms |
| Standard | 200ms |
| Emphasized | 300ms |

All animations respect `reduceMotion` accessibility setting.

---

## 7. Typography Hierarchy

- **Display**: 32-40sp (HALO logo, hero text)
- **Headline**: 24-28sp (section titles)
- **Title**: 18-22sp (card titles)
- **Body**: 15-17sp (content text)
- **Label**: 12-14sp (labels, buttons)
- **Mono**: 12-14sp (Console, diagnostics)

---

## 8. Runtime State Visualization (HaloStateOrb)

```text
IDLE       ○ Thin ring, subtle 4s breath
LISTENING  ◉ Filled dot, 1.5s pulse ring
CAPTURING  ● Solid, waveform from center
RECOGNIZING ◌ Ring, 3 dots orbiting 2s
REASONING  ◉ Dot, 0.8s scale pulse
PLANNING   ◉ Dot, ring segments fill clockwise
EXECUTING  ◉ Dot, rotating progress ring
SPEAKING   ◉ Dot, outward sonar pulses
RECOVERING ◌ Amber ring, slow 4s breath
STOPPED    ✕ Cross, fade to idle
```

**Forbidden:** Random glow, particles, permanent pulsing, decorative motion.

---

## 9. Components

| Component | Material | Description |
|-----------|----------|-------------|
| HaloGlassSurface | Variable | Base glass container |
| HaloGlassCard | Standard/Elevated/Strong | Content containers |
| HaloGlassSection | Standard | Titled sections |
| HaloGlassButton | Interactive | Action buttons |
| HaloGlassChip | Standard | Labels, status indicators |
| HaloGlassDivider | Standard | Separators |
| HaloGlassNavigation | Background | Bottom nav bar |
| HaloGlassSheet | Elevated | Bottom sheets |
| HaloGlassDialog | Elevated/Strong | Modal dialogs |
| HaloGlassMetric | Standard | Label-value pairs |

---

## 10. Navigation

**Bottom Navigation:**
1. Home (`home`) - Runtime, state visualization
2. Activity (`activity`) - Cognitive timeline
3. Devices (`devices`) - Device management
4. Settings (`settings`) - Configuration

**Developer Mode (Drawer only):**
- Console (`console`)
- Protocol Lab (`protocol-lab`)
- Runtime Inspector (`runtime-inspector`)

**BLE Capture is NOT a primary navigation item.**

---

## 11. Accessibility

- Color contrast ≥4.5:1 (text), ≥3:1 (UI)
- Font scaling support up to 200%
- All animations respect `reduceMotion` setting
- Tested with TalkBack and Switch Access

---

**End of HALO-GLASS-DESIGN.md** — This is the authoritative design specification. All UI implementations must follow this document.