# halo DevKit 2.0 (Brand Book 3.0) — start with IMPLEMENTATION.md, then USER_FACING.md

# halo Developer Kit v1.4 (Brand Book v2.3)

**New in v1.4 — Voice & Read everywhere:** `voice/` makes speaking and listening part of every input and every content surface: 8 field kinds, a coverage map of all 39 input surfaces (113 fields) from the app source, 14 new screens (V01–V08 + Kids K15–K17 + Study S45–S47), 10 components, 9 new Lotties, 65 strings, HaloTextField reference and a 7-journey prototype (`learning/prototype`). Start with `voice/VOICE_READ_HANDOFF.md`.

## v1.3

**New in v1.3 — Learning (Kids + Study):** `learning/` adds the UX–engineering contract for Kids Explore, homework, Kids Create and adult Study: 21 screen IDs with entry/exit events and real states, 16 shared components, hint ladder, Intent rules, 6 new Lotties (+ static fallbacks), 116 comps (light/dark, states, large text, 412 dp), a clickable prototype, strings and Compose wiring. Start with `learning/LEARNING_HANDOFF.md`. Nothing from v1.2 was changed except `tokens.json` (new `learning` block), `IMPLEMENTATION.md` (§7) and this README. See `CHANGES-v1.3.md` and `MANIFEST.json`.

---

# Halo in-app graphics v1.2 — Icons, Presence, Surfaces, Kids, Onboarding, Notebook, Tools, Devices & Settings

**Start with `IMPLEMENTATION.md`.** Built on the halo identity (The Open Ring). Covers the current Android `main` structure: 5 primary destinations, Mini Halo, Floating Halo, widgets, and the `HaloSignalFace` states.

```
icons/svg/        140 icons, 24×24, stroke = currentColor
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

## 13. Halo's tools: Home, approvals, Activity (v0.6) — `tools/`
**Fixes from the current build (01-audit.png)**
1. "Task paused" shows twice with no task name or action → one banner: "“Open Chrome” is paused · Step 2 of 3" with Resume / Stop (Apricot edge).
2. The chat is squeezed into a "Reply" card with three expanders → Home is the conversation: presence row (orb + state + where it's thinking + Ask/Act switch), suggestion chips, thread, composer.
3. The Tools menu mixes tools with chat management → composer "+" sheet = File, Camera, Photo, From Notebook, Timer, Open app, Save to Inbox, New note; ⋮ menu = New conversation, Clear, History, Connection & context.
4. "Mode: Automatic" → Ask / Act chip next to the orb; "1 updates" → "1 update"; notification bell with count in the top bar.
5. "Reflection – No durable learning needed" ×5 → one muted line "Nothing new to remember" (or hidden).
6. Raw, doubled run events → human timeline; raw log behind "Technical log". Mapping: `copy/activity_event_labels.json`.

**Approval sheet:** APPROVAL_REQUIRED orb · "Needs your OK" · one-sentence question · numbered steps Halo will take · what it touches + privacy line · Not now / Allow. **Running:** ACTING_PHONE orb, steps tick off (Mint done, Apricot current, muted next), Stop always visible (also in Mini Halo and the notification). **Failure:** ERROR orb, failed step marked, plain reason, Try again / I'll do it, "Saved to Activity".
**Activity:** tabs Needs you (count) · Running · Done. Run card = type tile, human title, the user's words as subtitle, status chip, where it ran + duration + time. Needs-you and paused cards get an Apricot border. Status chips: Needs your OK, Running, Paused, Done, Stopped, Didn't work.
**Run title rule:** summarize the intent ("Answered a question", "Open Chrome", "Timer · 10 min"); never use the raw message as the title.
New icons: pause, play, history, notifications.

## 14. Devices & Settings (v0.7) — `devices/`, `settings/`
**Fixes from the current build (devices/01-audit.png)**
1. “This phone · Details” lists 33 raw capability names → 7 abilities with on / ask first / off. Mapping: `copy/capability_groups.json`. Raw list behind “Technical names”.
2. Add device goes straight to glasses → chooser sheet: Glasses, BuddyBot, Headphones (↗ Android Bluetooth).
3. “Unknown glasses” + MAC ×3 → glasses only, sorted by signal, name + last 4 address characters, signal bars + words (Very close / Close / In the room / Far away). Unnamed/unsupported fold into “Other devices (n)”.
4. “Stop search” red → neutral outlined. Red = real errors only.
5. Wrong icons → Add device `add_device` (not play), pairing header `glasses` (not eye), Integrations `integrations`, Skills `skills` (not both wrench), Check setup `diagnostics` (not lock), Advanced/Runtime `runtime_status` (not history).
6. “Intelligence: OpenRouter” → location chip “On this phone / On your network / In the cloud”, provider name as secondary text.
7. Light/Dark asked twice → one System / Light / Dark. If “Light colours / Dark colours” sets accent strength, rename “Accent: Soft / Deep”.
8. Kids mode copy → “Simpler screens and kinder words for children. Settings stay behind a parent lock.”
9. Long Settings → search button, value on every row, Halo nearby as 3 tiles, Advanced as last row.

**Devices:** This phone card (location chip + “n of 7 abilities on”), device cards (state, battery %, what it does for Halo: voice / mic / camera chips), disconnected = Dusk “Not connected · seen hh:mm” + Reconnect (never red), Sound routing (speaks through / listens with). The same routing appears in Settings › Voice.
**Pairing:** 3-step stepper Get ready → Find → Connect. Code confirmation only if the device shows a code. Success: SUCCESS orb, name field, switches (voice on, listening on, camera “only when you ask”). Failures: ERROR (couldn’t connect), OFFLINE (went quiet), WARNING (not supported) — each with one next step. BuddyBot reuses the flow with its own icon.
**Settings order:** Your companion (Intelligence, Voice, Language) · Look and feel (Appearance, Kids mode, Halo nearby) · Actions and connections (Integrations, Skills, Automation) · Privacy and data (Privacy, Permissions, Memory and learning, Storage and backup) · Setup and support (Guided setup, Check setup, Default assistant) · Advanced.
**Intelligence:** 3 radio cards (On this phone = recommended, preselected; On your network; In the cloud — never preselected). “Offer the cloud” = Halo asks each time. Cloud replies carry the cloud mark in chat. Model download shows progress and never blocks Halo. Provider detail: connection test, model (Automatic), key “saved on this phone only”, use Never / Ask me (default) / Always, weekly counts, Disconnect.
**Voice:** live mic test (CAPTURING_VOICE orb), Tap / Hold to talk, read aloud Always / When I talk / Never, Halo’s voice + speed, routing, listen language.
**Privacy:** hero answers “did anything leave this phone today?”, where each data type lives, controls (learn from conversations, ask before reading screen), Clear history, Privacy report.
**Permissions:** same 7 abilities, Allowed / Ask first / Off. Apricot border only when a feature the user relies on is blocked.
**Advanced:** health summary first, then Runtime status, Technical log, Export diagnostics (strips personal content), Reset Halo (always confirms).
Strings: `copy/halo_strings_en.json` → `devices`, `settings`. New icons: `signal`, `battery`, `audio_route`.
Note: the Intelligence, Voice, Privacy, Permissions and Advanced inner screens were designed from the Settings structure, not from screenshots of those screens. Match them to real behaviour when available.

## 15. Round 2 (v0.8): halo FX, Guide, wordmark, 2027 polish, Kids 2.0 — `round2/`, `wordmark/`
**Wordmark (`wordmark/`)**: lowercase “halo”, Sora 600, −3% tracking; the “o” is the Open Ring (gap 45° upper right). Ring always Mint (dark) / Deep Mint→Mint (light), never the accent. Header may show 3 ring states: ready, needs you (Apricot dot in the gap), offline (Dusk). Min 16dp tall; clear space = ring width. SVGs are outlined (no font needed); PNGs at 1600 px. In app copy write “halo” lowercase.
**halo FX (`tokens.json → fx`)**: Aura (presence), Trace (Guide), Edge glow (acting on phone only), Shimmer (thinking/loading), Bloom (success, once), Ripple (mic level). One light at a time. Reduced motion = static glow.
**Guide (`tokens.json → guide`)**: accent light marks the single best next step (next-step card, traced action, step path, halo tip). Apricot = needs you. Red = broke. Never on cloud opt-in, purchases, new permissions or destructive actions. Always says why + offers “Not now”. Tip types cool down 7 days after dismissal.
**2027 polish**: floating glass nav pill (64dp, 12dp margin), cards radius 20, sheets 26, full-round buttons/chips, title Sora 700 19–22sp, Light + Dark from the same parts (see 05, 11, 12).
**New/missing screens designed**: Updates (notifications), reply actions (Listen / Copy / Save to Notebook + “answered on this phone”), halo acting overlay with edge glow, Intelligence from the real screen (your conversation, next step, Providers, Model library, Model roles, Context and privacy, Vision lab, Audio test, System check), Notebook find & replace + blocks + floating format bar, Canvas “With halo” sheet, Study material step, Language picker, toasts, confirm dialog, field error/focus, empty, skeleton, banners.
**Kids 2.0**: home with 3 choices (Ask / Learn / Create), Adventures path, Read-along with word highlight, Bloom celebrations + stickers (no streaks, no leaderboards), PIN-locked Grown-up space (weekly summary, daily time, cloud off by default, camera asks a grown-up).
**Language**: confident, welcoming, transparent. `copy/halo_strings_en.json → voice.rewrites` maps today’s strings to new ones; plus `guide`, `home_v2`, `kids_v2`, `components`.

## 16. halo FX motion (v0.9) — `fx/`
`fx/lottie/` 16 Lottie files (30 fps, pure shape layers, no images/expressions/effects), dark + light:
| File | Size | Frames | Play | Use |
|---|---|---|---|---|
| halo_fx_trace_pill | 344×80 (button 320×56 + 12 margin) | 96 | once (72f lap + rest) | Guide on a primary button |
| halo_fx_trace_card | 352×144 (card 328×120, r20) | 96 | once | Guide on a next-step card |
| halo_fx_edge_glow | 412×915 (r44) | 150 | loop | halo acting on the phone; fade view alpha in 250 ms / out 150 ms |
| halo_fx_bloom (+ _kids) | 240×240 | 30 | once | SUCCESS; light haptic |
| halo_fx_aura | 240×240 | 120 | loop | presence behind the orb / hero |
| halo_fx_ripple | 240×240 | 48 | loop | listening; scale speed with mic level |
| halo_fx_shimmer | 320×96 | 48 | loop | skeleton / thinking |
Layer names: `ring`, `glow`, `glow2`, `bloom1-3`, `flash`, `aura`, `ripple1-3`, `bars`, `shine`. Recolour for accent themes via KeyPath (e.g. `**/ring/**` gradient stroke) using HaloPalette ringStart/ringEnd. Trace and edge glow assume their canvas size; for any other size use `HaloFx.kt` (size-independent).
`fx/static/` reduced-motion SVG for each file (trace at rest, bloom mid, aura mid-breath…).
`fx/HaloFx.kt` Compose reference: `Modifier.haloTrace()`, `Modifier.haloEdgeGlow()`, `Modifier.haloAura()`, `Modifier.haloShimmer()`, `HaloBloom()`, `HaloRipple()`, and `rememberHaloReducedMotion()`. Prefer these in production; Lottie files are for previews, onboarding and fixed-size spots.
`fx/halo-fx-preview.mp4` 8-second preview of all effects.

## 17. Motion v2 (v1.0): breathe first, rotate rarely
- **Edge glow is now a pulse** (`halo_fx_edge_glow_*`, 72f loop): no rotation; edge strokes breathe every 2.4 s plus a soft glow from the bottom (layer `floor`). Compose `haloEdgeGlow()` updated to match.
- **Guide default = pulse** (`halo_fx_trace_pulse_pill/card_*`, 72f loop). The one-lap `halo_fx_trace_*` is only for the first time a screen shows its next step (`haloTrace(lapOnAppear = true)`).
- **Rotation lives in THINKING only** (orb gap orbit).
- New moments (dark + light): `halo_fx_splash` (480×200, 54f, wordmark reveal from outlined Sora glyphs), `halo_fx_summon` (24f), `halo_fx_tool_{timer,search,open_app,camera,calendar,send}` (84f; markers `start` 0–12, `working` 12–60 loop while the tool runs, `done` 60–84), `halo_fx_remember` (60f), `halo_fx_cloud_handoff` (60f, play every time a turn uses the cloud), `halo_fx_device_connect` (360×200; markers `connecting` 0–48 loop, `connected` 48–72).
- Use markers with lottie-android: `setMinAndMaxFrame("working")` to loop while a tool runs, then play `done`.
- `fx/halo-flow-startup-tools.mp4` (1080×1920): splash → welcome (summon + Guide pulse) → tool use (timer) → acting (edge pulse with steps).
- `round2/13-motion-map.png`: every moment that earns motion, built vs next (pull-to-refresh ring draw, model download ring, draft draw-on, canvas arrange, accent sweep, shared orb, kids word glow).

## 18. Motion v2.1 (v1.1): the last seven moments
| Moment | Lottie (dark + light) | Compose | Notes |
|---|---|---|---|
| Pull to refresh | `halo_fx_pull_refresh` 120×120, markers pull 0–30 · release 30–40 · refreshing 40–88 (loop) · done 88–104 | `HaloPullRing(pullFraction, refreshing)` | scrub “pull” with pull distance |
| Model download | `halo_fx_download_ring` 240, frame = percent 0–100, marker complete 100–124 | `HaloProgressRing(progress)` | arrow → check + bloom at 100% |
| Draft draw-on | `halo_fx_draft_drawon` 352×144, 40f | `Modifier.haloDraftBorder()` | border stays dashed until Keep |
| Tidy the canvas | `halo_fx_canvas_arrange` 360×240, 48f | `haloGlideOffset(target, index)` | 100 ms stagger, 0.9 s, ease 0.2/0/0/1 |
| Accent sweep | `halo_fx_accent_sweep_{mint_to_blue,mint_to_lilac,mint_to_coral,lilac_to_mint}` 240, 30f | `HaloAccentSweepRing()` (any pair) | sweeps in from the gap |
| Shared orb | — | `sharedElement(key = "halo-orb")` snippet | orb keeps its state during the move |
| Kids read-along | — | `KidsReadAlong(words, currentWord)` | drive from TTS onRangeStart |
Preview: `fx/halo-fx-preview-v2-1.mp4`. Motion map (`round2/13-motion-map.png`) now shows all 20 moments built.

## 19. v1.2 — ready to implement
- `IMPLEMENTATION.md`: source of truth per area, what Round 2 replaces, build order, motion wiring table, acceptance checklist.
- `round2/` boards re-rendered: lowercase “halo” throughout, halo FX page updated to motion v2 (pulse, not rotation), motion map shows all 20 built.
- Strings: product name lowercased in all values (23 strings).
- Brand book v2.1: new contents page; chapters reordered (current screens and motion in 09–10); Round 1 screens moved to chapter 11 and stamped with the page that replaces them.
