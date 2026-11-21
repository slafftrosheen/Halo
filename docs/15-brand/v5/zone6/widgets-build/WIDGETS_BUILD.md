# halo Zone 6 · home-screen widgets — build kit (pack 6.2)

Baseline: `slafftrosheen/Halo@257a013` — `nativeactions/EverydayWidgetViews.kt` (measured RemoteViews face), `EverydayWidgetProvider.kt` (8 providers), `ui/mini/HaloNearby.kt` (scheme, orb, background).
Contract kept: **OS-bounded cached repaint — no fetch, no wake lock from the widget.** Sources refresh inside halo.

## 1. What's in `res/`
| File | Change |
|---|---|
| layout/halo_everyday_widget.xml (+ _ru) | Zone 6 face. **Every existing view id kept** (drop-in). New optional ids: `everyday_widget_ask`, `everyday_widget_alert`. Title becomes a 14sp label; clock 40sp; rows get a hairline divider; status = freshness line; configure = 48dp pill. |
| layout/halo_widget_preview_*.xml (8) | Picker previews use the same face. |
| drawable/halo_everyday_widget_background.xml | radius 28dp, 1dp outline, accent glow top-right (static fallback). |
| drawable/halo_widget_pill.xml · _pill_attention.xml · _ask_disc.xml · _row_divider.xml | pill (accent 12%), Apricot alert pill, ask disc, row hairline. |
| drawable/ic_halo_widget_ask.xml | **canonical** open ring (gap centred at 45°). |
| values/ + values-night/everyday_widget_colors.xml | Night Ink / Cream fallbacks + new `outline, glow, pill, attention_fill, attention_ink, stale`. Live faces still repaint from `nearbyScheme()` (Appearance accent/tone/contrast). |
| values/everyday_widget_zone6_strings.xml | 11 English strings, keys ready for -lv/-ru/-pl. |

## 2. Kotlin (3 small edits)
1. `HaloNearby.patch` — `nearbyBackground(…, radiusDp = 28f, glow = scheme.primary.toArgb())` (2 optional params, old callers unchanged).
2. `EverydayWidgetViews.render()` — title: 14sp, `onSurfaceVariant`; then replace the status/configure block at the end with
   `Zone6WidgetFace.apply(views, context, instance, face, snapshot, scheme, ratio, remaining, locale, fullDescription = status)`
   (keep the existing technical `status` string as the TalkBack description — it leaves the visible face).
3. `EverydayCards.patch` — `EverydayCardRow.attention` (true only for ALERTS collector rows / BriefFactKind WARNING|ATTENTION). Drives the Apricot pill at the top of **Your day**.

`java/EverydayWidgetZone6.kt` contains `Zone6WidgetFace`: freshness, ask, alert, one fix.

## 3. Behaviour
- **Ask halo about this** (48dp ring, top-right): opens Mini halo with a typed draft “About my Upcoming widget: ” via `MiniHaloEntry.pending(…, "mini_halo", draft)`. Never starts the microphone.
- **Freshness line** (always plain words):
  - fresh → “Updated 9:38” · Your day → “Updated 9:38 · from 6 sources”
  - stale → amber “Updated 07:12 · showing saved information”
  - last refresh failed → amber “Updated 07:12 · can’t reach the source” + pill **Check source**
  - Headlines/Weather with nothing yet → “No news sources yet” + pill **Choose a source**
- **One fix only**, opens `screen/sources` (EverydayRoutes.SOURCES) in halo. “Configure widget” is no longer a permanent row (Android 12+ reconfigure handles it).
- **Sizes**: the existing measured fit logic stays — header, date, label, rows and the fix are shown only if they fit; 2×1 keeps one fact; large text drops rows before shrinking anything.
- **Kids**: no ask ring (adult draft), no alert pill; same face otherwise.

## 4. Checks before release
- All 8 providers × light/dark × 4 accents: text ≥ 4.5:1, pill text ≥ 4.5:1, ring/disc ≥ 3:1.
- Font scale 1.0 / 1.5 / 2.0: no clipped label; fix pill keeps 48dp.
- Freshness never says “Updated” for data that was never received.
- Alert pill only when a real ALERTS/WARNING row exists.
- TalkBack: root reads the full description; ask ring reads “Ask halo about this · Upcoming”.

## 5. Finding
`drawable/ic_halo_brand_halo.xml` (used by `nearbyOrb` and the old header) has its gap centred at ~29° (≈2:30), not the canonical 45°. The new `ic_halo_widget_ask.xml` uses the canonical geometry; consider replacing the brand icon path with it app-wide.
