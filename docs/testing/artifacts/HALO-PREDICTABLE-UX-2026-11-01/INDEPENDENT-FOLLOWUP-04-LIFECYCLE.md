# Independent post-fix source review

Status: HISTORICAL REPORT — independent observations at the source snapshot described below.

Date: 1 November 2026. Read-only review of fixes following `INDEPENDENT-FOLLOWUP-03-GUIDANCE.md`, with the same two isolated assessors. Root is actively editing; this records the inspected snapshot rather than freezing subsequent work. No implementation edits, test execution or device actions by the reviewers.

## Remaining findings at review time

Paths are relative to `app/src/main/java/com/halo/app/`.

| ID / priority | Evidence, trigger and correction |
|---|---|
| E07 / **P2** | **Retained-owner recreation can strand a picker admission before Android is launched.** `ui/workspace/WorkspaceExternalExportUi.kt:139` persists busy PICKER through `owner.picking()` before setting a remember-only `launchRequested`. The effect at `:73–84` launches later. Rotate/dispose between admission and launch, including cancellation on the IO-to-main return: the singleton owner retains PICKER, `load()` is already complete, the replacement host has no launch request, and no system result can arrive. Close and Choose remain disabled. Distinguish admission from completed system handoff or reconcile abandoned admission when attaching a new host, without automatically replaying external work. Test retained-owner recreation between admission and actual launch. |
| G01 continuation / **P2** | **Static requested guidance loses the next step after its first action.** `ui/presence/HaloZone5OrbLauncher.kt:83–85` closes Help before every action, and its Help binding at `:280–281` uses that callback. In compact layout or with guide lights off: Help → Guide me → Study Continue advances setup, closes Help, and leaves no next-step presentation because `ui/shell/HaloZone.kt:82–85` suppresses those cues. Studio Add layer → Apply similarly advances to Save while Help remains closed. Retain Help for safe in-place actions, or suspend and resume it after an inspector completes while the same guided owner/entry is active. Explicit pause/exit must cancel resumption. |
| N01 / **P3** | **New Workspace disclosure has no accessible expansion state.** `HaloZone5OrbLauncher.kt:257–261` keeps the label unchanged and only changes its decorative arrow; `ui/zone5/HaloZone5Presentation.kt:111–112` sets icon descriptions to null. Add localized expanded/collapsed state semantics so assistive users can identify the group's current state. Device TalkBack behavior remains a later test. |

## Confirmed source closure

- E01: immutable per-token ActivityResultRegistry keys now bind callbacks to the original attempt. Added registry tests exercise queued restoration while another token is registered. No remaining mutable-token relabel was found.
- E04: invalid-result journal failure now has a nonbusy in-memory terminal fallback preserving earlier destination uncertainty.
- G03: Studio and Notes retain export completion by source revision independently of output-dialog visibility. Later source revisions do not inherit that completion.
- G04/G05: unavailable Help actions expose owner reasons and keep the explanation present; file-tool Help is now format neutral.
- G02: Study projects its actual shared Continue/Start callback and current-step wording; a loaded lesson publishes a completed setup step.
- G01 initial/relocated target: compact or guide-light-off Guide me keeps Help open. A subsequent edit during review added actual target visibility to the launcher and `!menusInSheet` to cue eligibility, resolving the relocated-in-Actions invisible cue. The action-to-next-step continuation above remains separate.
- Library now observes late saved-view binding and waits for current query/filter/sort/collection state before consuming a return identity.
- The adult Workspace instrument group starts collapsed; Home/Screen purpose labels and local action groups are present. The reported one-lap/static guidance and task/help-over-ambient precedence changes showed no additional concrete source defect in this bounded pass.

## Evidence boundary

The latest previously completed focused result remains 95 tests / 14 suites / zero failures/errors. The broader host run was active on a preceding snapshot when inspected; newly added tests had not run. This report does not promote source inspection or pending tests to passing evidence. Matching native renders and the completed host results are required for the final expert rubric review. No revised score is assigned here, and later participant/device/assistive acceptance remains separate under the original rubric.

## Subsequent source pass while the final run compiled

Root added retained-owner unlaunched-picker recovery, localized Workspace expanded/collapsed semantics, a measured horizontal 56dp Kids strip, and retained/resumable compact Help. E07 and N01 now address the reported source mechanisms; Kids strip inspection found no additional authority or target-size issue. `broad-host-results.json` records the preceding snapshot as 2,388 tests / 501 suites / one Kids-strip layout failure / zero errors. It does not establish the subsequent fix's result.

The new compact Continue test covers synchronous owner changes, but two parts of the same G01 continuation remained at this inspection:

- `HaloZone5OrbLauncher.kt:96` required `owner != guidedActionOwner`. Studio Help → Guide me → Add layer suspends Help; cancelling the chooser (`StudioScreen.kt:310`) restores the identical pre-action owner without a domain mutation, so the resume gate never runs. Track observed inspector suspension separately to permit return to that same owner. Explicit Help Close/Pause/Exit must still prevent resumption.
- The resume effect at `HaloZone5OrbLauncher.kt:94–105` queried lifecycle state without observing it as an effect key. An async inspector that finished while STOPPED could not reopen Help, and RESUMED alone did not trigger another evaluation. Observe resume state while retaining the current owner/session fence.

These were sent to root for correction. Matching final tests, inspector cancellation/background checks, and native captures were still pending; no score update was made.
