# BrandBook 3.0 implementation sync — 21 October 2026

Base: `ebdebbfff83de434a80d339e7a5898d1aa92ceee`, synchronized with origin/main before editing. Authority: DevKit 2.0 `IMPLEMENTATION.md`, `USER_FACING.md`, the 95-design inventory, package state boards and reference images. This report supersedes the incomplete source-only checkpoint in `ANDROID-SOURCE-SYNC-2026-10-21.md`.

## Implemented

- C01/C02/AS02: durable app-wide and per-semantic-action eligibility, pending/error switch states, friendly action review, and invalidation of prepared operations. Resolution, authorization return, each fallback and dispatch marker recheck access. Native Assistant direct actions also check access and the discovery revision. Accessibility cannot bypass a disabled known action. Enabling eligibility does not supply execution approval.
- SC01–SC11: source-backed card preview before Add; reset confirmation and Undo; bounded EN/RU/LV text/voice layout proposals with explicit preview/apply, stale-preview rejection and unsupported recovery; user-selected topics; exhaustive routine-result state labels; last-success freshness and source sensitivity; stable detail identity; literal source text; persisted card configuration; calendar permission recheck after returning; background publication does not replace the user's layout Undo.
- D05: actual telemetry drives states. Long-press requests arming, pending requests suppress repeats and report unconfirmed timeout, and only confirmed armed telemetry enables steering, releasing stops, and leaving/backgrounding/fault/disconnection stops and disarms. Stop stays anchored. Missing telemetry remains unknown. Existing BuddyBot host owns connection/pairing and motion commands.
- P04: password-protected exports and scheduled folder backups, password stored through the existing Keystore-backed secret owner, encrypted archive verification, actionable missing-password state and seven-day local restore recovery. Failed password saves preserve the entered field. Restore/merge/relink/clear remain explicit existing owners.
- S05: Again / Almost / I knew it. Almost holds the current rung, resets streaks and never claims mastery. Room 26→27 retains old review events and stores the new outcome separately; the mastery model does not treat partial recall as a forgotten penalty. Failed recording leaves the revealed answer retryable.
- I03/I05/X02: everyday provider setup no longer reveals diagnostic summaries/protocol identifiers or links directly to role assignment. On-device states are readable and show the actual answer. Technical model controls live in the gated Local model lab. All lab destinations share the Lilac Lab boundary.
- AS01/AS04, NB02/NB05 and shared surfaces: localized assistant overlay with scrolling content and reachable final actions; shared mint primary actions in note/Canvas review and setup; wrapping/scrolling controls and original source content retained.
- EN/RU/LV: 3728 catalogue entries generated from `ui.tsv`. Polish stays coming soon.

## Scope boundaries

Weather and interest-article providers do not exist in the product source. Their designed setup/no-source states remain explicit; this change does not invent weather, articles, credentials or a successful provider connection. The AS06 lock-screen proposal is not implemented or claimed. Existing tile entry remains. Physical glasses, BuddyBot, audio and provider success require actual observations.

No builds, tests, renders or phone operations ran during implementation. Validation began only after the source work was complete. Current validation results are recorded below; source integration does not certify pixel parity.

## Validation

The initial compile and 27 semantic-action tests passed. The first Android host run passed 40/41; its older Advanced test lacked the existing Robolectric thermal-service shadow. After correcting the test setup, all 41 Android checks passed. Visual review then aligned the three recall choices to equal-width controls with a quiet filled middle choice. The first weighted enlarged capture hid a choice; the final layout stacks all three at 200% and its visibility/click check passes.

The connected-phone inventory was empty during validation; no installation or physical acceptance is claimed. Existing open issues #37 and #43 retain research/provider and physical journey acceptance requirements and remain open.

The APK is built from the working tree, including pre-existing glasses and local-model edits. Those edits are preserved separately and excluded from this sync's Git commit. Host results do not establish their hardware/provider acceptance.

## Final validation and artifact

- 27 runtime semantic-action tests, 41 app host tests and two Android app-function tests passed with zero failures/errors/skips. After the final enlarged recall-layout adjustment, all five targeted recall tests passed again.
- Debug APK assembly succeeded after that final change. Two localization-generator tests passed; the 3728-entry EN/RU/LV catalogue and Git whitespace checks passed.
- Thirteen host captures were inspected and saved in `docs/testing/artifacts/BRANDBOOK-SYNC-2026-10-21/`. These cover Settings/Advanced at 360/412dp and enlarged text, plus the recall component in light/dark/enlarged layouts; they do not certify every route on a phone.
- APK: `releases/checkpoints/halo-brandbook3-sync-2026-10-21-debug.apk`. SHA-256: `ba4be426020aa78fbd162cd402e09bf513a0faa7cd2193d1746fe9276906e89f`.
- Local logs: `C:/Users/slavd/HaloEco/brand-review/brandbook-sync-compile.log`, `brandbook-sync-delivery.log`, and `brandbook-sync-recall-final.log`.
- Phone installation and physical UI/model tests remain blocked because ADB reports no attached devices. No issue was closed on source/host evidence alone.

## State inventory

Every required state remains listed here for traceability. Entries identify production owners, not screenshot acceptance. Existing Home/Notebook/Kids/voice owners are retained; the changes above close the remaining implementation gaps in this sync. All physical screenshot acceptance remains separate.

| Design | Production owner | Required states |
|---|---|---|
| NAV01 | ui/shell/AppShell.kt · HaloNavigation | default  ·  active item  ·  hidden (editor/mini/kids/setup)  ·  rail ≥600dp  ·  200% text |
| NAV02 | AppShell.kt (56 routes) | adult  ·  kids  ·  advanced  ·  developer  ·  permission-unavailable |
| NAV03 | AppShell.navigateSafely · rememberKidsGrownUpGate | verify  ·  verified  ·  credential unavailable  ·  cancel |
| NAV04 | AppShell bottomBar · InteractionSurfaceRenderer | bar idle/playing  ·  approval pending from any tab  ·  return to setup |
| H01 | ui/screens/home/HomeScreen.kt | empty  ·  multi-turn  ·  earlier history  ·  edit/rerun draft  ·  auto-scroll |
| H02 | ui/screens/home/HomeComposer.kt | idle  ·  typing  ·  listening  ·  busy  ·  stop/barge-in  ·  IME open  ·  landscape  ·  disabled |
| H03 | HomeScreen.kt · ContextualCaptureSheet.kt | picker  ·  camera preview  ·  chips  ·  privacy scope  ·  vision model missing  ·  remove source |
| H04 | HomeScreen.kt | menu  ·  timer form  ·  open-app form  ·  new conversation  ·  clear confirm |
| H05 | HomeScreen.kt · interaction renderer | streaming  ·  tool use  ·  steps  ·  sources  ·  partial failure  ·  retry  ·  stopped  ·  refusal  ·  provider missing  ·  offline |
| H06 | DailyAssistantSection | one guided action  ·  see more  ·  dismissed  ·  empty |
| H07 | interaction/InteractionSurfaceRenderer.kt | request  ·  inspect plan  ·  approve (verb)  ·  not now  ·  running  ·  failed  ·  permission expired |
| A01 | ui/screens/activity/ActivityScreen.kt | All  ·  Needs you  ·  Running  ·  Done  ·  empty  ·  failed |
| A02 | ActivityScreen.kt (runId) | timeline  ·  receipts  ·  stop/resume/retry  ·  details collapsed |
| N01 | ui/screens/notifications/NotificationInboxScreen.kt | list  ·  filter/search  ·  digest  ·  empty  ·  no listener permission |
| N02 | ui/screens/notifications/NotificationDetailScreen.kt | read aloud  ·  reply  ·  confirm send  ·  sent-to-app  ·  expired  ·  dismiss |
| N03 | notification_settings route | allowed apps  ·  digest schedule  ·  listener off |
| NB01 | ui/screens/notebook/NotebookScreen.kt | hub  ·  library filters  ·  batch select  ·  empty  ·  Trash |
| NB02 | ui/screens/notebook/NoteEditorScreen.kt | edit  ·  toolbar  ·  find & replace  ·  embed  ·  source mode  ·  conflict  ·  autosave restore |
| NB03 | NoteEditorScreen.kt | ask  ·  draft side-by-side  ·  apply  ·  save as new  ·  regenerate  ·  stale draft |
| NB04 | ui/screens/notebook/CanvasEditorScreen.kt | empty  ·  pan/zoom  ·  select/multi  ·  connect  ·  tidy  ·  export |
| NB05 | CanvasEditorScreen.kt + dialogs | dashed draft  ·  keep/discard  ·  partial  ·  regenerate  ·  PDF page pick  ·  voice memo |
| NB06 | ui/screens/notebook/WorkspaceObjectDetailScreen.kt | image  ·  PDF  ·  media  ·  missing file  ·  relink  ·  send to Study |
| NB07 | ui/screens/notebook/MemoryHubScreen.kt | memories  ·  suggestions  ·  review before save  ·  correct  ·  forget  ·  stale |
| NB08 | ui/screens/notebook/ConversationsScreen.kt | list  ·  search  ·  continue in Home  ·  rename  ·  delete/recover  ·  empty |
| S01 | ui/screens/study/StudyHomeScreen.kt | continue  ·  due review  ·  new lesson  ·  empty |
| S02 | StudyHomeScreen.kt | goal  ·  material  ·  style  ·  options  ·  capability unavailable |
| S03 | ui/screens/study/StudyScreen.kt · LearningJourney.kt | 7 stages  ·  hint/example/practice  ·  resume  ·  stop |
| S04 | StudyScreen.kt · ExtractionReviewSheet.kt | material  ·  PDF pages  ·  failed extraction  ·  OCR confirm  ·  change source |
| S05 | StudyScreen.kt | front/back  ·  hint ladder  ·  reveal  ·  self-rating  ·  summary to Notebook |
| K01 | ui/screens/kids/KidsModeScreen.kt | Ask/Learn/Create  ·  resume  ·  gate  ·  safe exit |
| K02 | KidsModeScreen.kt · LearningJourney.kt | topic  ·  prior knowledge  ·  focus  ·  branches  ·  summary |
| K03 | Study extraction + Kids learning | camera consent  ·  preview  ·  unverified text  ·  one exercise  ·  saved |
| K04 | ui/screens/kids/KidsCreateFlow.kt | choose  ·  shape  ·  draft  ·  keep/change/discard  ·  share gated |
| K05 | ui/screens/kids/KidsStoryReader.kt | read-aloud  ·  read myself  ·  no-score  ·  resume page  ·  speech unavailable |
| O01 | ui/onboarding/HaloSetupScreen.kt | welcome  ·  privacy  ·  permissions  ·  provider  ·  download  ·  STT/VAD  ·  TTS audible test  ·  device  ·  readiness  ·  postpone |
| O02 | HaloSetupScreen.kt · on-device manager | candidates  ·  never auto-download  ·  language coverage  ·  hardware qualification |
| D01 | ui/screens/devices/DevicesScreen.kt | this phone  ·  connected  ·  connecting  ·  saved  ·  stale battery  ·  empty  ·  scan fail |
| D02 | DevicesScreen.kt · PairingScreen.kt | glasses  ·  BuddyBot  ·  headphones  ·  unsupported |
| D03 | ui/screens/discovery/PairingScreen.kt | scan  ·  choose  ·  link  ·  code (if asked)  ·  timeout  ·  bonded≠verified |
| D04 | ui/screens/devices/HeyCyanControlScreen.kt | ready vs connected  ·  capture  ·  media sync  ·  low storage  ·  battery/wear  ·  gestures  ·  forget |
| D05 | ui/screens/devices/BuddyBotControlScreen.kt | telemetry  ·  arm/lease  ·  joystick  ·  persistent STOP  ·  obstacle  ·  fault |
| D06 | ui/screens/settings/PermissionsScreen.kt · AndroidAssistantScreen.kt | explain  ·  request  ·  granted  ·  denied  ·  permanently denied  ·  restricted |
| D07 | SpeechOutputSettings.kt | phone  ·  headset  ·  glasses  ·  test route vs heard  ·  fallback |
| D08 | ui/mini/MiniHaloScreen.kt · HaloNearbyControls.kt · HaloNativeWidget.kt | mini  ·  floating orb  ·  widget  ·  overlay permission  ·  unsupported launcher |
| I01 | ai_settings | phone/network/cloud  ·  current model  ·  health  ·  next step |
| I02 | settings/intelligence/providers | by location  ·  not checked  ·  verified  ·  error |
| I03 | settings/intelligence/providers/{providerId} | key/endpoint  ·  validate  ·  test connection vs generation  ·  clear credential |
| I04 | settings/intelligence/models | search  ·  filters  ·  eligible  ·  unavailable |
| I05 | settings/intelligence/on-device | starter picks  ·  catalog  ·  import  ·  download/cancel/resume  ·  verified/unverified/corrupt  ·  remove |
| I06 | settings/intelligence/roles | CHAT/FAST/REASONING/AGENTIC/VISION  ·  inherit/auto/explicit/off  ·  unsupported |
| I07 | on-device + Notebook CTA (HALO-SEARCH-145) | not indexed  ·  indexing  ·  indexed  ·  reindex  ·  scope per source  ·  hardware not qualified |
| I08 | settings/intelligence/usage · images/{id} · policy | what leaves device  ·  image gen vs understanding  ·  usage  ·  consent |
| VO01 | voice_settings | listening  ·  dictation  ·  read aloud  ·  voice  ·  languages |
| VO02 | settings/voice/input | Whisper Small/Base  ·  VAD  ·  downloading  ·  mic test  ·  privacy |
| VO03 | settings/voice/output | Automatic  ·  Supertonic  ·  Kokoro  ·  Android  ·  cloud opt-in  ·  audible confirm |
| VO04 | SpeechOutputSettings.kt | download/import  ·  preview voice  ·  languages  ·  your test ≠ certification |
| VO05 | settings/voice/elevenlabs | key  ·  voice/style  ·  auth ≠ sample  ·  limits |
| VO06 | settings/voice/speech-lab · local_audio_lab | sample  ·  side-by-side  ·  timing honesty  ·  dev framing |
| P01 | settings/intelligence/context | per-category consent  ·  transient vs saved  ·  revoke/purge |
| P02 | permissions | explain→request  ·  denied  ·  permanent  ·  Android settings return |
| P03 | memory_learning | opt-in  ·  forget  ·  correct  ·  irreversible warning |
| P04 | storage_backup | folder  ·  encrypted export  ·  restore merge/replace  ·  conflicts  ·  relink  ·  clear caches |
| P05 | Settings inline page 'Appearance' | mode  ·  tone  ·  accent  ·  live preview dense  ·  contrast |
| P06 | LanguagePickerPage | picker  ·  search  ·  selected  ·  speech language separate  ·  200% |
| C01 | skills | available  ·  permission needed  ·  grants  ·  failure |
| C02 | ConnectedAppsScreen · AppActionAccessStore · SemanticAppActionBroker; custom servers in Advanced | servers  ·  add/edit  ·  secret  ·  refresh tools  ·  per-tool access  ·  connected≠approved |
| C03 | automation?routineId · automation_review/… | routines  ·  follow-ups  ·  history  ·  builder  ·  review  ·  run now |
| C04 | android_assistant | role  ·  default assistant  ·  blocked |
| C05 | Settings inline page 'Halo nearby' | mini  ·  floating  ·  widget  ·  permission |
| ST01 | ui/screens/settings/SettingsScreen.kt | status card  ·  5 groups  ·  Advanced row  ·  search  ·  search empty |
| ST02 | SettingsScreen.kt advanced | default  ·  confirm  ·  developer on |
| ST04 | CheckUpScreen.kt | checking  ·  one fix  ·  did you hear?  ·  all good |
| X01 | system_check technical half → Advanced | report preview  ·  share safe report |
| X02 | runtime_status · workload_diagnostics · assistant_diagnostics · vision_lab · local_audio_lab · sandboxes | dev visual language  ·  gated  ·  collapsed groups |
| XS01 | shared | loading  ·  skeleton  ·  offline  ·  unavailable  ·  undo toast  ·  validation  ·  out of storage  ·  expired token  ·  hw disconnect  ·  200% / keyboard / rotation |
| AS01 | assistant/HaloVoiceInteractionSession.kt · assistant/ui/AssistantSurface.kt | invoke  ·  listening  ·  understanding  ·  answer  ·  speaking |
| AS02 | HaloVoiceInteractionSession.attachNativeActions · appFunctionConsumer | offer  ·  approve  ·  running  ·  done |
| AS03 | onRequestConfirmation / onRequestPickOption (pauseForNativeChoice) | app-asks  ·  pick |
| AS04 | AssistantSurface error states | no-screen  ·  mic  ·  failed  ·  handoff |
| AS05 | ui/screens/settings/AndroidAssistantScreen.kt | active  ·  selected  ·  not-selected  ·  repair |
| AS06 | HaloTalkTileService; lock-screen proposal excluded | tile  ·  lockscreen |
| VA01 | ui/components/HaloReadAloudBar.kt | playing  ·  follow  ·  options  ·  route |
| VA02 | HaloVoiceTextActions · speech settings | mic-busy  ·  no-listening  ·  no-reading  ·  secret-field |
| VA03 | Compose semantics (traversalIndex, contentDescription) | home-order  ·  kids-order  ·  captions |
| SC01 | screen/HaloScreen.kt · LocalScreenStore · ScreenLayoutRequests · ScreenWindowPolicy | home-switch  ·  intro  ·  starter-ready |
| SC02 | screen/HaloScreen.kt · LocalScreenStore · ScreenLayoutRequests · ScreenWindowPolicy | top  ·  scrolled  ·  private  ·  offline |
| SC03 | screen/HaloScreen.kt · LocalScreenStore · ScreenLayoutRequests · ScreenWindowPolicy | editing  ·  resize  ·  removed |
| SC04 | screen/HaloScreen.kt · LocalScreenStore · ScreenLayoutRequests · ScreenWindowPolicy | picker  ·  preview |
| SC05 | screen/HaloScreen.kt · LocalScreenStore · ScreenLayoutRequests · ScreenWindowPolicy | detail  ·  article |
| SC06 | screen/HaloScreen.kt · LocalScreenStore · ScreenLayoutRequests · ScreenWindowPolicy | not-set-up  ·  ready  ·  updated  ·  failed |
| SC07 | screen/HaloScreen.kt · LocalScreenStore · ScreenLayoutRequests · ScreenWindowPolicy | feed  ·  no-source  ·  topics |
| SC08 | screen/HaloScreen.kt · LocalScreenStore · ScreenLayoutRequests · ScreenWindowPolicy | listening  ·  preview  ·  applied  ·  unsupported |
| SC09 | screen/HaloScreen.kt · LocalScreenStore · ScreenLayoutRequests · ScreenWindowPolicy | phone  ·  phone-land  ·  tablet-land  ·  tablet-port  ·  night |
| SC10 | screen/HaloScreen.kt · LocalScreenStore · ScreenLayoutRequests · ScreenWindowPolicy | settings  ·  reset |
| SC11 | screen/HaloScreen.kt · LocalScreenStore · ScreenLayoutRequests · ScreenWindowPolicy | states |
