# Halo in-app graphics v0.5 — Icons, Presence, Surfaces, Kids, Onboarding & Notebook

Built on the Halo identity (The Open Ring). Covers the current Android `main` structure: 5 primary destinations, Mini Halo, Floating Halo, widgets, and the `HaloSignalFace` states.

```
icons/svg/        133 icons, 24×24, stroke = currentColor
icons/android/    the same icons as VectorDrawable XML (tint with android:tint / colorControlNormal)
orb/svg/          21 presence states × dark + light, 240×240 viewBox (scale to any size). These are also the static / reduced-motion versions.
orb/micro/        ring-only versions for 16–32dp (notifications, tiny indicators)
orb/lottie/       15 animated states × dark + light, 240×240, 30fps
tokens.json       colors, motion, sizes, badge rules
```

## 1. Three layers (composed at runtime)

| Layer | What | Where on the orb |
|---|---|---|
| 1 · Presence | One of the states below. Never changed by where AI runs or by device conditions. | The ring + center glyph |
| 2 · Context | on-device · lan · cloud · glasses · mic · camera · tool · memory · phone-action | Badge, bottom-right |
| 3 · Condition | approval · permission · failure · battery-low · disconnected · unavailable · automation · retry · download | Badge, bottom-left |

Badge = 28% of orb diameter, Night Ink border (4dp at Standard). Context badge: Mint glyph on #1A1760. Condition badge: Night Ink glyph on Apricot (Ember for failure only). Show at most one badge per side; Layer 3 wins attention over Layer 2. Badges are hidden at Micro size, where the system notification icon carries the context.

## 2. Presence states

The gap is the storyteller: it **rests** at upper right (ready), **travels** (thinking), **closes** (done), **holds an Apricot dot** (Halo needs you).

| State | Ring | Glyph | Motion (Lottie) | Loop |
|---|---|---|---|---|
| IDLE | Mint gradient, gap at upper right | none | breathing glow 55→90%, ring scale 100→102%, 4s, ease in-out | yes |
| LISTENING | full brightness + 1 ripple | dot | ripple 100→128% fading out, dot pulse, 1.6s | yes |
| CAPTURING_VOICE | full brightness | 5-bar waveform | bars scale staggered, 1.2s (drive with mic level in code when possible) | yes |
| CAPTURING_VISION | full brightness | aperture + pupil | aperture gap rotates, pupil pulse, 3s | yes |
| THINKING | gap orbits once | 3 dots | gap 360° in 3s (ease 0.6/0.4), dots fade in sequence | yes |
| PLANNING (extra) | gap at top | 3 connected steps | static glyph on THINKING motion | — |
| ACTING | 3 segments | chevron | segments advance 1/3 turn per 2s, chevron nudges forward | yes |
| ACTING_PHONE (extra) | 3 segments | phone | same as ACTING | — |
| SPEAKING | + 2 staggered ripples | 3 voice bars | ripples 100→135%, bars pulse, 2s. **Only with audio playback.** | yes |
| STREAMING_TEXT | standard | 3 text lines | lines draw on in sequence, 1.5s. Use for text replies without audio. | yes |
| PREPARING_CONTEXT (extra) | standard | eye | static glyph on IDLE motion | — |
| WAITING | 55% opacity | Apricot gap dot | dot breathes 100→35%, 2s | yes |
| APPROVAL_REQUIRED | 60% + Apricot arc at gap | approve box + Apricot dot | dot pulse, 2s | yes |
| SUCCESS | gap closes into a full ring | check | ring closes in 0.5s, check draws on, then hold | play once |
| WARNING | Apricot arc fades in at gap | ! | 0.5s transition, then hold | play once |
| ERROR | Ember #D64545 | × | static | — |
| RECOVERING (extra) | 80% | retry arrow | static glyph; may reuse THINKING gap motion | — |
| MODEL_LOADING (extra) | progress arc (bind to real %) | download arrow | drive the arc from progress in code | — |
| PERMISSION_MISSING (extra) | 55% + Apricot gap dot | lock | WAITING motion | — |
| OFFLINE | dashed, 60%, Dusk #9A9EB5 (dark) / Slate (light) | none | static | — |
| STOPPED | solid Dusk/Slate, no glow | stop square | static | — |

### Fixes recommended in the state mapping
1. **Split CAPTURING** into `CAPTURING_VOICE` (waveform) and `CAPTURING_VISION` (aperture). Speech capture must never resolve to the camera glyph.
2. **Split SPEAKING from STREAMING_TEXT.** SPEAKING (outward ripples) only when audio is actually playing; text streaming uses the text-lines glyph.
3. **Home orb size.** Home is 64dp but the renderer needs 72dp for motion. Either move Home to Standard (80dp) or add a *Compact motion profile* for 40–79dp: opacity breathing and gap rotation only, no ripples, no glow overflow.

### Honesty rules (never imply a capability that isn't active)
- Ripples, waveform and mic badge appear **only while the mic is actually open**. IDLE never shows ripples.
- Camera/aperture only while the camera is capturing.
- Cloud badge only when the current turn actually used a remote provider.
- ACTING / phone glyph only while an action is executing; future-category icons stay out of shipped UI.

## 3. Sizes

| Asset | dp | Use |
|---|---|---|
| Hero | 160–240 | future immersive views: full motion, glow, ripples |
| Standard | 80–120 | Home, Mini Halo (88dp): full motion |
| Compact | 40–64 | cards, overlays, Floating Halo (56dp): compact motion profile or static |
| Micro | 16–32 | notifications, tiny indicators: `orb/micro`, static, ring shape + color only |

Floating orb, widgets and Settings identity: use the static SVG for the relevant state (IDLE by default).

## 4. Icons
- 24dp grid, 2dp padding, **1.75 stroke**, round caps and joins, corner radius 2–4.5.
- Every circle carries the Open Ring gap at upper right (≈50°).
- Active / selected = 4.5dp Mint dot top-right **plus** a label/weight change. Never color alone.
- Night Ink on light, Cream on dark. Never Mint or Apricot icons on light surfaces (contrast).
- `home` is the Open Ring itself. Halo *is* home.

## 5. Accessibility
- Reduced motion (animator duration scale = 0 or Halo's own setting): show the static SVG of the same state.
- Each state needs a spoken label (e.g. "Halo is listening", "Halo needs your approval").
- On light surfaces the Mint ring is decorative (1.4:1). State meaning is carried by the Night Ink glyph, the gap dot and the label.
- Ember (#D64545) is a functional color for ERROR only: 4.5:1 on Night Ink, 4.2:1 on Cream (ring/graphic use; pair with ×).

## 6. Lottie notes
- Files are pure shape layers: no images, no expressions, no effects. Small and lottie-android friendly.
- Layer names (`ring`, `glow`, `ripple`, `dot`, `bar`, `line`, `gapdot`, `check`…) can be recolored with `LottieDynamicProperties` / KeyPath if you add themes (e.g. Kids mode).
- Loop all except SUCCESS and WARNING (play once, hold the last frame).

## 7. Icon library (v0.2, 85 icons)
Navigation · Notebook & Canvas · Home & assistant · Settings · Devices & hardware · Halo surfaces · Context (layer 2) · Conditions (layer 3). See `surfaces/icon-library.png`. File names: `halo_ic_<name>`.
- AI-assisted actions carry a small open ring (`ai_draft`, `ai_organize`, `intelligence`). Use it as the "Halo did this" marker, never sparkles.
- `kids_mode` is the ring with a smile. Kids mode may swap the Mint dot for Apricot as its active color.
- `robot` (BuddyBot) is a device icon only. Never use it to represent Halo.
- Future categories (desktop hosts, AR displays, smart home) are intentionally not drawn yet, so the UI doesn't imply features that aren't shipped.

## 8. Surfaces
**Mini Halo** (`surfaces/mini-halo.png`): 88dp orb with full motion. Status line in Sora 700/19, one plain-language sub-line, Stop as a 40dp outlined button top-right, quick-tool chips, and an input pill with a Mint mic as the primary action. Approval replaces the input row with Allow / Not now.
**Floating Halo**: 52dp orb on a 60dp Night Ink disc, docked to the edge, static IDLE. Only WAITING, APPROVAL_REQUIRED or PERMISSION_MISSING add the Apricot condition badge and an optional one-line tip. No motion; tap opens Mini Halo.
**Widgets**: compact 4×1 (orb, "Talk to Halo", mic, capture) and wide 4×2 (greeting + daily count, Talk, Capture, Continue learning with progress). Light and dark follow the system. The orb is static IDLE.
**Launcher** (`launcher/`): adaptive icon (foreground ring in the 66dp safe zone, #060330 background, monochrome layer for themed icons). `ic_launcher_full.svg` is the Play Store source; export at 512×512.

## 9. Kids mode (v0.3)
Files: `kids/orb_svg` (21 states × dark/light), `kids/lottie` (15 × dark/light), `kids/kids-states.png`, `kids/kids-screens.png`.
- Ring gradient Mint → Apricot, ring 25% thicker, warmer glow. IDLE gets a smile (eyes + smile glyph). Every other glyph and every state stays the same, so meaning never changes between modes.
- Apricot leads for warmth and the main action (mic button). Text stays Night Ink on Cream; Apricot is never used as text on light backgrounds.
- Touch targets: 56dp minimum, 72dp primary mic. Body 18sp+, titles Sora 700 at 24sp+. One idea per screen, max 3 choices.
- Approval, camera and new permissions always ask with "Ask a grown-up if you're not sure." Provider, model and advanced settings sit behind the parent lock.
- Wording: `copy/halo_strings_en.json` has a `standard` and a `kids` string for all 21 states.

## 10. Alpha onboarding (v0.3)
`surfaces/onboarding.png` + strings in `copy/halo_strings_en.json` → `onboarding`.
1. **Welcome** (IDLE orb, hero): "Hi, I'm Halo." · Nice to meet you
2. **Where I think** (THINKING): On this phone (recommended, preselected) / On your network / In the cloud · Continue. "You can change this anytime in Settings."
3. **Honest permissions** (LISTENING): Microphone, Notifications, Phone actions (later, approval-only) · Allow microphone / Not now
4. **Alpha invite** (SUCCESS): what alpha means, feedback via Setup & Support, open source, suggest what's next · Let's start
Rules: no permission is requested before screen 3; "Not now" never blocks progress; the cloud option is never preselected.

## 11. Notebook & tools (v0.4) — `notebook/`
**Fixes from the current build (01-audit.png)**
1. Notebook hub shows its title/intro twice → one header. Skills (phone actions) moves to Settings › Actions & Connections.
2. Library times appear in Russian inside the English UI → format dates with the app language, not the system locale.
3. Raw names ("memo_7eb816ce…", "Untitled canvas") → auto-titles: first line of a note, cleaned file name, "Voice memo · 14 Oct, 15:14", or a Halo-suggested canvas title.
4. "Context (0)" → "In chat (0)"; "Hide tools" → a Tools toggle (icon + label).
5. Editor: two action rows + Paragraph + toolbar → one formatting bar on the keyboard + an "Ask Halo" sheet.
6. Canvas tools split across "Add content" and "More" → one bottom dock + Think with Halo button; starter-action empty state.
7. Study setup step numbers repeat ("3" twice) → a 4-step stepper: Goal → Material → Style → Options.

**Hub:** create row (Note, Canvas, Import, Inbox) · Continue (2 recent) · Study progress card · Memory card (Apricot dot when memories wait for approval) · Library preview.
**Library item:** 38dp type tile · title Sora 600/13 · meta "type · collection · time" · "In chat" Mint pill · favorite heart · long-press multi-select · swipe to archive. Filters: All / Notes / Canvases / Files / Chats, plus collection chips.
**Editor:** top bar (back · title + collection/revision · Think with Halo · more) · Edit/Read/Source segmented control · formatting bar only while editing (undo, heading, bold, italic, bullets, numbers, quote, link, code, attach) · Ask Halo sheet: Study this note, Remember in Halo, Create an image, Use in conversation (all with the open-ring marker), then Find & replace, Archive, Move to trash (confirm) · status "Saved on this phone · words".
**Canvas:** dock = Select, Text, Sketch, Link, Group, Import + 58dp Halo orb button. Cards on Surface #110E45, radius 14. Halo drafts: dashed Mint border + "Draft by Halo" + Keep/Discard until accepted. Groups: dashed Cream 22% frame with label. Connections: 2dp Mint curves with 3.5dp dot ends. Sketch: pen, highlighter, eraser, shapes, select; colors Cream, Mint, Apricot, Dusk, soft red; width slider.
**Study:** home (Continue card with progress ring, Start a new lesson, Review today, Recent) · new lesson stepper with style cards (Guide me / Let me practise / Explain it) · practice card (question, voice answer or flip, Halo hint row, "Not yet" / "I knew it").
**Dark surfaces:** background #060330 · Surface #110E45 · Surface 2 #1A1760 · hairline Cream 8% · muted text #B7B9D3.

## 12. Appearance themes (v0.5) — `theme/`
Maps the app's Appearance settings (Light/Dark × Accent Mint/Blue/Lilac/Coral × Background Natural/Warm/Cool) to 24 brand-safe themes. All pass WCAG AA: text and muted text ≥ 4.5:1 on background and both surfaces; accent ink ≥ 4.5:1; text on accent fill ≥ 4.5:1; attention/error graphics ≥ 3:1.
- `halo_themes.json`: every value. `HaloPalette.kt`: ready-to-use Compose colors (surfaces, accent fill/onFill/ink/ring, semantic).
- **Accent changes:** primary buttons, selected chips, links, progress, active-tab dot, in-app orb ring (Lottie: recolor the `ring` gradient stroke via KeyPath `**/ring/**` with the accent ringStart/ringEnd).
- **Accent never changes:** logo, launcher icon, marketing (always Halo Mint) and meaning colors: Apricot = needs you, red = error.
- **Coral retuned** from #F5BEA9 to rose #FF9EB3, because the old Coral was nearly identical to the Apricot attention color.
- **Light mode:** accents are pastel fills with dark text on top; use the `ink` shade for accent text/icons (Mint #047A5E, Blue #1F5FBF, Lilac #6446D6, Coral #B8325A). The attention dot on light uses Apricot Deep #D9692A (3.3:1); attention text uses #A84B12.
- **Natural tone** = brand Night Ink (dark) / Cream (light). Warm and Cool shift only background and surfaces.
- **Kids mode** defaults to Warm and keeps the Mint→Apricot kids ring whatever the accent.
- Suggestion: add **System** to the Light/Dark choice so Halo follows the phone.
