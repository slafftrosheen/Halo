# Issue 61: actual adult Study journey

Status: **KEEP_OPEN**. Device `f01fe673`, Redmi 23124RA7EO, Android 15; Russian UI,
explicit English learner questions. Configured provider `openrouter`, alias `openrouter/free`;
the resolved serving model was not retained and is not inferred from that alias.

The native Word editor created a real 444-character QA note. Its actual selected range
`[0,444)` and revision 12 reached the canonical adult, guided, source-only Study session.
The optional probe was explicitly skipped. Three escalating questions were typed through
the native composer. All three actual provider replies retain the same source ID, version
and exact locator. The third request encountered a network failure; its draft and the two
earlier replies survived, and one explicit retry succeeded.

**FAIL: strict source-only content.** The first reply introduced surface tension, absent
from the source. All replies remain explicitly UNVERIFIED, and no mastery is inferred.
A real source citation proves attribution, not semantic support for every generated claim.

Practice creation first rejected a response without saving any drafts. One bounded diagnostic
reproduction observed a valid five-card JSON reply at the production parser, then continued
that unmodified reply into the normal persistence/UI path. The debugger and ADB forward were
removed. This establishes a functional debugger-observed case, not unassisted reliability or
performance. Native review kept three faithful, distinct cards; the weak heat question and
duplicate surface-escape question remained unaccepted. Every card retains revision 12 and
the exact range. Review counts and mastery remain zero.

**PASS: generated output save and restart persistence.** Native Save study notes created
note `e90d4692-4430-44b0-8497-d0668d30acec` with canonical session provenance, a live source
reference, all three actual replies and exactly the three kept questions/answers. After process
stop, installation and reopening, native Study library showed the same lesson with three due
cards. Continue resumed that exact session; Learn restored its latest question/reply. Independent
readback verified unchanged note blocks/provenance, all three persisted turns and all five draft
states. No duplicate output or automatic grading was introduced.

The journey exposed a separate host next-step bug: ASK requested the diagnostic even after
the learner skipped it. A focused regression failed before the fix and passed afterward for
skipped, completed and still-needed probes. Future ASK replies now invite restating the idea
when the baseline is already handled. Historical saved turns remain unchanged.

App-authored source locators now render through the existing language owner in both citation
and card rows. The installed app showed the Russian selected-range label with the same exact
offsets. Model replies and source titles preserve their original requested language. The actual
network failure and host-authored next steps also have EN/RU/LV/PL copy.

Only that regression and main APK assembly ran for this change; no generic suites, lint or
unchanged instrumentation rebuild. Frozen source hashes show zero build drift. Installed APK
SHA-256: `323f34f67d1b1bc4b325d13929666f3bbb6acf256e16d2d04529a6f07c31370b`.
The earlier generation APK and all IDs/limitations are recorded in the
[result](issue61/2026-10-30/adult-generated-output-result.json).

Voice remains deferred. Child/photo, mixed-media, protected hardware and all other acceptance
are separate gates. Neither this persistence result nor issue 60's user qualification closes them.

Evidence: [actual replies](issue61/2026-10-30/actual-tutor-turns.json),
[cards](issue61/2026-10-30/practice-cards.json),
[saved note](issue61/2026-10-30/saved-summary.json),
[focused build](issue61/2026-10-30/probe-copy-result.json).
