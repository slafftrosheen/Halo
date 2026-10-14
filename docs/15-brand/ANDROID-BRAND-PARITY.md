# HALO Android · Brand parity implementation

Status: **code-integrated, not visually signed off** (2026-10-14)

## Authority and provenance

Original owner assets at repository root: `HaloBrandKit (1).pdf`, its four `HaloBrandKit (N).png` reference images and `HaloDesignMockup.png`. The GitHub connector could identify these files but could not read their PDF/image bytes.

The HaloWeb repository **does** expose the brand book's implemented, documented design system. This pass intentionally reuses:

- [HaloWeb brand implementation](https://github.com/slafftrosheen/HaloWeb/blob/main/BRAND-IMPLEMENTATION.md)
- [HaloWeb canonical tokens](https://github.com/slafftrosheen/HaloWeb/blob/main/css/tokens.css)
- [Open ring trace](https://github.com/slafftrosheen/HaloWeb/blob/main/assets/brand/halo-ring.svg)

The source ring is explicitly a **trace from page 6 of the owner's raster brand book**, not the official editable vector master. The Android launcher reproduces all 83 source coordinates, the open gap, and the same four gradient stops in the 108dp adaptive icon safe-zone. Monochrome artwork uses the same geometry as an alpha mask, not the coloured gradient.

## Canonical identity tokens (not the older provisional choices)

| Token | Hex | Android role |
|---|---|---|
| Halo Mint | `#0CF2BA` | Dark-mode primary and signal halo |
| Night Ink | `#060330` | Dark background and light-mode ink |
| Deep Mint | `#047A5E` | Accessible light-mode primary action |
| Cream | `#FAFAF4` | Light background and dark-mode text |
| Slate | `#4A4F63` | Secondary identity |
| Sunrise Apricot | `#FF9F6B` | Soft object accent, not an error/success signal |
| Mint Tint | `#B4E6D6` | Ring highlight and selected light container |

Semantic warning/danger remain independent of identity colours. Users can choose alternate accent profiles; the **default** colour scheme follows these canonical values.

Representative measured contrast ratios (normal text): light Deep Mint on Cream ~5.07:1; Night Ink on Cream ~18.85:1; Cream on Night Ink ~18.85:1; Halo Mint on Night Ink ~13.56:1. See `BrandPaletteParityTest` for release assertions.

## Typefaces and licences — bundled and wired

**Delivered in Android main:** local variable TTFs for Sora, Atkinson Hyperlegible Next (regular + italic) and Onest Cyrillic, copied byte-for-byte from the existing HaloWeb assets via GitHub. All four cross-repository Git blob SHAs matched their originals; the three complete SIL OFL 1.1 licence notices were also committed at `app/src/main/assets/licenses/`.

`app/src/main/java/com/halo/app/ui/theme/BrandFonts.kt` maps each face to Android `R.font` resources. Display/headings use **Sora**, body/forms/labels use **Atkinson Hyperlegible Next**, and the Russian app-language theme uses **Onest** because the bundled website Sora/Atkinson subsets lack Cyrillic. `AppLanguageProvider` supplies the selected locale through Compose `LocalConfiguration`; `HaloTheme` reacts to changes without restarting the cognitive runtime.

Unlike CSS `unicode-range`, Compose does not select font resources by glyph subset. Russian UI text therefore uses Onest as its entire type family, with platform fallback where necessary for Latin glyphs. That is a documented deliberate difference from HaloWeb.

No Google Fonts CDN, remote font provider, or startup network permission is used. To verify offline resource integrity in a local checkout:

```bash
python -m pip install fonttools
python scripts/brand/install_fonts.py
# Optional checksum comparison when HaloWeb is checked out beside Halo:
python scripts/brand/install_fonts.py --haloweb-dir ../HaloWeb
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

The script checks local font name metadata and complete OFL notices. Use `--haloweb-dir ../HaloWeb --sync` only to intentionally refresh exact binaries; it does not rewrite the Kotlin font mappings. **Device font rendering and Gradle build still require local execution.**

## Screen and overlay coverage

Shared components: secondary page headers, transparent-less rounded surfaces, modal dialogs, dialog headings, selected controls, text inputs, settings rows, working-context sheets. Primary screen migration: Home, Activity, Notebook, Canvas, Study, Devices, Settings and onboarding. Secondary follow-through: Intelligence, Android Assistant, Voice, Notebook context overlays and Mini Halo. The Signal Face's inner display now uses Night Ink/Cream consistently; outer state colour remains runtime-semantic and preference-aware.

## Visual verification and release gate

```bash
python -m pip install Pillow pymupdf
python scripts/brand/audit_brandkit.py
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

1. Open all original brand-book pages and compare the traced logo against the master's intended shape. Do not infer exact vector geometry solely from a raster trace.
2. Review actual app screenshots alongside the originals at identical density, including launcher icon (regular, round, monochrome), widget, Mini Halo and all eight primary screens.
3. Run dark/light/system, 320dp/360dp/412dp/tablet, 100%/200% font sizes, EN/LV/RU, TalkBack and reduced-motion visual checks.
4. Verify Sora, Atkinson Hyperlegible Next and Onest actually render on device, including Cyrillic fallback, label truncation, keyboard overlays, role/provider failures, and appearance persistence.
5. Keep existing Robolectric/Roborazzi baselines and inspect diffs before updating them. Validate actual font resource loading and gradient rendering on an Android device.
6. Do not mark a design complete, or publish a release, based on GitHub source-only review.

**No Gradle/Android emulator or live hardware test was available for these remote commits.** The actual licensed fonts are now bundled and linked in code; source SHA parity with HaloWeb and brand palette/ring geometry were checked, but rendered pixel parity is not claimed. Pixel-accurate parity with the unavailable original PDF/images is not certified.
