# Conversation-first Home — issue #44

## Delivered behavior

Home uses one chronological conversation feed and one pinned, expanding composer.
History and New chat remain in the header. The selected conversational mode stays
visible; the existing shell continues to own the durable Private / Power control.
On short screens the mode occupies the center of the header and Chat / Screen moves
into its overflow. Updates, Your day, Activity and Clear conversation remain available
without permanent cards in the thread.

Short windows use a single-row composer. While their keyboard is visible the Home
header is hidden; bottom navigation is also hidden while typing in Home. The persistent
privacy control remains. The narrow-landscape host fixture reserves shell space and a
separate interaction case constrains the composer to a 96dp viewport.

The composer has one attachment entry, one Tools entry, and one mutually exclusive
Talk / Send / Stop target. Text remains editable while dispatch is unavailable.
Selected context has visible removal controls using the existing interaction-context
owner. Drafts remain scoped to the conversation and the existing draft-replacement
confirmation protects unfinished text when editing an earlier question.

Message overflow exposes Copy, explicit Android Share, Read aloud, Save to Notebook,
and Edit as a new message where applicable. Editing prepares a draft; it never silently
submits it. Reading remains mounted after closing the menu and uses the existing
speech provider, Private/Kids and cloud-consent rules. Save uses the exact canonical
conversation/turn review owner. Incomplete replies retain their explicit warning.

The transcript identifies the live answer by conversation and turn ID, never by equal
answer text. Earlier identical replies remain visible. A saved answer also stays in the
feed after a failed follow-up with no partial response. The admitted typed question
remains visible until its exact committed interaction is present. Legacy request IDs
are only used when the committed turn has no contradictory interaction ID.

History reads the canonical Room conversation list and activation transaction. Before
switching, Home awaits capture shutdown, cancels visible work, stops playback and
clears the previous interaction context. A navigation gate prevents concurrent chat
switches or new text/microphone/Voice Mode starts during activation. Restoring a
conversation, including a delayed Room emission, is not an automatic speech trigger.

The orb and compact status still project runtime state. Detailed task artwork/plans
are optional, receipts remain owned by the existing interaction pipeline, and the
global task pill is suppressed on Home to avoid duplicate floating chrome. The global
approval host and Activity review route remain intact. New conversations retain at
most three draft suggestions; daily items move to a separate sheet.

New labels are provided in EN, LV and RU. Existing BrandBook tokens and Open Ring
assets are reused. No runtime engine, provider dispatch, persistent transcript store,
permission grant or tool-execution shortcut was added.

## Verification record

The implementation was completed and independently reviewed before local build/test.
The first debug assembly passed in 9m 1s. A subsequently identified Voice Mode start
race received entry and post-readiness guards before the final delivery gate.

Native recording also exposed a dark-theme user-label contrast error; the label now
uses the mint bubble's `onPrimaryContainer` foreground. The complete build, 99 focused
cases and six recorded fixtures subsequently passed. The final window correction adds
a merged-manifest regression case; its delivery result is recorded with the artifacts.

The phone exposed a separate landscape keyboard failure. `dumpsys window` confirmed
MainActivity was using `adjustPan`, panning the content out of the viewport. MainActivity
now explicitly requests `adjustResize`; the regression reads the merged ActivityInfo,
rather than checking manifest source text. The independent review accepted this fix.

The final frozen gate completed successfully in **2m 41s**: debug APK assembly and
**100 tests, zero failures/errors/skips**, with six explicitly recorded native host
fixtures. The gate used `:app:assembleDebug :app:testDebugUnitTest`, the 15 class selectors
identified by the committed XML reports, `-Proborazzi.test.record=true`, `--no-daemon`,
`--no-parallel`, `--max-workers=1`, a 2 GB Gradle heap and UTF-8. No GitHub Actions ran.

Final APK: **286,408,785 bytes**, SHA256
`8081ec5fa5c70a443c2faed703523ba77c5d65039ddbf40a066af10b888bebf1`.
The final installation attempt reported the device disconnected; this APK is **not
installed/physically qualified**. Earlier installed-APK observations are identified below.

Evidence: [manifest](artifacts/HOME-CHAT-044-2026-10-22/manifest.json),
[test reports](artifacts/HOME-CHAT-044-2026-10-22/tests),
[host renders](artifacts/HOME-CHAT-044-2026-10-22/renders), and
[labelled device observations/failures](artifacts/HOME-CHAT-044-2026-10-22/device).

## Physical qualification remains under #43

The API-35 phone (23124RA7EO) reconnected during validation. The installed functional
revision's SHA256 was read back and matched:
`97b2e227a04db7b30f0aecc24c5fdee2416efb441c36171bb88d41128dce85e1`.
It confirmed New chat, typed draft/Send presentation, portrait IME hiding bottom
navigation, draft retention across rotation, Tools and attachment access, attachment
dismissal preserving the draft, and distinct draft restoration through A/B chat history.
Test drafts were synthetic and never sent to a provider. Existing saved chats were kept.

The landscape **without** keyboard showed the compact composer. The landscape **with**
keyboard failed on that earlier APK; the resize correction was built after the phone
disconnected and has not been installed or physically retested. Do not treat the source
fix or merged-manifest test as a physical PASS. The final APK identity is in the manifest.

The 360dp/200% attempt was interrupted by the debug LeakCanary heap-dump overlay.
The following 412dp capture was still transitioning from that configuration, so it was
excluded. No heap contents were collected. This remains a configuration/retention
investigation for #43. Earlier launcher captures and images containing old user history
are excluded from the committed evidence.

Density (440, no override), font scale (1.0), automatic rotation (1) and user rotation
(0) were restored and read back before disconnection. Host component renders remain
separate from installed-app evidence and real speech/provider qualification.

- [ ] Install the exact final APK and exercise idle, typing, recording, streaming,
  acting, approval, provider-missing/offline, cancellation, error and recovery states.
- [ ] Confirm history switching during ordinary capture, Voice Mode, pending text and
  read-aloud; no stale delivery, automatic historical speech or wrong-chat binding.
- [ ] Verify draft preservation through rotation, sheet dismissals, lifecycle and
  multiple chat switches, including pending Android share flows.
- [ ] Accept dark/light 360/412dp, 200% EN/LV/RU text, short landscape, tablet and IME
  layouts on Android; verify TalkBack focus and reachable 48dp controls.
- [ ] Install the resize-corrected APK, confirm `dumpsys` reports resize, and repeat the
  landscape IME failure case; investigate the debug configuration/retention interruption.
- [ ] Verify exact-turn copy/share/save/edit/read actions and removable context against
  saved objects; preserve Private/Kids/cloud-consent and global approval boundaries.

These remain unchecked release criteria. Review, compilation and host tests must not
be substituted for physical acceptance. Sibling Tools catalog (#45), orb satellites
(#46) and processing profiles (#47) keep their separate implementation scopes.
