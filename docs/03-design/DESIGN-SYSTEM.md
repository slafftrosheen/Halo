# Halo Design System

Status: DESIGN TARGET. Reviewed 2026-09-24.

This document describes the 2026–27 visual language for the Halo ecosystem. (For the specific workspace implementation redesign, see [HALO-WORKSPACE-DESIGN.md](HALO-WORKSPACE-DESIGN.md)).

## Visual Doctrine

- Airy, bright default theme
- Charcoal dark counterpart
- Halo Signal Face circular presence (outer state halo + inner graphite OLED display)
- Unified grid-based HaloGlyph pixel pictogram vocabulary
- Pastel semantic surfaces
- Quiet depth
- Clean typography
- High information clarity
- Soft geometry
- Strong whitespace

### Halo Signal Face Doctrine

**Halo Presence** is a circular semantic display:
- **Outer Halo**: Communicates state and activity through restrained colour, luminance, and motion.
- **Inner Display**: Communicates meaning through a clear, monochrome pixel pictogram (`HaloGlyph`).

RUNTIME/UI-024 supersedes the 2026-09-18 spatial-cell direction.

### Rejected Concepts

We explicitly reject the following concepts:
- Anthropomorphic AI eyes or cartoon faces
- Abstract morphing blobs or particle clouds
- Decorative sci-fi radar or radial dashboard gauges
- Arbitrary non-semantic idle animations

Dark mode is the exact same design language rendered under different luminance; it is not a separate skin.

## Accessibility

- 48dp touch minimum target size
- Large text support
- TalkBack integration
- Reduced motion settings respected
- Clear selection and focus states
- No gesture-only critical actions
