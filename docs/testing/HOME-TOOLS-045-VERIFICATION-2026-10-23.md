# Home tools and attachments — issue #45

## Delivered behavior

Home has one expanded attachment/context chooser and one grouped Tools catalog.
Camera, Photos, File/PDF, Audio, Notebook and Screen/Ink selection reuse their
existing host owners. A pinned Done action stays outside the scrollable sources.
Multi-line source descriptions use 12dp corners so enlarged text stays inside
the control instead of being cut by a tall pill. Native dialogs preserve the
app's current font density.

The typed catalog exposes stable IDs, group, icon, canonical owner binding,
offline/mode eligibility, actual readiness, reason and targeted recovery.
Create / Find / Work / On this phone share the existing runtime registry,
capability/permission projection, selected answer-model eligibility and web
owner's local preflight. Opening the catalog does not call a provider, probe a
model, reserve cost or grant consent. Explicit Use rechecks readiness and cancels
pending UI work on dismissal, lifecycle stop or disposal.

Existing editors open as editors with Limited status where assistant operations
are not yet available. Maps prepares a draft for the existing Android handoff
tool, never an invented navigation receipt. Calculations and Python are explicitly
In development, with purpose previews and no Run/Send/execution action. The plan
records a future isolated Python environment with denied filesystem/network,
resource limits, deterministic cancellation, vetted packages and explicit Run.

Files enter the canonical Workspace Inbox using its bounded import path. A receipt
offers explicit attachment only for supported text/images, Study for PDF, and
Notebook for saved-only formats/audio. Importing does not attach or transcribe.
Binding is protected against duplicates and conversation switches, preserving
existing visual context if source selection fails. Notebook choices exclude
trashed and unavailable files. Existing source chips retain removal/provenance.

Voice Mode uses the existing owner, with busy-state/readiness checks and generation
invalidation on Stop/chat switch; a delayed capture rechecks inside its command
mutex. No new speech or tool executor was added. Compact landscape typing uses
a stable multiline BasicTextField with compact decoration padding. It preserves
the full draft, including embedded newlines, across viewport changes.
EN/LV/RU resources share 113 new keys; existing BrandBook/Open Ring assets remain.

## Local verification

Source and regression cases were completed before build/testing and independently
reviewed. Phone findings were fixed before rerunning the checks. The first
integrated gate passed 157 app cases and one LiteRT artifact-lock case. A later
attachment-expansion gate passed 160 app cases; the unchanged provider task was
up to date. The final stable-editor gate passed **163 app cases, zero failures/errors/skips**,
with **eight recorded native host renders**, in **8m 32s**.

The final gate uses debug assembly and the focused app classes named by the XML
reports, native Roborazzi recording, no daemon/parallel tasks and one worker.
The 2 GB attempt was deliberately stopped after sustained heap saturation;
the retry uses a local 3 GB heap override without changing repository settings.
An attempted dynamic single-line mode was rejected by a host test and an actual
phone rotation crash (Compose scroller orientation mismatch). The replacement
keeps one multiline scroller; transition/editing and inner-viewport 1x/2x cases
cover this failure path. Rejected APKs are not final acceptance evidence.
No GitHub Actions ran. Build artifacts do not establish provider or device behavior.

Final APK: **286,483,453 bytes**, SHA256
`68f53c0f6c822fef8fdd88ac72d668b914e9aea5647f97ea46d05d92de2c14eb`.
This exact APK was installed and its hash read back matched. The manifest identifies
the compiled app source tree. Provider lock inputs did not change; its earlier
passing XML is labelled separately from the final app-only execution.

Evidence: [manifest](artifacts/HOME-TOOLS-045-2026-10-23/manifest.json),
[test reports](artifacts/HOME-TOOLS-045-2026-10-23/tests),
[host renders](artifacts/HOME-TOOLS-045-2026-10-23/renders),
and [named phone captures](artifacts/HOME-TOOLS-045-2026-10-23/device).

## Named phone observations and limits

Samsung SM_G990B2 / Android API 36, RU app language and existing Private mode.
The earlier tested APK was 286,483,453 bytes, SHA256
`4fad7b18842e2af055eb108d81c6531cf2bc0cdfdbc0cc40214390b50c038813`;
the installed APK hash matched. It demonstrated:

- Tools discovery, local note readiness, online-image setup requirements, and
  Python purpose once with In development and no executable action.
- All six attachment entries reachable, pinned Done, and dismissal retaining draft.
- A synthetic 71-byte UTF-8 file imported through Android's local document picker;
  saved-only receipt, explicit Attach, source chip, removal, and draft retention.
- Canonical Notebook cleanup selected exactly the synthetic file and moved it to
  recoverable Trash. The trashed fixture then disappeared from source selection.
  Only the hash-verified owned Download fixture was removed from device storage.
- Draft retention through picker/navigation/configuration changes.

That APK also exposed landscape field clipping and rounded-button text clipping
at 200% font. It is superseded for visual acceptance. The final `68f53c...` APK passed the named corrected checks:
landscape rotation with IME visible, complete editable text above the keyboard,
and Attach/Tools/Send reachable; the 360dp/200% attachment file description has
no corner clipping and pinned Done operates; Tools opens at approximately 412dp
(density 419, 412.4dp physical width) with 200% text. Draft retention after these
changes was confirmed and the installed hash rechecked. MainActivity still reports
`adjustResize`. These observations do not qualify every landscape/font/IME state.

The device baseline is physical density 480/no override, font scale 1.0,
accelerometer rotation 1, user rotation 0; configuration checks restore and read
back these values in finally. No provider question was sent and no new spending
or provider consent was approved. This is not proof of all background network traffic.

TalkBack exploration, fresh permission denial, process-death restoration, live
provider/error/offline behavior, all voice states and full release qualification
remain tracked in #43. The earlier API-35 phone's landscape failure and interrupted
large-text test are not retroactively passed by this Samsung check. #47 retains
profile enforcement and #54 the wider natural-language assistant-operability gaps.

Evidence contains synthetic host fixtures and selected synthetic phone views only.
Existing chat history, account-picker views, Notebook metadata, raw logcat, heap
contents, credentials and the APK binary are excluded from Git.
