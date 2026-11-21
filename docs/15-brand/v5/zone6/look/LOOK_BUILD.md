# halo · Make it yours — icons, app icon & customisation (pack 6.4)

Grounded in `ui/theme/PalettePreferences.kt` (HaloAccent MINT·BLUE·LILAC·CORAL·SAGE·ROSE, HaloTone NATURAL·WARM·COOL·PAPER·SLATE, HaloBackgroundStyle STILL·HALO·MIST, separate light/dark), `BrandAppearance.kt` (real hex values) and `VisualPreferences.kt`.

## 1. Page structure (Places › Settings › Make it yours — replaces “Appearance”)
- **Looks**: 6 presets (Night Mint, Paper Sage, Slate Blue, Warm Rose, Cool Lilac, Coral Day) — each = real accent + tone + background for its light or dark side (`HaloPreset`).
- **Colours**: accent (6 swatches), tone (5 chips), background (Still · Halo · Mist), per light/dark (existing storage).
- **Icons & app icon**: app icon Match look · Classic · Light · System; icon style Line · Spot · Tile.
- **Motion, text & feel**: Motion Full · Gentle · Still; Haptics; Edge light; Button labels Always · Selected only; text size; next-step hints; replay the dock guide.
- Dock: place “Make it yours · Previewing …”, filled **Apply**, Undo, Help. Live preview of the whole phone; nothing persists until Apply (same pattern as `selectPersisted`).

## 2. App icon follows the look
- 12 adaptive icons (`res/mipmap-anydpi-v26/ic_launcher_{night|cream}_{accent}.xml`): Night Ink or Cream background with a soft radial glow in the accent, canonical open ring (gap at 45°) inside the safe zone, shared monochrome layer for Android 13+ themed icons.
- Night ring = accent; Cream ring = accent ink (light) — contrast: Night 10.2–13.6:1, Cream 5.1–6.4:1.
- `manifest/AndroidManifest.icon-aliases.xml`: Classic alias (existing icon) + 12 aliases. **Move MAIN/LAUNCHER from MainActivity to the Classic alias**; MainActivity stays enabled.
- `HaloAppIconSwitcher.apply()` — enable target first, then disable others; DONT_KILL_APP; only on Apply; skip if unchanged. Warn before Apply: some launchers move the icon.
- Match look: dark side active → Night + accent; light → Cream + accent. System → Classic alias (launcher uses monochrome).

## 3. Icons follow the theme
`HaloThemedIcon(icon, …)` = one call site; colours only from theme roles.
- **Line** (default): accent ink outline. **Spot**: text-colour outline + accent dot (55%). **Tile**: accent-ink glyph on a 15% accent tile.
- Semantic icons (Apricot needs-you, error) always Line in their meaning colour. Higher contrast → solid ink, no tints.
- Checked in all 60 looks: Line ≥ 4.70:1, Spot ≥ 13.0:1, Tile ≥ 3.85:1 (glyph vs tile); filled-button text ≥ 5.32:1.

## 4. Motion level
FULL = current spec (spring damping 0.62). GENTLE = no overshoot (0.9), shorter loops, edge light static. STILL = reduced-motion path (`lessMotion = true`): no springs, 150 ms fades, Lottie end frames.

## 5. Widgets
Widgets already repaint from `nearbyScheme()`; they follow the same look automatically (accent + tone + light/dark).

## 6. Strings
`res/values/halo_look_strings.xml` — 33 English strings, keys ready for -lv/-ru/-pl.
