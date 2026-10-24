# Interface 4.0 configuration discovery and disclosure

Integrated test execution and exact device/artifact results: [verification ledger](INTERFACE-4-VERIFICATION.md). Sub-batch source notes below retain their original evidence boundaries.

Integration source batch for preferences/profiles, native Screen layout,
saved device/notification, voice/learning/provider configuration, saved routine
management, active Study actions, speech inventory, Canvas review/media workflows
and the current-Canvas query.
Central executor/descriptor/HostTools wiring remains owned by the composition
root. This batch does not change the runtime authority model.

## Canonical families

`PersonalConfigurationToolAccess` declares exact known identities: four
preferences/profile tools, seven native Screen layout tools, nine saved-device
tools, five notification settings/delivery tools, two voice tools, two learning
tools, four provider tools, seven saved-routine tools, two active-Study tools
and two speech-inventory tools. Seven existing-owner Canvas workflow tools
join `WorkspaceToolAccess.canvasWorkflowTools` separately.
`halo_canvas_current` joins the existing Workspace read family: 52 exact
identities total. Unknown prefix matches are not
classified or made callable.

| Discovery owner | Tools |
|---|---|
| Existing `halo.assistant` | Preferences plus exact get/edit mode-profile operations |
| `halo.screen.layout` | Native state/preview/apply/settings/undo/preset operations |
| `halo.saved_devices` | Saved-device queries and guarded existing-owner actions |
| `halo.notification_settings` | App policy and digest delivery configuration |
| Existing `halo.notebook` (Workspace) | `halo_canvas_current`, alongside exact Canvas query/edit tools |
| `halo.voice.configuration` | Voice configuration query and approved edit |
| `halo.learning.configuration` | Learning/suggestion preference query and approved edit |
| `halo.provider.configuration` | Bounded provider readiness and approved existing checks |
| Existing `halo.automation` | Saved routine queries, revision-guarded edits/delete/run and run receipt |
| Existing `halo.study` | Attached lesson query and scoped native action dispatch |
| `halo.speech.inventory` | Existing pinned speech catalog and approved inventory actions |
| Existing `halo.notebook` (Workspace) | Canvas workflow status, palette/transcription review draft and guarded export/save/edit/discard |

Native Screen layout does not own or expose cross-app accessibility controls.
The existing cross-app skill and Screen-context consent remain distinct.
All added tools are activatable through discovery/skill loading; the local
bootstrap remains exactly discovery, skill loading and current time.
Disabling a domain's owning skill still denies its operations.

## Source policy and provenance

Profile/provider/model selections, topic text, preset names, saved-device
labels/IDs, app identities, delivery targets and current-Canvas identity are
personal metadata. They use the existing **WORKSPACE** remote data category.
`DefaultToolExposureGate` checks canonical Workspace-result identities as
well as semantic category, so a SYSTEM/DEVICE_CONTROL descriptor cannot
bypass `allowCloudWorkspace=false`.

The same identities are in `WorkspaceToolAccess.readTools`, which drives
`remoteToolResultCategories`: both queries and mutation receipts carry
TOOLS + WORKSPACE. Existing `CognitiveTurnExecutor` and recovered-history
`CognitiveTurnPreparer` consume this provenance for source policy; this is
not an instruction-only restriction. Workspace consent does not authorize
cross-app Screen observation, enable a skill, bypass operating mode or grant
OS permission. Source-specific content remains under its existing categories.

## Host support and guidance

The Android publisher advertises local adapter support for
`host.assistant.preferences`, `host.assistant.profiles`, `halo.devices.saved`
and `halo.notifications.settings`, plus `host.voice.configuration`,
`host.assistant.learning`, `host.assistant.providers`, `halo.routines.manage`
and `halo.study.active`, plus `host.speech.inventory` for the existing pinned
speech catalog. Support is not a selected Android assistant,
paired/connected/qualified hardware, granted notification access, delivered
digest or audible output.

Bundled instructions require exact observed source/revision tokens before
edits; stale/uncertain writes require a fresh decision and never automatic
replay. Existing durable approval remains mandatory. Profile edits do not
activate a mode, select the current provider, expand sharing without review,
grant paid consent or change OS permissions. Font guidance now matches the
existing schema's maximum of 2.0 while preserving Android accessibility scale.
Current Canvas metadata is attachment evidence, not proof of OS foreground or
rendering. Configuration, dispatch, observed physical effects and speech are
reported separately.

Central descriptors retain ALWAYS + NEVER_REPLAY for every added mutation.
Routine deletion is DESTRUCTIVE; run-now and provider refresh/check are
EXTERNAL_SIDE_EFFECT; other new writes are SENSITIVE. Voice output is capped
at 12,000 characters. Provider checks expose no secret/endpoints or generation
capability; active Study dispatch is not accepted learner work or generated
feedback. Existing routine constituent-action, tutor/provider, local-learning,
source and paid-consent gates are unchanged. The host approval copy allowlist
and reviewed EN/RU/LV/PL catalogue include all 26 additional domain titles;
arbitrary target text still stays literal.

Canvas transcription is local file-to-review-draft only, never automatic save,
microphone capture or cloud fallback. Status returns draft metadata without
bytes/transcript. Exact board/source/draft token/hash guards remain with the
existing editor bridge. Palette/save/export use their real canonical owners;
optional Study setup target remains NOT_DISPATCHED and needs the existing
workspace open owner for explicitly requested navigation. All six mutations remain SENSITIVE,
ALWAYS and NEVER_REPLAY. Speech inventory exposes only the pinned catalog;
install/resume/pause/remove/select retain owner, foreground/grown-up, resource
and grant guards. Remove additionally requires the returned artifactVersion
checked under the existing transfer lock, protecting a newer acquisition.
Installed/selected/requested never implies recognition or audible speech.

## Regression sources and evidence

- `PersonalConfigurationPolicyTest`: remote consent despite SYSTEM category;
  separate native/cross-app Screen consent; result provenance for all 52
  identities; bootstrap bound; operating-mode and available-tool policy.
- `InterfaceConfigurationDiscoveryTest`: unique native ownership, readonly
  current/profile queries, skill loading/disabled-owner behavior and the
  mode-profile/font/current-Canvas guidance.
- `AndroidCapabilityPublisherTest`: all ten local configuration adapters are
  advertised independently of physical/assistant-role readiness.
- `CompleteConfigurationDescriptorTest`: nineteen actual Voice/Learning/Provider/
  active-Study/Canvas-workflow/speech-inventory definition factories are checked
  against central descriptors and registered operability guards, capabilities and approval.
  Existing RoutineManagementTools tests review its seven actual descriptors.

Build/test execution: **NOT_RUN by this batch owner**. Root integration must
record actual registration/operability review, focused regressions and APK
identity. Native UI, actual spoken recognition, permitted provider dispatch,
physical devices and durable approval acceptance remain separate evidence.
