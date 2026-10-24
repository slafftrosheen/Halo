# Interface 4.0 motion catalogue

Integrated test execution and exact device/artifact results: [verification ledger](INTERFACE-4-VERIFICATION.md). Sub-batch source notes below retain their original evidence boundaries.

Source audit: 2026-10-24. Authority: `docs/15-brand/v4/IMPLEMENTATION.md`,
`USER_FACING.md`, supplied `orb/`, `fx/` and package artwork. This is an
implementation inventory, not visual or physical acceptance.

## Packaged inventory

`app/src/main/assets/halo/` contains **166 Lottie JSON animations**:
60 orb files (15 states × adult/Kids × dark/light) and 106 effect files
(53 families × dark/light). The additional JSON at `artwork/manifest.json`
is an artwork manifest and must not be parsed as an animation. There are
404 SVGs: 96 effect stills and 308 artwork SVGs. The presence renderer uses
generated Android vector resources, including micro and extended states.
No animation was generated, replaced or recoloured for this batch. Four
previously unpackaged read-aloud/explain-back stills were copied byte-for-byte
from the supplied `voice/lottie/static/` directory into the production FX
static directory after the first regression gate identified the missing paths.

Only these production components load/play Lottie:

| Loader | Asset and clipping | Static/loading/failure fallback |
|---|---|---|
| `HaloSignalFace` | `halo/halo_{kids_}orb_<state>_<theme>.json`, full clip | Same-state Android vector; micro vector at ≤32dp |
| `HaloFxAnimation` | `halo/fx/halo_fx_<moment>_<theme>.json`, full clip | Supplied same-family SVG, semantic glyph while SVG loads/fails |
| `StagedHaloFx` | Same FX path, explicit marker | Supplied same-family SVG, semantic glyph while SVG loads/fails |
| `HaloPackageFx` | Same FX path, package marker | Explicit package still SVG, semantic glyph while SVG loads/fails |

Dark/light selection uses the actual theme background luminance. Presence
uses mint identity artwork regardless of the selected appearance accent.
Attention/error signals remain the supplied semantic artwork.

## Presence state mapping

| Artwork | Full-size playback | Source truth / compact policy |
|---|---|---|
| idle | Loop | Canonical idle; static at compact size, with no idle frame clock |
| listening | Loop | Actual listening presentation; compact stays static |
| capturing_voice | Loop | Canonical capturing; compact stays static |
| capturing_vision | Loop | Explicit vision presentation; compact stays static |
| thinking | Loop | Canonical thinking; quiet vector pulse at 40–79dp |
| acting | Loop | Canonical acting; quiet vector pulse at 40–79dp |
| speaking | Loop | Actual playback presentation; compact stays static |
| streaming_text | Loop | Text streaming without falsely claiming audio playback |
| waiting | Loop | Canonical waiting; quiet vector pulse at 40–79dp |
| approval_required | Loop | Actual approval presentation; quiet vector pulse at 40–79dp |
| success | Once, hold | Actual success; compact stays static |
| warning | Once, hold | Actual warning; compact stays static |
| error / offline / stopped | Bundled but playback disabled | Same-state vectors; no decorative looping |

Extended artwork (`acting_phone`, `planning`, `model_loading`, permission,
context and other condition states) uses static vectors, not fabricated
Lottie mappings. Full Lottie begins at 80dp. Below 40dp it is always static.
`PresenceArtworkContractTest` verifies all mapped vectors and full-motion
variants; it also checks streaming versus actual speaking projection.

## Active effects and their event owners

| Family / marker | Production owner and trigger |
|---|---|
| splash | `MainActivity`, initial launch surface |
| summon | `HomeVoiceSummonFx`, closed-to-open microphone transition; restoration does not summon |
| bloom | `OnDeviceModelsScreen`, completed setup result |
| remember | `MemoryHubScreen`, memory result surface |
| cloud_handoff | `HomeCloudHandoffFx`, actual reasoning provider classified REMOTE; configured, unknown and LAN providers do not qualify |
| draft_drawon / canvas_arrange | `CanvasEditorScreen`, draft/arrangement result surfaces |
| learn_open | `LearningJourneyPanel`, actual session, keyed by session ID |
| learn_branch: grow / choose | `LearningTopicMap`, branch availability/choice |
| explain_back: compare | `LearningTopicMap`, actual review, keyed by review |
| read_aloud: reading | `HaloReadAloudBar`, `playing`; preparing/paused uses a static speech glyph |
| tool_timer/search/open_app/calendar/send: start → working | `HomeToolExecutionFx`, actual `ExecutingTool` ID; start-to-working is decorative timing, never a completion receipt |
| device_connect: connecting / connected | `PairingScreen`, pairing next-action projection; connected is not general device readiness |
| task_step: running / done | `LiveTaskPill`, actual task state; `ActivityScreen` historical done explicitly disables animation |
| approval_breathe: needs_you | `InteractionSurfaceRenderer`, actual approval |
| receipt: confirmed / unconfirmed | `HomeToolResultFx`, persisted tool outcome and `ActionTruth`; dispatch alone cannot confirm |
| stop_cancel: stop | `LiveTaskPill`, real Stop epoch |
| assist_invoke: invoke | `AssistantSurface`, actual assistant invocation |
| apply_draft: apply | `NoteEditorScreen` through `HaloEventMoment`, incremented applied-draft event |
| memory_forget: forget | `WorkspaceObjectDetailScreen`, forget result |
| kids_gate: lock / unlock | `KidsGrownUpGate`, actual gate result |
| card_update: update | `HaloScreen`, changed card |

`HaloFxTool.CAMERA` is supported by the wrapper but deliberately has no Home
execution mapping until a camera action has a real execution ID. Unmapped
phone actions use a truthful text/static progress card. Animation availability
does not imply callable-tool availability.

`HaloFxMoment.LEARN_INSIGHT`, `LEARN_ADVANCE`, `LEARN_RETRY` and `BLOOM_KIDS`
are available wrapper cases without a current production call site.

## All bundled FX families

Every family below has one dark and one light JSON. “Library” means bundled
but not directly requested by a current production Lottie load site. Several
have equivalent Compose effects; bundling does not imply runtime playback.

| Use | Families |
|---|---|
| Current production Lottie | apply_draft, approval_breathe, assist_invoke, bloom, canvas_arrange, card_update, cloud_handoff, device_connect, draft_drawon, explain_back, kids_gate, learn_branch, learn_open, memory_forget, read_aloud, receipt, remember, splash, stop_cancel, summon, task_step, tool_calendar, tool_open_app, tool_search, tool_send, tool_timer |
| Wrapper available, currently unused | bloom_kids, learn_advance, learn_insight, learn_retry, tool_camera |
| Library / Compose equivalent | accent_sweep_lilac_to_mint, accent_sweep_mint_to_blue, accent_sweep_mint_to_coral, accent_sweep_mint_to_lilac, aura, canvas_voice_add, dictation_insert, download_ring, edge_glow, kids_page_turn, kids_word_pop, learn_frame, listen_chapters, pull_refresh, ripple, shimmer, trace_card, trace_pill, trace_pulse_card, trace_pulse_pill, voice_field, voice_wave |

## Motion, lifecycle and Stop

- Reduced motion combines the observed system/appearance preference with
  battery saver, screen off and moderate-or-higher thermal pressure. A missing
  thermal service safely constrains decorative motion.
- Lottie compositions remain mounted on lifecycle pause, stop playing below
  STARTED and use `restartOnPlay = false`. This preserves one-shot position
  across a normal pause/resume. Reduced-motion changes remove Lottie loading
  and show stills. Re-enabling motion can load a new composition.
- This batch keys package playback by asset and marker, matching staged FX:
  a changed marker gets its own playback owner; lifecycle changes do not
  remount it. This matters for task, receipt and Kids gate phase changes.
- Aura, edge, shimmer and refresh breathing now remove their infinite frame
  clocks below STARTED. Existing trace/state aura/assistant edge already
  observe lifecycle. Disabled guide lighting now also suppresses state-aura
  and assistant-edge clocks. No progress state is inferred from elapsed time.
- Success light now maps to `HaloLight.SUCCESS` (stationary), rather than
  falling through to idle breathing. Stop remains OFF.
- `HaloMotionEvents` records Stop epochs and bounded cancelled interaction
  IDs. `HaloEventMoment` suppresses arrival/restoration replay and dismisses
  an active moment on Stop. Learning moments observe `VoiceFieldCancellation`.
  Tool receipts use their existing owner and Stop filtering. No runtime,
  provider, action permission or confirmation policy changed.

## Regression and acceptance evidence

Added/updated source regressions (execution belongs to the shared integration gate):

Executed local source inspection: all 166 animation JSONs have layers, a
positive frame rate/duration and no referenced bitmap assets; all 96 effect
SVGs parse as XML with SVG roots and viewBoxes. Result: no structural
failures. This inspection does not execute the Lottie playback parser.

First integration gate: production and unit-test compilation passed; 193 of
195 selected app regressions passed. Two failures identified the missing
read-aloud/explain-back supplied stills and duplicated logical FX names in
Robolectric's merged asset listing. The four supplied stills are now packaged,
and the inventory restores path deduplication before counting/parsing. Their
recovery gate is **NOT_RUN** at the time of this update; do not interpret the
source fixes as a final passing result. The root integration report records
later execution and the separate native APK/device evidence.

The subsequent full gate executed 1,864 app tests with 22 failures. One was
the inventory test admitting `halo/artwork/manifest.json`: Robolectric can
return nested relative names from `assets.list("halo")`. The test now
restricts the deduplicated inventory to canonical orb and FX animation paths,
retaining the 60/106 counts and actual parser/layer checks. This is a test
inventory correction, not evidence of a broken production animation. The
other full-gate failures remain with their existing owners and are not fixed
or reclassified by this motion update. Recovery of this final inventory
correction is **NOT_RUN** here.

- `PresenceArtworkContractTest`: real `LottieCompositionFactory` parsing of
  all 166 shipped animations; dimensions/state variants, nonempty layers,
  positive duration, no image dependency; excludes the non-Lottie manifest.
- `HaloMotionAssetContractTest`: real Lottie parser checks for every marker
  used by staged/package APIs, positive and in-bounds clip ranges, both themes;
  XML parser checks for all referenced effect stills, viewBoxes and drawable
  content, without external image dependencies.
- `HaloMotionLifecycleTest`: a mounted Compose surface observes lifecycle
  transitions through paused, resumed and destroyed states.
- `HaloLightStateContractTest`: exhaustive presentation-to-light mapping;
  success and Stop cannot retain an idle loop.
- Existing `HaloStaticFxAssetsTest`, `HaloFxFallbackTest`,
  `HaloSharedOrbMotionTest`, `HomeToolExecutionFxTest`,
  `HomeToolResultFxTest`, `HomeCloudHandoffFxTest` and presence identity tests
  cover complementary fallback, truth and event contracts where present.

**NOT_RUN in this bounded audit:** recovery Gradle compilation/tests; screenshot comparison;
real-device dark/light at 360/412dp and 200% text; rendered Lottie versus
still parity; native assistant lifecycle; power/thermal transitions; real
spoken recognition; external action/provider receipts. The integration gate
must record its actual results separately. JSON structure inspection is not
a substitute for Lottie's parser tests or rendered acceptance.

Known acceptance gap: the supplied reduced-motion Kids gate plate is
`kids_gate_locked` for both lock and unlock wrapper cases. The surrounding
gate text owns the result; verify the unlocked static presentation with the
brand reference before visual acceptance. No invented unlocked asset added.

## Route/action parity boundary

This motion batch introduces no route or callable operation. Existing event
owners and domain actions remain authoritative. Read-only inspection found
`ToolOperabilityReview` and registered-descriptor review tests for workspace,
notes, study, attachments, purge, ink and local model setup. Those declarations
are source evidence only; this audit did not execute the registered-feature
gate or establish voice/device acceptance. Do not treat the FX family list,
route navigation, a configured skill, or the reserved camera clip as proof
of functional tool parity. The full route/action matrix belongs to the parent
Interface 4.0 audit, not this bounded motion inventory.

## Native launcher fixture

`app/src/androidTest/java/com/halo/app/ui/presence/InterfaceFourDeviceTest.kt`
renders the actual launcher, SignalFace and theme inside an isolated native
Compose fixture. Six tests exercise touch tap, long press, swipe up, the
custom accessibility action, 200% text with disjoint orb/menu/privacy targets,
and lifecycle dismissal plus suppression of stale/background callbacks.
English is selected using the production language provider and private
memory-only storage; the user's saved language and settings are unchanged.

The leading-slot privacy button only increments a fixture counter. This
checks layout/activation separation, not the durable privacy control or its
policy, Kids gates or Full Power consent. `LocalInspectionMode` excludes the
live speech host. This fixture does not qualify AppShell, recognition,
microphone permission, providers, actual speech or the native assistant.
No MainActivity smoke is included because setup/user-state-dependent launch
would extend beyond this isolated test's ownership. The integration owner
reported all **six native fixture cases PASS** after the full gate. This
result covers the isolated rendered launcher fixture described above; it does
not establish AppShell, durable privacy policy, real speech or provider acceptance.

After the integration owner builds and installs the matching debug APK and
test APK, run only this fixture on the explicitly selected phone:

```powershell
adb -s SERIAL shell am instrument -w -e class com.halo.app.ui.presence.InterfaceFourDeviceTest com.aistudio.braintool.xkqzps.test/androidx.test.runner.AndroidJUnitRunner
```

## Physical idle redraw recovery

A 30-second foreground sample prompted a per-thread comparison on the actual phone. Checkpoint 10 Workspace measured main/render activity at about 46%/35% of one CPU core; background and Android reduced-motion samples were near zero. These are short CPU observations, not battery or leak qualification. The compact idle renderer now uses its supplied static vector; active compact states and full-size Lottie keep their existing policies. Checkpoint 11 retained redraws, revealing a separate actually visible 32dp approval Lottie in the global task pill. Checkpoint 12 replaces that decorative second Halo with a static status icon while preserving Review/Stop and task truth. The final short sample measured main/render activity at about 20%/17% of one core while a WorkManager thread was active at72%; this is not a controlled idle, battery or sustained-load PASS. See the integrated verification ledger and sample JSON for exact artifacts and limits. Android animator scale was originally unset and restored to unset after the comparison.
