# halo — implementation handoff (Developer Kit v1.4 · Brand Book v2.3)

Everything needed to build the current design. Where Round 1 and Round 2 disagree, **Round 2 wins**.
Brand book: chapters 09–10 are the screens and motion to build; 01–08 are foundations; 11 is history.

## 1. Source of truth

| Need | Use | Notes |
|---|---|---|
| Colours, 24 themes | `theme/HaloPalette.kt`, `theme/halo_themes.json` | Accent changes buttons, chips, links, progress, active tab, orb ring. Never the logo, wordmark or launcher. |
| Tokens | `tokens.json` → `color`, `orb`, `motion`, `fx`, `guide`, `wordmark`, `nav`, `radius` | |
| Wordmark | `wordmark/halo_wordmark_{dark,light,mono}.svg/png` | Lowercase “halo”, the “o” is the Open Ring. Outlined, no font needed. |
| Icons | `icons/android/*.xml` (140), `icons/svg/` | 24dp, 1.75 stroke, tint with `currentColor` / `android:tint`. |
| Presence orb | `orb/lottie/` (animated), `orb/svg/` (static + reduced motion), `orb/micro/` | 21 states × dark/light. Kids: `kids/`. |
| halo FX + motion | `fx/HaloFx.kt` (preferred), `fx/lottie/` (58 files), `fx/static/` (reduced motion) | See §4. |
| Screens | `round2/01–13.png` | Current design. Round 1 boards in `devices/`, `settings/`, `tools/`, `notebook/` are reference only. |
| Words | `copy/halo_strings_en.json` | English only. Includes `voice.rewrites` (old → new), `guide`, `home_v2`, `kids_v2`, `components`, `devices`, `settings`, `states`, `onboarding`. |
| Capability → ability mapping | `copy/capability_groups.json` | 32 raw capabilities → 7 user-facing abilities. |
| Run events → plain words | `copy/activity_event_labels.json` | Raw log stays behind “Technical log”. |

## 2. Replaced by Round 2 (don’t build the old version)

| Old | New |
|---|---|
| Capital “Halo” in UI copy | lowercase **halo** everywhere in the app (strings already updated) |
| Rotating edge glow | **Edge pulse** (breathes 2.4 s, no rotation) |
| Guide Trace looping laps | **Guide pulse** by default; one lap only on first appearance (`lapOnAppear = true`) |
| Approval button “Allow” | The action verb: “Send it”, “Open it”, “Set it” |
| Current step marked in Apricot | Current step = **accent glow**. Apricot means only “halo needs you” |
| Bottom nav bar (Round 1) | Floating glass nav pill (64dp, 12dp margin, radius 32) |
| Notebook / Tools / Devices / Settings Round 1 screens | `round2/05, 08, 09, 10, 11, 12` |
| Kids screens v1 | Kids 2.0 (`round2/06`) — Kids orb states (`kids/`) still valid |

## 3. Suggested build order

1. **Theme layer** — `HaloPalette.kt` + tokens (radius, nav, type: Sora headings, Atkinson Hyperlegible Next body). Light, Dark, System.
2. **Brand assets** — wordmark in the app bar, icons, launcher (`launcher/`).
3. **Presence orb** — one `HaloOrb(state, size)` composable backed by `orb/lottie` with static fallbacks. Sizes: Hero 160–240, Standard 80–120, Compact 40–64, Micro 16–32.
4. **halo FX** — drop in `fx/HaloFx.kt`; wire `rememberHaloReducedMotion()` to Halo’s own reduce-motion setting too.
5. **Components** — `round2/12`: buttons (primary / secondary / guided / text), chips, toasts with Undo/Open, confirm dialog, fields (error + focus), empty, skeleton + shimmer, banners (paused / problem), next-step card, halo tip, step path.
6. **Screens**, in this order: Home + reply actions + Updates (`05`) → approvals + acting overlay + Activity (`05`, `10`) → Intelligence (`08`) → Devices + pairing (`10`, `03`) → Settings, Context and privacy, Language, System check (`11`) → Notebook, Canvas, Study (`09`) → Kids 2.0 (`06`).
7. **Motion moments** — wire each from the motion map (`round2/13`), see §4.

## 4. Motion wiring

| Moment | Trigger | Implementation |
|---|---|---|
| Startup | app cold start | `halo_fx_splash_*` once (54f), then cross-fade to Home |
| Summon | side button / Talk | `halo_fx_summon_*` once (24f), then orb LISTENING |
| Listening | mic actually open | orb `capturing_voice` + `HaloRipple(level)` |
| Thinking | model working | orb THINKING (the only rotation) + `haloShimmer` on placeholders |
| Tool use | a tool runs | `halo_fx_tool_{timer,search,open_app,camera,calendar,send}_*`: play `start`, loop `working` until the tool returns, play `done` |
| Acting on phone | ACTING / ACTING_PHONE | `Modifier.haloEdgeGlow(active = true)` on the root + steps card; Stop always visible |
| Needs your OK | approval required | orb `approval_required` (Apricot dot breathes) |
| Done | success | `HaloBloom` / `halo_fx_bloom_*` once + light haptic |
| Remember | memory saved | `halo_fx_remember_*` once |
| Cloud | every turn that used the cloud | `halo_fx_cloud_handoff_*` once + cloud mark on the reply |
| Pair device | connecting / connected | `halo_fx_device_connect_*`: loop `connecting`, play `connected` |
| Next step | screen has a best next step | `Modifier.haloTrace(enabled = true)`; `lapOnAppear = true` the first time only |
| Pull to refresh | lists | `HaloPullRing(pullFraction, refreshing)` with Material3 PullToRefreshBox |
| Model download | download progress | `HaloProgressRing(progress)`; at 1f → check + bloom |
| halo draft | canvas card created by halo | `Modifier.haloDraftBorder(visible = true)`; dashed until Keep |
| Tidy canvas | “Tidy up the board” | `haloGlideOffset(target, index)` per card, then group breathes once |
| Accent change | Appearance | `HaloAccentSweepRing` in the “Your halo” preview |
| Screen to screen | Home ↔ Mini halo ↔ sheets | `sharedElement(key = "halo-orb")` (snippet in HaloFx.kt) |
| Kids read-along | TTS word boundaries | `KidsReadAlong(words, currentWord)` from `onRangeStart` |

lottie-android:
```kotlin
val comp by rememberLottieComposition(LottieCompositionSpec.Asset("halo_fx_tool_timer_dark.json"))
// loop "working" while the tool runs, then play "done"
LottieAnimation(comp, clipSpec = LottieClipSpec.Marker("working"), iterations = LottieConstants.IterateForever)
// accent recolour (gradient stroke on the ring layer):
val props = rememberLottieDynamicProperties(
    rememberLottieDynamicProperty(LottieProperty.GRADIENT_COLOR, arrayOf(ringStart.toArgb(), ringEnd.toArgb()), "ring", "**"))
```

## 5. Acceptance checklist

- [ ] One light at a time per screen (Aura or Guide). The orb doesn’t count.
- [ ] Only THINKING rotates. Everything else breathes, glides or fills.
- [ ] Reduced motion → every effect shows its static version.
- [ ] Apricot appears only when halo needs the user; red only for real errors; accent glow for “next” and “current”.
- [ ] Ripple only while the mic is open; edge pulse only while acting; cloud mark + handoff on every cloud turn.
- [ ] Cloud is never preselected; the Guide never points at cloud opt-in, purchases, new permissions or destructive actions.
- [ ] Every approval lists the exact steps and what it touches; the button names the action.
- [ ] Text ≥ 4.5:1 in all 24 themes (values in `halo_themes.json` already pass); touch targets ≥ 48dp (Kids ≥ 56dp, mic 72dp).
- [ ] Dates and times follow the app language, not the system locale.
- [ ] No raw IDs in UI (model hashes, MAC addresses, event names) outside “Details” / “Technical log”.
- [ ] Every state has a spoken label (`copy/halo_strings_en.json → states`).
- [ ] Kids: max three choices, parent lock on settings, cloud off by default, camera asks a grown-up.

## 6. Designed without screenshots — verify against the real app
Approval prompt, Voice, Privacy and Permissions inner screens, provider detail, Model library, Model roles detail, BuddyBot details. The pairing code step appears only if the device shows a code.


## 7. Learning — Kids + Study (new in v1.3)
Everything for the Kids + Study redesign lives in `learning/` and is specified in `learning/LEARNING_HANDOFF.md` (contract v1, 2026-10-18).
- **Implement against screen IDs** K00–K14, H20–H23, C30–C34, S40–S44, G90 and their events/states (`learning/spec/halo_learning_contract.json`), not against the look of a comp.
- **Motion:** 6 new Lotties `halo_fx_learn_{open,frame,insight,branch,advance,retry}` (+ static SVGs) drop into `assets/halo/fx/` with the existing naming; wiring + Intent policy in `learning/compose/HaloLearningFx.kt`.
- **Intent** = existing `Modifier.haloTrace(lapOnAppear = true)` on exactly one control, moved by real events (halo finished speaking → mic; 12 s/20 s hesitation → Hint; checked correct → Next).
- **Strings:** `learning/strings/` (EN filled; LV/RU/PL keys to translate; the app has no `values-pl` yet).
- **Prototype:** `learning/prototype/index.html` (serve the folder) — journeys A–D, every state, contract beside each screen.
- **Open gaps** are listed in the handoff §8 — decide them with Codex, don't guess.


## 8. Voice & Read everywhere (new in v1.4)
Spec: `voice/VOICE_READ_HANDOFF.md` + `voice/spec/halo_voice_read_contract.json`.
- **One field:** migrate every `OutlinedTextField` (113 fields in 39 surfaces, listed by file in §3 of the handoff) to `HaloTextField(kind = …)`, built on the existing `HaloVoiceTextActions`. Reference: `voice/compose/HaloTextField.reference.kt`.
- **Kinds:** TEXT · SEARCH · SHORT · NUMBER · CODE · URL_ID · SECRET (no mic, never read) · STRUCTURED ("Describe it").
- **Read aloud** on every content block (answers, notes, cards, whole canvas, notifications, plans, memories) + the shared read-aloud bar.
- **Voice approval:** only reversible actions; send/call/delete/pay/share/permission/cloud consent always need a tap.
- **Motion:** 9 new `halo_fx_*` Lotties (voice_field, dictation_insert, read_aloud, voice_wave, canvas_voice_add, kids_page_turn, kids_word_pop, listen_chapters, explain_back) in `voice/lottie/` → copy to `assets/halo/fx/`.
- **Kids + Study expanded:** K15 read-along, K16 read it myself, K17 pre-reader; S45 listen mode, S46 speak to answer, S47 explain it back (in the prototype as journeys F and G).
- **Gaps:** word timing from TTS, 15 s capture cap vs long dictation, media session, number words per language, Polish.
