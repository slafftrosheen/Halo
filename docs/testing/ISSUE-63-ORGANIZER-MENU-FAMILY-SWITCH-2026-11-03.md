# Organizer hierarchy and persistent family-mode menu control

Status: source implementation and regression tests authored; build/test/device execution **NOT_RUN**, per the user's code-first instruction on the Raspberry Pi.

## User-requested hierarchy

Workspace → Tools lists Organizer alongside Halo Studio, Halo Notes, Study, Memory and Canvas. Your day in Halo, World clocks and Sources are no longer separate rows in that toolkit. Opening Organizer provides:

- Tools: Your day in Halo and World clocks.
- Settings: Sources (adult only).
- Existing organizer agenda/tasks/reminders/alarms/timers and editing/delivery actions.

The global Orb's Workspace instrument group includes the same Organizer entry. The child global menu lists Organizer as the parent instead of listing clocks/brief as peer instruments. Child Organizer still opens allowed clocks and its scoped brief, but not adult source management. Canonical navigation admission lists and typed command IDs are retained for existing assistant, widget, Library and saved links.

This user instruction supersedes the earlier presentation decision assigning every Everyday instrument to Screen. Existing `screen/organizer`, `screen/brief`, `screen/clocks`, `screen/sources` and `screen/source` URI strings are deliberately preserved for compatibility. Primary selection identifies Workspace; cold-entry Back fallbacks identify the actual Organizer parent (or source list for adult article details). Ordinary Back still returns to the previous navigation entry and draft.

`OrganizerToolLinks` shares the canonical destination catalogue with the shell. All labels reuse existing localized keys. The Organizer icon aliases existing calendar artwork with a tool role, so it cannot be mislabeled Halo Notes by the icon-based Workspace naming helper.

## Family-mode menu ownership and security

The main shell menu previously supplied only Local/Power privacy controls; it had no persistent family-mode switch. Family options were tied to individual Kids/Settings pages. In addition, `HaloZone5OrbLauncher` closes its transient menu on Activity ON_STOP, including the stop caused by Android's credential Activity. Hosting authentication state inside that menu would destroy its launcher/result consumer during verification.

`rememberFamilyModeMenuControl` is now composed at AppShell scope. Its returned presentation is placed at the top of every global Orb menu through a dedicated `familyControl` slot, independently of current page actions, workspace expansion, privacy controls, compact layout or family presentation. Dismissing the menu does not dispose its credential gate, pending callback or durable preference write.

The switch reads and writes the existing `KidsPreferences` owner and key. Enabling Kids persists through that owner. Leaving Kids requires the existing grown-up/device PIN or password gate; cancel, absent lock credentials, failed authentication or failed preference persistence cannot report a mode change. The switch remains visible but disabled while loading/writing. Failures retain a visible retry explanation. Parent disclosure pauses shell guidance, and a successful change cancels active Home voice input before changing the family presentation.

Kids/full Halo are presentation and authority states, separate from Local/Power execution/privacy. No third privacy mode, new network permission, bypass tool or Android permission is added. The manual credential operation remains the same protected human/OS exception; assistant tool schemas are not given a credential bypass.

## Authored regressions and later validation

Eight new test methods plus the updated Workspace catalogue assertion cover:

- Toolkit parent-only presentation and all three working Organizer subtool destinations.
- Adult/child catalogue separation, preserved deep-link admission and parent highlighting.
- Family-switch visibility while changing pages/family presentations.
- Durable entry into Kids, menu reopening, disabled loading, protected exit, and retaining the gate when menu content is removed.

No Gradle tasks, compilers, unit/UI tests or APK builds ran in this coding phase. Source diff/whitespace inspection is not runtime evidence. Later, run serially:

```bash
source /home/pi/.config/halo/env.sh
./gradlew :app:testDebugUnitTest --tests '*ShellOrbCatalogTest' --tests '*OrganizerToolHierarchyTest' --tests '*FamilyModeMenuControlTest' --tests '*FamilyModeMenuVisibilityTest' --tests '*Zone5KidsControlsTest' --tests '*Zone5WorkspaceMenusTest' --tests '*KidsPreferencesTest' --tests '*EverydayEntryNavigationTest' --console=plain
```

Then include this batch in the issue-63 regression/build gate. Actual Android credential success/cancel/process death, entry/exit from every main-shell page, launcher cold-entry Back, TalkBack, large-text/IME and EN/RU/LV presentation require later execution/device evidence. Do not infer that acceptance from these source changes.

Independent read-only source review: no actionable important finding; no obvious Compose/Kotlin typing issue found. Credential success, lifecycle and rendering acceptance remain NOT_RUN.
