# PHONE-CONTROL-005 — sequenced Android operations

Status: second hardening pass committed to `main`; Gradle/device validation pending. Scope: Android host only.

## What changed

- Plain native commands remain model-independent and use `NativeCapabilityDispatcher` → `SkillExecutionService` → `ToolTurnExecutor`. STT supplies text, never authority.
- `AppCommandParser` checks bounded scripts **before** generic app launches. Otherwise "open Gmail then tap Search" is mistakenly treated as a single installed application name.
- Explicit spoken screen actions `find <label> on screen`, `tap <label>`, `type <value> into <label>` are represented as typed intents. There are **no coordinate commands**, screenshots interpreted as authorization, or remembered UI tokens.
- The Android `NativeScreenTargetResolver` obtains a new accessibility snapshot for each step, verifies the foreground package after launch, refuses blocked or incomplete screens, and requires exactly **one enabled, nonsensitive, exact-label match**. It then prepares an existing `halo_find_on_screen`, `halo_ui_click` or `halo_ui_set_text` call.
- The existing tool-turn authorization, approvals, preflight, exact-generation token validation, action receipts and durable journal execute the action. No direct-user-authority shortcut for UI mutations.
- A scripted sequence stops at a failed, rejected, unknown or unconfirmed UI action. If a UI action requires approval, the remaining explicit steps are held **in-process** and can continue only after the approved step has actually completed with a confirmed effect. An unapproved or cancelled action never advances the sequence. The hold expires after five minutes and is not automatically restored after process death. A launch being **dispatched** allows a subsequent fresh foreground check; launch alone is not considered proof the app is open.
- Unresolved in-app goals such as "open Gmail and find invoices" are **not** downgraded into `OpenIntent` or web search. They enter the agentic tool path.
- Explicit screen workflow turns seed Android semantic tools at model preparation using the same skill-availability, app policy, current cognitive mode, host and local-model allowlist. There is no vendor-specific phone executor.

## PHONE-CONTROL-006 hardening notes

- **Trust and privacy:** UI tools may return the entire untrusted accessibility projection. Native screen responses now use trusted fixed status messages instead of tool-output text (including text entered into a field). Source screen content is not spoken back or treated as a command.
- **Cloud consent:** `DefaultToolExposureGate` rejects all accessibility tools for remote models unless `allowCloudScreenContext` is explicitly enabled. This is enforced during initial assembly **and again** on every subsequent tool-exposure round. Native commands remain provider-free. A remote model is not allowed to work around the cloud screen policy by using an alternate tool.
- **Provider behavior:** an explicitly seeded tool set no longer gets overwritten by generic discover-first heuristics for remote models. A common observe → uniquely identify → act → verify instruction applies to every compatible tool-capable provider.
- **Approval state:** `NativeContinueIntent` cannot impersonate approval of a pending operation. Durable `InputResolved(Approved)` evidence is required. After approval, a different next native step uses its **own** run ID for output/continuity.
- **Cancellation:** Stop invalidates pending sequence checkpoints and cancels all outstanding native runs (without terminating scheduled routines). An in-flight Android dispatch cannot always be undone.
- **Launch transitions:** the app controller retries a fresh foreground check for roughly three seconds; no wrong-package UI action is allowed while switching activities.
- **Host dispatch truth:** navigation and web tools now check the actual Android `Outcome` rather than returning a success after host failure. Web search falls back from missing `ACTION_WEB_SEARCH` handler to an encoded HTTPS search URL if a browser exists. Browser links require absolute HTTP(S) URLs.
- **Media ambiguity:** if multiple Android media sessions exist, only a uniquely playing session is selected automatically; otherwise name the app. Halo no longer operates the first arbitrary session.
- **Brightness verification:** the exact 1–255 Android brightness register is compared to the written value, avoiding false failures from percent-rounding.
- **Added regression coverage:** protected cloud exposure and revocation, scoped discover-first, UI result redaction, cancellation of multiple native runs, spoken parsing, missing Android intent handlers and media ambiguity.

The tools execute with Android permissions, not with arbitrary system privileges. Permission dialogs, restricted applications and purchases still require direct user takeover. Cross-process recovery of the remaining steps of a script remains intentionally unsupported until a durable workflow-level checkpoint/confirmation contract is implemented.

## PHONE-CONTROL-007 — model-independent interaction and mode integrity

- Explicit scrolling is now a native intent: `scroll down`, `scroll up`, and the same steps in bounded scripts such as `open Gmail then scroll down then tap Compose`. The Android adapter obtains a new screen snapshot, requires exactly one enabled unprotected scrollable node, and sends its snapshot-scoped token to `halo_ui_scroll` through ordinary approval, action truth and postconditions. Nested or multiple scroll surfaces trigger user takeover rather than a guessed swipe.
- Accessibility field lookup now considers exact `labelHint`, content description, resource ID and non-editable text, with normalized whitespace and case. The readable field value is **not** used as a target label; this is especially important in editable forms.
- `UiTargetSearch` includes editable hints in its score, but text inside editable controls is still excluded from matching. Search tests cover an explicit Search hint, a private entered value and a partial decorative label.
- Provider tool re-exposure carries the runtime's real operating mode in `PreparedCognitiveTurn.toolExposureMode`. It no longer trusts a free-form turn metadata value that could silently default to AUTO. The same Ask/Plan/Agent restrictions apply to subsequently exposed tools.
- Remote semantic app capability discovery, execution and dynamically registered `halo_appfn_*` functions now require the user's `allowCloudScreenContext` opt-in, just as accessibility tools do. The model is never entitled to screen-derived app context merely because a separate semantic route exists.
- On-demand native screen interactions do not require an LLM. App-specific goals requiring inference still need a tool-capable provider and explicit consent for remote screen context.

**Validation gate:** The new Kotlin and Android tests are committed but not executed in this environment (no Gradle/Android SDK or device). Run `./gradlew :libraries:halo-cognition-language:test :runtime:cognition:test :app:testDebugUnitTest` from the actual checkout, then perform the real-device acceptance below. Do not treat the GitHub push as a green build.

## PHONE-CONTROL-008 — script checkpointing and exact observation

### Implementation
- A scripted command now creates one opaque workflow ID. Each actually started step records a **redacted, durable** `AgentStep.PlanCreated` entry: versioned ID, ordinal/total (maximum four), and a fixed action category. These markers correlate ordinary per-step native runs without changing Room tables. Neither the dictated text nor an app target, UI node token, screen contents, or an approval grant is serialized as workflow progress.
- `NativeWorkflowJournal.progress(runs)` projects step outcomes from the **real tool journal**, distinguishing `CONFIRMED`, `DISPATCHED`, `UNKNOWN`, failure, and approval waits. A planned checkpoint is not a successful effect. The projector deliberately exposes `autoResumeAllowed=false`.
- Approval continuation in the *same process* carries workflow ID and step ordinal through the existing bounded pending-sequence map. If process death loses the remainder, successfully resumed approved actions return an explicit **workflow incomplete** message, not a claim of finishing the script.
- A suspended scripted native step cannot be replayed from old UI arguments on user resume: the old run is marked failed with a fresh-observation requirement. After interruption, the user checks the live app and explicitly requests the remaining actions. Fully durable automatic continuation is **not** implemented.
- `AppCommandParser` recognizes sequence boundaries only before command verbs and outside double-quoted text. For example `type "click and then buy" into Search` and `type fish and chips into Search` preserve the field contents instead of splitting them into commands.
- Strict native `find` uses `UiTargetSearch.findExact` and `halo_find_on_screen exactOnly=true`. It refuses absent/duplicate targets; fuzzy ranked candidates remain available only for the model-facing discovery path. No successful find is inferred from a merely fuzzy candidate.
- `go back` and `press back` use the existing, policy-gated `halo_ui_global_action action=back` with a fresh generation. Android's dispatch acknowledgement alone is never treated as proof of an effect.

### Device acceptance additions

1. In an app with Search, say `find Search on screen`. Then try the same target when it has duplicate labels. Unique exact result should succeed; duplicate result must stop rather than report success.
2. Dictate `Open Gmail then tap Search and type fish and chips into Search`. Confirm the typed text includes the word “and” unchanged. Repeat with quoted `"click and then buy"` to test a command-looking phrase inside a field value.
3. Say `Go back`. Verify Android authorization and resulting screen behavior. Repeat inside a sequence and ensure unconfirmed navigation prevents dependent UI steps.
4. Trigger a scripted action requiring approval. Approve and verify ordinal progression in the journal; deny and ensure no remaining step runs.
5. Kill the process while the workflow awaits approval or a UI effect; restore the app. Check that the checkpoint remains, no old click/text entry replays, and the response explains that the remaining steps must be reissued.
6. Verify the activity/journal contains only workflow ID, step index, and action kind, **not** typed text or accessibility tokens.
7. Keep cloud-screen consent off while exercising local deterministic steps; then opt in for a tool-capable cloud provider and revoke it again. Consent must affect subsequent tool rounds, without rerouting native steps through the model.

### Software validation

New regression coverage: `NativeWorkflowJournalTest`, `UiSearchFieldHintsTest`, parser tests, scripted dispatcher tests and Room restart persistence tests. **Not yet executed here.** Run `./gradlew :libraries:halo-cognition-language:test :runtime:cognition:test :app:testDebugUnitTest` on the development machine, correct any compile/test failures, then run real Android accessibility acceptance. This checkpoint is conservative workflow evidence, **not** a durable parent orchestrator or proof of on-device stability.

## PHONE-CONTROL-009 — first-class Activity workflow cards

This pass makes a bounded deterministic phone script understandable from **Activity**, not just from development logs:

- Every durable workflow is rendered as **one** Activity card. Individual native step runs are grouped by the opaque, versioned journal workflow ID rather than appearing as multiple unrelated tasks. Normal one-step native runs are not grouped.
- Activity reports `N of M steps verified`, shows which steps were observed but unconfirmed, and distinguishes pending approval, rejected/failed, cancelled, interrupted and successful outcomes. A planned step is never counted as confirmed without an actual `ToolOutcomeRecorded` confirmation.
- The pending approval action deep-links to the exact live step run; Activity focus accepts an individual step run ID and scrolls to its containing workflow.
- Incomplete, failed or uncertain scripts show **Give a new instruction**. The button opens Halo Home to submit the remaining goal afresh; it never calls `resume`, reuses an earlier approval or replays an accessibility token.
- The workflow card is localizable in English, Russian and Latvian through dedicated Compose string resources. App-authored labels and progress are translated; original screen text or transcript are intentionally not included in the grouped card.
- The semantic Android Back command is permitted from an unreadable-but-captured screen, because global Back has no semantic target. The exception is narrow and applies only to `halo_ui_global_action`: both preflight and `AccessibilityActions.dispatch` call the validator without requiring an inspectable tree, while retaining snapshot TTL, current revision, live foreground-package, protected-window and action-availability checks. Normal `halo_ui_click`, `halo_ui_set_text` and other token-based actions still require an inspectable surface and exact-generation target.
- A scripted action now fails closed **before external execution** if its workflow checkpoint could not be appended to the durable journal. This is covered by a forced journal failure test.
- A resolved input request is not projected as a new pending approval. When execution truth remains unknown, Activity says so.

**Acceptance on a real device:** Issue `Open Gmail then tap Search and type fish and chips into Search`, review Activity for one card and three steps, verify that neither `fish and chips` nor any accessible control token appears in the card, and repeat with approval denial, a forced interruption, and a dispatched/unconfirmed result. Switch Halo among English, Russian and Latvian and check labels, progress, and the fresh-instruction CTA. Ensure the button does not trigger a previously authorized action.

**Validation status:** These changes have been committed but not compiled or instrumented in this environment; Gradle and Android hardware are unavailable. Run `./gradlew :libraries:halo-cognition-language:test :runtime:cognition:test :app:testDebugUnitTest`, verify Android resources for all locales, and execute physical-device permission and process-death acceptance before labelling the feature stable.

## PHONE-CONTROL-010 — durable workflow owner and recovery admission

**Architecture:** every bounded `NativeCommandSequenceIntent` now creates a first-class **NATIVE parent AgentRun**, before any child tool action. The owner saves a versioned, content-free `halo.phone.workflow.parent.v1:<opaque UUID>` journal marker with a 2–4 step count. Existing individually authorized NATIVE step runs retain their `halo.phone.workflow.v1` markers and normal `ToolInvocationPrepared`, `InputRequested` and `ToolOutcomeRecorded` records. No new Room tables, migrations, provider coupling, or serialized UI tokens are introduced.

**Execution contract:** the parent checkpoint must persist before the first action; each step checkpoint must persist before its own action. On every exit from a script and every approved in-process continuation, the parent state is reconciled against **durable child receipts**. It can be marked COMPLETE only when all indexed steps are observed and each effect is CONFIRMED. An explicit step rejection/failure fails the owner; a stop cancels it. Only an approval waiting in a live, time-bounded process-local continuation may leave the owner SUSPENDED. Everything else unfinished or unconfirmed becomes INTERRUPTED, not a fake success.

**Restart contract:** `DefaultAgentRunCoordinator.prepareOutstandingRunsForRecovery` recognizes **both** workflow owners and individual scripted steps and marks nonterminal entries INTERRUPTED. It never retries a provider, an old UI token, a transient text field, or an approval under those entries. `NativeCapabilityDispatcher.resume` additionally requires matching live in-memory continuation generation, expiry and a nonterminal owner **before executing** a scripted approval; a missing/expired grant fails the step and interrupts its parent. Normal non-script native and agent recovery paths remain unchanged.

**User experience:** Activity projects one workflow card using the owner and child records. It renders the parent even when a crash happens *before step 1*, removes the duplicate parent run card, and suppresses stale approval links if the owner was interrupted/cancelled. The explicit **Give a new instruction** route asks for a fresh observation; it is never a button to replay prior actions. The card's translated labels still use EN/RU/LV resources without dictation or on-screen text.

**Stop race:** the workflow snapshots its cancellation generation before parent creation and checks it again when entering every tool path, including after asynchronous app/screen resolution. A stopped/cancelled parent prevents the next scripted effect from dispatching. This does not claim to reverse an effect already dispatched.

**Tests committed:** parent-only journal projection, correlation after Room reopen, parent-completed versus interrupted execution, orphaned approval hiding, startup interruption of parent and scripted step, cancellation during semantic screen reacquisition, and pre-effect journal failures. All new and previously unexecuted phone tests still require Gradle compilation and device acceptance. Include process-kill tests **before the first step**, **during screen settle**, **while awaiting approval**, and **after an unconfirmed dispatch**. Confirm that the Activity card tells the truth after restart and does not present any prior action as safely replayable.

## PHONE-CONTROL-011 — user-directed recovery guidance

- Added `NativeWorkflowRecoveryGuide.assess(progress)`, a **pure, read-only** recovery advisor shared by local and cloud tool paths. It computes the longest contiguous `CONFIRMED` prefix and identifies the **earliest** unresolved step, even when later steps have journal receipts. Its result carries only the ordinal, total, fixed action kind and advisory classification; it contains no UI token, original app target, dictated text, permission grant or tool argument.
- Guidance discriminates `INSPECT_UNCERTAIN_EFFECT` from `REQUEST_FRESH_INSTRUCTION`. An action that was dispatched but not verified **may already have happened**; the user must inspect the current app before requesting another action. A never-started or failed step needs a completely new user command and fresh observation. Live, owned approval requests remain reviewable; orphaned parentless or interrupted approval records do not.
- Activity shows a localized “First unresolved: step N of M” line and the appropriate instruction in English, Russian and Latvian, while retaining the verified-step count. A just-created `EXECUTING` workflow is displayed as in progress rather than prematurely interrupted.
- The Home composer still owns all new user commands. Recovery guidance intentionally does **not** insert an executable command into the composer, synthesize a target label, re-authorize an old action, or silently skip a previously dispatched effect.
- Separately corrected the `ToolExposurePolicyTest` current-app consent fixture: remote screen sharing consent does not automatically activate `halo.current_app`; the test explicitly enables that skill before asserting allow/deny behavior.

**Tester exercise:** (1) run `Open Gmail then tap Search and type fish and chips into Search`; (2) force an unknown tap outcome while leaving a later step receipt; (3) reopen Activity and verify it identifies the earliest unverified ordinal, shows “inspect before repeating”, and never offers direct replay; (4) kill the process while a step awaits approval and verify startup invalidates the old authorization; (5) switch EN/RU/LV and verify recovery advice and step counts; (6) deny remote screen consent, then grant it without activating the current-app skill, and confirm the app-specific tool remains disabled.

**Validation remains pending:** Android/Gradle build, JVM test execution and instrumented screen-control tests were unavailable here. This is conservative user-directed recovery *guidance*, not an automatic paused-workflow replay engine.

## PHONE-CONTROL-012 — truth-gated progression beyond UI mutations

A scripted action must not unlock the next side effect merely because Android reported `DISPATCHED`. `canAdvanceNativeScript` now applies the same explicit progression rule to *both* initial scripts and same-process approval continuations:

- A `CONFIRMED` action may advance.
- A `DISPATCHED` application launch may advance **only** when its immediately following step is a screen action for that exact requested app. The Android host's `NativeScreenTargetResolver` must independently capture the requested app in the foreground and resolve the next current-generation target before any tap, scroll or text entry.
- A `DISPATCHED` timer, volume change, message/share action, other external side effect or standalone app launch must **not** permit another scripted side effect. `UNKNOWN`, rejected and failed actions never advance.

This deliberately makes `Open Spotify then set volume` stop after an unconfirmed app-launch dispatch, rather than executing volume as a second independent side effect. The user may request the remaining action afresh. `Open Gmail then tap Search` retains its guarded app-specific screen handoff. Parent completion is still evidence-based; app-launch dispatches are not yet independently journaled as confirmed from foreground evidence, so a seemingly successful app+screen script can still report the launch as unverified. **Next design step:** record bounded, trusted foreground proof for the preceding launch without promoting the original dispatch acknowledgement itself.

Regression tests now pin this exception, reject all other dispatched continuations, and retain cloud-screen consent and skill-enable separation. Complete the Gradle and Android device acceptance matrix before declaring this stable.

## Speech/text checks

Use an installed app and controls whose actual accessibility labels are known:

| Command | Expected path |
| --- | --- |
| `Open Spotify` | Native installed-app launch |
| `Open Spotify and set volume to 30 percent` | Provider-free two-step script |
| `Open Gmail then find Search on screen` | Provider-free launch then screen-read/target search |
| `Open Gmail then tap Search` | Provider-free launch, **fresh foreground verification**, exact-label tap with normal approval |
| `Type invoice into Search` | Provider-free exact text-field step; not a send/submit |
| `Open Gmail then tap Search and type invoice into Search` | Provider-free three-step script; third step only after confirmed tap |
| `Open Gmail and find the latest invoice` | Agentic, tool-capable provider, observe/search/verify loop |
| `Search in Gmail for invoice` | Agentic in-app workflow, **not** web search |
| `How can I search in Gmail?` | Explanation, no phone side effect |
| `Open Gmail then delete everything` | Unsupported as deterministic script; normal agentic policy only |

Parser supports a maximum of four explicit operations per utterance. Pronouns, undocumented buttons and open-ended goals must be resolved from context; no best-guess target is permitted.

## Operational and provider limits

- **Hardware/permissions:** Android accessibility service must be enabled and the device unlocked. Accessibility is not omnipotent: system Settings UI, permission dialogs, banking/wallet/password-manager applications, CAPTCHA, obscured views and uninspectable target trees are deliberately blocked.
- **Foreground:** Android background-launch restrictions still apply. A launch receipt is `DISPATCHED`, not `CONFIRMED`. Wait for the actual app package; if it does not appear, stop and explain.
- **Approval:** existing per-tool policy still controls access and confirmation. No automatic sending, checkout, payment, permission granting or destructive changes.
- **Provider portability:** deterministic native scripts use **no LLM** and work regardless of the selected chat provider. Open-ended app workflows require a model/provider capable of Halo's tool loop; a text-only model cannot execute arbitrary Android actions merely because it can write prose. Missing capability must be reported rather than claiming success.
- **Verification:** UI text changes must be observable, exact target generation valid and effect confirmed before proceeding with another UI mutation. Unknown results are not automatically replayed.
- **Localization:** deterministic speech patterns in this pass are English; screen labels can be the app's actual displayed language. Agentic multilingual comprehension remains provider-dependent.
- **Coverage:** existing `halo.phone.actions`, `halo.phone.screen` and `halo.current_app` skills remain separate so installing/enabling one does not grant the others silently.

## Device acceptance (no emulator equals real foreground test)

1. Start from a locked phone: confirm all invasive requests refuse or require unlock.
2. With accessibility disabled, request `Tap Search`: expect an actionable setup explanation, no click.
3. Enable accessibility; open a benign app. Verify `Find Search on screen` returns a read result and never activates a node.
4. Verify `Tap Search` and `Type test into Search` stop for approval according to policy. Inspect refreshed snapshots and receipt statuses.
5. In one utterance open an app and tap an exact unique control. Expect package verification; **do not** act if a different app/permission dialog appears.
6. Rename or hide the target before the step executes: expect missing/stale target failure; do not click nearest other control.
7. Use two controls with the same label: expect ambiguity, no action.
8. Approve a tap in a three-step script: confirm the next step is prepared **only after verified confirmation**; approval alone is not success. Deny approval, cancel the action and force post-tap `DISPATCHED`: confirm later script steps never execute.
9. Test a slow app launch (including a temporary empty active window), a screen rotation, activity recreation and a blocked package. Expect a bounded ~3-second settle/stop, never cached tokens or background polling.
10. Switch LiteRT, OpenRouter, OpenAI and other configured providers. Native scripts should behave identically. For unresolved goals, test only targets that expose actual Halo-compatible tool calls; cloud screen tools should be denied until screen sharing is explicitly opted in, and should disappear as soon as consent is revoked.
11. Verify final result does not state an unconfirmed external action succeeded. Examine `HALO_NATIVE_ACTION_TRUTH`, screen receipts and AgentRun steps.
12. Test with Russian/Latvian display labels explicitly; English command syntax remains a known limitation.

### Architecture boundary

The native parser resolves **instructions already explicit in user speech**. It does not infer a target from an untrusted screen, grant authority from the output of an LLM or silently fall back from a rejected native action to a different executor. The provider route can propose tool actions; the existing runtime still authorizes, executes and verifies them.

This pass does not claim full general-purpose, autonomous operation of third-party applications. Supported app controls depend on Android capabilities and semantic accessibility, and the next test cycle must determine real coverage from device traces.
