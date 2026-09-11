# HALO-GLASS-DESIGN.md

**Status:** ACTIVE  
**Authority:** Single source of truth for the Halo Glass visual language  
**Last Updated:** 2026-09-11

---

## 1. Design philosophy

Halo uses a premium dark glass interface with a restrained technical/pixel character. It should feel like an instrument for a cognitive runtime: calm at rest, precise under activity, and information-dense without becoming a dashboard.

The target is **glass + signal**, not generic cyberpunk and not an Apple clone.

Visual language:

`dark · translucent · layered · quiet · precise · soft · spatial · high-contrast · technical · minimal`

The pixel language is a secondary identity layer. Use small square markers, compact monospace metadata, stepped/rectilinear accents, and restrained status indicators. Do not cover the UI in pixel art.

Core principles:

1. **Functional glass** — depth and translucency communicate hierarchy.
2. **Runtime first** — visual state must reflect real runtime/device state.
3. **Signal, not decoration** — pixels, glow and motion are meaningful only when they communicate state.
4. **Performance first** — never trade interaction quality for blur effects.
5. **Accessibility first** — typography, contrast, touch targets and reduced-motion behavior remain usable.

## 2. Material system

| Level | Name | Use |
|---|---|---|
| 1 | Background | Full-screen backdrop |
| 2 | Standard glass | Cards and sections |
| 3 | Elevated glass | Dialogs and sheets |
| 4 | Strong glass | Critical/floating interaction |
| 5 | Interactive glass | Buttons and controls |

The current Android implementation intentionally uses opaque/translucent Material surfaces rather than forcing platform blur everywhere. True backdrop blur is optional enhancement, never a prerequisite for readability.

Limit expensive blur surfaces to roughly 2–3 per screen when platform support permits it.

## 3. Color tokens

**Base**
- Background: `#080A0B`
- Glass: `#141719`
- Elevated glass: `#1B1F20`
- Signal surface: `#101516`

**Text**
- Primary: `#F5F7F6`
- Secondary: `#A5ADA9`
- Muted: `#6B7471`

**Halo signal**
- Primary: `#7DE2B1`
- Strong: `#54D99A`
- Information: `#4EA8FF`
- Warning: `#F0B86B`
- Danger: `#FF6B72`

**Borders**
- Subtle: white at ~20%
- Standard: white at ~30%
- Strong: white at ~50%

The green/cyan signal should remain sparse. A healthy screen should still look mostly graphite when no important runtime state is active.

## 4. Pixel language

Pixel treatment is deliberately small-scale:

- 4–8dp square signal markers for runtime/status state.
- Monospace only for diagnostics, identifiers, compact metadata and developer surfaces.
- Slightly stepped/rectilinear motifs may appear in backgrounds or state visualizations.
- Avoid pixel fonts for normal body copy; readability wins.
- Avoid permanent scanlines, CRT effects, noisy textures, particle fields or constant glow.
- Never use a pixel motif to disguise missing functionality.

A useful rule: **one strong signal is better than ten decorative ones.**

## 5. Spacing and shapes

```kotlin
object HaloSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}
```

Preferred shapes:

- Small controls: 6–12dp
- Cards/sections: 18dp
- Dialogs/sheets: 24dp
- Large floating surfaces: 32dp
- Pills: reserved for status/chips, not every control

Spacing should create hierarchy before borders or shadows do.

## 6. Typography

- Display: 32–40sp
- Headline: 24–28sp
- Title: 18–22sp
- Body: 15–17sp
- Label: 12–14sp
- Diagnostic mono: 11–14sp

Use normal sans-serif typography for primary interaction. Use monospace as a deliberate technical accent, not as the application-wide font.

## 7. Runtime state visualization

The central state visualization should communicate cognition without becoming a decorative animation:

```text
IDLE        ○   quiet ring
LISTENING   ◉   short pulse
CAPTURING   ●   waveform/signal
RECOGNIZING ◌   processing indication
REASONING   ◉   restrained pulse
PLANNING    ◉   progressive segments
EXECUTING   ◉   progress state
SPEAKING    ◉   outward audio signal
RECOVERING  ◌   amber recovery state
STOPPED     ×   settled/disabled state
```

Forbidden: random glow, permanent pulsing, decorative particles, motion without state meaning.

## 8. Components

The shared Compose design system provides the visual primitives used by screens:

- `HaloCard` — standard glass content surface
- `HaloButton` — primary/secondary action
- `HaloStatusChip` — state + square signal marker
- `HaloTag` — compact metadata label
- `HaloSection` — section heading + signal marker
- `HaloMetricCard` / `HaloMetric` — compact runtime metrics
- `HaloStatus` — label/value health state
- `HaloTimelineItem` — activity timeline row
- `HaloDialog` — elevated modal surface
- `ConsoleSection` / `ConsoleRow` — technical/developer surfaces
- `HaloPixelSignal` — intentionally small runtime/status marker

New screens should reuse these primitives rather than introducing one-off card/button styles.

## 9. Navigation

Primary navigation:

1. **Home** — runtime state and cognitive activity
2. **Activity** — timeline/history
3. **Devices** — saved devices and live device state
4. **Settings** — system configuration

Secondary/developer surfaces remain outside primary navigation:

- Intelligence
- Permissions
- Discovery/pairing
- Console
- Runtime Inspector
- Protocol Lab

Developer tooling must never visually compete with the primary wearable workflow.

## 10. Device UI

Device pages must answer three questions immediately:

1. **Is the device connected and healthy?**
2. **What can it actually do right now?**
3. **What actions can I safely perform?**

Capabilities must be labeled truthfully as available, limited, structural/unverified, blocked, or unavailable. Do not render a button for a protocol action that is not verified.

For HeyCyan, hardware input descriptions and capability status are informational unless a corresponding runtime command is actually implemented.

## 11. Motion

Target timings:

| Type | Duration |
|---|---:|
| Immediate | 120ms |
| Standard | 200ms |
| Emphasized | 300ms |

All motion must respect the platform's reduced-motion preference. State transitions should settle quickly; Halo is an instrument, not an animation showcase.

## 12. Accessibility

- Text contrast target: ≥4.5:1
- UI contrast target: ≥3:1
- Support system font scaling up to 200% without clipping or overlap.
- Minimum practical touch target: 48dp.
- Do not encode state using color alone.
- Support TalkBack semantics on interactive controls.
- Respect reduced-motion preferences.

## 13. Performance guardrails

- Prefer Material/alpha surfaces over universal runtime blur.
- Avoid nested expensive effects.
- Keep scrolling screens lazy.
- Do not animate large blurred regions continuously.
- Verify visual changes on the actual phone and wearable workflow before calling them final.

**End of HALO-GLASS-DESIGN.md** — authoritative visual guidance for Halo Glass.
