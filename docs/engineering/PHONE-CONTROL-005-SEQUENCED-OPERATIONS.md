# PHONE-CONTROL-005 — sequenced Android operations

Status: implementation on `main`; device validation pending. Scope: Android host only.

## What changed

- Plain native commands remain model-independent and use `NativeCapabilityDispatcher` → `SkillExecutionService` → `ToolTurnExecutor`. STT supplies text, never authority.
- `AppCommandParser` checks bounded scripts **before** generic app launches. Otherwise "open Gmail then tap Search" is mistakenly treated as a single installed application name.
- Explicit spoken screen actions `find <label> on screen`, `tap <label>`, `type <value> into <label>` are represented as typed intents. There are **no coordinate commands**, screenshots interpreted as authorization, or remembered UI tokens.
- The Android `NativeScreenTargetResolver` obtains a new accessibility snapshot for each step, verifies the foreground package after launch, refuses blocked or incomplete screens, and requires exactly **one enabled, nonsensitive, exact-label match**. It then prepares an existing `halo_find_on_screen`, `halo_ui_click` or `halo_ui_set_text` call.
- The existing tool-turn authorization, approvals, preflight, exact-generation token validation, action receipts and durable journal execute the action. No direct-user-authority shortcut for UI mutations.
- A scripted sequence stops at a failed, rejected, unknown or unconfirmed UI action. If a UI action requires approval, the remaining explicit steps are held **in-process** and can continue only after the approved step has actually completed with a confirmed effect. An unapproved or cancelled action never advances the sequence. The hold expires after five minutes and is not automatically restored after process death. A launch being **dispatched** allows a subsequent fresh foreground check; launch alone is not considered proof the app is open.
- Unresolved in-app goals such as "open Gmail and find invoices" are **not** downgraded into `OpenIntent` or web search. They enter the agentic tool path.
- Explicit screen workflow turns seed Android semantic tools at model preparation using the same skill-availability, app policy, current cognitive mode, host and local-model allowlist. There is no vendor-specific phone executor.

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
9. Test a slow app launch, a screen rotation, activity recreation and a blocked package. Expect bounded retry/stop, never cached tokens or background polling.
10. Switch LiteRT, OpenRouter, OpenAI and other configured providers. Native scripts should behave identically. For unresolved goals, test only targets that expose actual Halo-compatible tool calls and check the tool journal.
11. Verify final result does not state an unconfirmed external action succeeded. Examine `HALO_NATIVE_ACTION_TRUTH`, screen receipts and AgentRun steps.
12. Test with Russian/Latvian display labels explicitly; English command syntax remains a known limitation.

### Architecture boundary

The native parser resolves **instructions already explicit in user speech**. It does not infer a target from an untrusted screen, grant authority from the output of an LLM or silently fall back from a rejected native action to a different executor. The provider route can propose tool actions; the existing runtime still authorizes, executes and verifies them.

This pass does not claim full general-purpose, autonomous operation of third-party applications. Supported app controls depend on Android capabilities and semantic accessibility, and the next test cycle must determine real coverage from device traces.
