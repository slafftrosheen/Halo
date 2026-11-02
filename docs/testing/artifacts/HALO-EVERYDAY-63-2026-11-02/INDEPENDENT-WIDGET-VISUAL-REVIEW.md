# Independent Everyday widget visual review

Status: Independent bounded source and rendered-fixture review. Widget findings closed on matching host evidence; separate disclosure correction awaits its release result. Launcher/device and participant acceptance pending.

**Current confirmation: widget usability/implementation 32/40 = 8.0/10; sampled visual clarity 9.2/10.** The confirmation addendum at the end supersedes the initial scores and finding dispositions below. The initial failed runs and original assessment remain preserved as history.

Reviewed 2 November 2026. Assessment A and Assessment B ran as isolated reviewer agents. A completed before B's detector findings entered synthesis. All 32 synthetic widget PNGs and the dashboard-configuration PNG were inspected; the synthesizing reviewer additionally inspected RU/LV/PL organizer form captures. No reviewer edited production/test files, ran an app build or operated a device.

**Provisional widget usability/implementation assessment: 28/40 = 7.0/10. Sampled visual clarity: 8.3/10.** These use the original ten-by-four and five-by-two rubrics. They assess the bounded widget sample, not whole-Halo implementation completeness, participant task performance or physical launcher acceptance. The separate P1 disclosure finding below prevents integrated source acceptance regardless of these widget ratings.

## Evidence boundary

The 32 RemoteViews images belong to the earlier `integration-source.json` 3,741-file snapshot and `integration-results.json`: **299 tests, 281 passed, 18 failures, zero errors/skips**. The native widget suite had seven tests with two clock-test failures; the geometry/render cases passed. Images cover eight variants in EN normal/light, LV normal/light at density 3, RU 200%/dark, and RU advertised minimum sizes at 200%.

The later `integration-final` run failed at compilation and executed no tests. The subsequently corrected `integration-verified-source.json` run is active; no green result is inferred. Events translation, clock test receivers and date-sheet changes postdate these renders. The four reviewed widget production targets retain the same hashes across the 3,741 and 3,743 source snapshots.

The detector ran once on the native renderer, configuration activity and two XML layouts: exit 0, JSON `[]`. It supplied no processed-file inventory and reported platform null. This is zero emitted detector findings, not an Android geometry/accessibility certification. Browser inspection was inapplicable to the native targets.

## Prioritized findings

1. **WV01 — P2, incomplete interface localization.** `EverydayCards.kt:179–180,195` requests `Active items` and `Due today`; both remain absent from the inspected catalogue/resources. RU and LV tasks, dashboard and alarm captures therefore retain English status labels. Add these through the canonical EN/RU/LV/PL catalogue and verify matching renders. Captured Events is already source-corrected; English synthetic headlines, publishers and city names are fixture content rather than untranslated interface labels.
2. **WV02 — P2, compact variants lose their identity.** At RU minimum size/200%, seven non-clock variants show identical `Open Halo` tiles while their taps open different targets. `EverydayWidgetViews.kt:158–168` hides title/status and replaces them with that generic fallback. Retain a measured concise localized variant/destination label plus full accessible identity. Add purpose-specific content only when it fits; preserve the existing exact target and readable text size. Tests that merely assert remaining text fits cannot detect this loss of recognition.
3. **Integration P1 — same-profile grant revocation can outlive a held tool read.** `EverydayToolExecutor.kt:53–65` fences family/mode/foreground, but not the exact source grant/version or BriefSettings sharing version used by its result. `organizer_get` suspends after checking the record grant; `source_get` receives a coherent but potentially superseded repository snapshot; `EverydayBriefService.visible` can suspend after reading settings and sources and ends with an audience-only check. Current repository callbacks do not advance the family presentation revision on these individual grant changes. Revalidate canonical disclosure authority immediately before returning Success, with held reads and revocation while remaining in Kids. This was separately requested and confirmed during this visual pass; it is not represented by the widget-only score.

No additional clipping/overlap was confirmed. The clock/weather RU title looked truncated in a grouped tool response, but standalone original-resolution inspection showed the complete title. Inspected SHA-256: `4ad90f19b40a0c78a9e97bc33c2d2c28e0bde897d6a708edab400701bc52a96a`. Do not convert this tool-presentation anomaly into a production defect.

The configuration PNG does not establish dark-theme contrast. Its test hosts the bare form under a dark HostlessHaloTheme without the production Activity's Surface (`EverydayWidgetConfigurationActivity.kt:71–72`), producing a light background and faint dark-theme chip labels. Match the production host before judging contrast. Its captured viewport shows the Apply area, while test source separately exercises reordering/restoration; the image alone does not show the reorder controls.

The 1970 clock/date is the host fixture epoch while other synthetic data uses November 2026. It is not a demonstrated production clock error or a valid real-time/date acceptance result.

## Original usability rubric

| Dimension | /4 | Main basis or limitation |
|---|---:|---|
| System status | 3 | Cached/local and scheduling states exist; compact states disappear. |
| Real-world match | 3 | Familiar categories; two untranslated status labels remain. |
| Control and freedom | 3 | Canonical configure/cancel/CAS mechanisms; final integration/device evidence pending. |
| Consistency | 3 | Shared semantic theme and owner paths; compact identities are lost. |
| Error prevention | 3 | Widget version/privacy/target guards exist; score is widget-scoped. |
| Recognition | 2 | Seven compact variants are indistinguishable. |
| Efficiency | 2 | Compact variants require opening Halo to identify their purpose/content. |
| Minimalism | 4 | Restrained hierarchy, readable wrapping, no decorative clutter. |
| Recovery | 2 | Source mechanisms inspected; matching final lifecycle/launcher evidence pending. |
| Help | 3 | Refresh/privacy explanations exist; independent comprehension unmeasured. |
| **Total** | **28/40** | **7.0/10, bounded expert widget implementation assessment.** |

## Original visual rubric

| Dimension | /2 |
|---|---:|
| Hierarchy/content focus | 1.8 |
| Typography/readability | 1.7 |
| Recognition of controls/files/tools | 1.3 |
| Geometry/cohesion | 1.9 |
| Visible state/orientation | 1.6 |
| **Total** | **8.3/10** |

Halo's Orb, semantic light/dark colors, reading fonts and generous wrapping are coherent. The score deductions chiefly reflect lost compact identity, untranslated status and reduced visible orientation. Large-text users are the most directly affected group; no participant behavior is inferred from expert inspection.

Current Everyday integration and Local/Power matrix documents now address the earlier documentation gap with explicit owner/evidence limits. Prior EUI03/EUI05 restore corrections remain source-addressed. Their presence does not establish full issue-63 acceptance.

No physical launcher, lock/unlock, Doze/battery, real notification/audio, TalkBack/Switch Access, participant completion/error/ease or whole-product 10/10 result is claimed. Earlier predictable-Halo ratings remain historical and are not replaced by this different bounded target.

Questions skipped: implementation and verification are already authorized and assigned to the root agent; no user design decision is required for these concrete fixes.

## Matching handoff confirmation — 2 November 2026

Assessment A and Assessment B again ran independently. A completed before B's findings entered synthesis. Each inspected the fresh **32 widget PNGs plus the dashboard-configuration PNG**; the synthesizing reviewer additionally inspected key captures as standalone images. B verified that all **33 image hashes match `handoff-renders.zip`**, and that 11 reviewed implementation, test and translation files match `handoff-source.json`.

`handoff-results.json` and archived JUnit record **BUILD SUCCESSFUL, 305 tests, zero failures/errors/skips, 9m17s, and 3,744 matching source hashes**. Native widget rendering passed **7/7**, forms passed **7/7**, and the four widget Apply/recovery cases passed. This is matching host evidence, not a claim that an Android launcher or physical phone executed these widget journeys. Earlier failed and unexecuted runs above remain historical; they are not relabelled green.

### Finding closure and design judgment

- **WV01 — closed:** Active items, Due today and Events now render translated in the RU/LV captures. Canonical EN/RU/LV/PL catalogue entries were checked. English synthetic publishers, headlines and city content remain labelled fixture data.
- **WV02 — closed:** all minimum-size variants retain a distinct localized purpose. Open is included when measured space permits; the root retains the full accessible action and kind. There is no longer a set of indistinguishable Open Halo tiles with different destinations.
- **Fixture limitations resolved:** clock/date now use the aligned November 2026 host epoch. The configuration image now includes the production-style dark Surface; chips, selected state and Apply are readable. The capture is scrolled to its lower controls, while matching tests cover dashboard reordering and restoration.
- **No new material widget production defect was established.** The grouped-output clock/weather anomaly again disappeared on standalone inspection. It is not a clipping finding. At minimum RU 200% size, some long words wrap inside words but remain complete and in bounds; this is a small readability tradeoff, not a blocker.

The composition is specific to Halo: the Orb, semantic light/dark colors, restrained hierarchy, reading typography and generous wrapping are consistent with its native interface. Content and the primary destination remain clear. The minimum sizes necessarily show less information; configuration explicitly explains that Android controls refresh and that enlarging a widget reveals more.

First-time recognition now benefits from purpose-specific compact labels. Large-text users have complete in-bounds labels in the reviewed cases. For interrupted users, retained Apply ownership and verified operation receipts support recovery. No new cognitive-load or persona-specific blocker was found in this bounded confirmation. These are expert observations, not measured user comprehension or task-completion results.

### Final bounded original rubrics

| Usability/implementation dimension | /4 | Basis and remaining evidence limit |
|---|---:|---|
| System status | 3 | Local/cached provenance and update explanations remain honest; minimum sizes reduce visible detail. |
| Real-world match | 3 | Familiar translated categories; comprehension of compact labels remains unmeasured. |
| Control and freedom | 3 | Per-instance configuration, cancellation and retained Apply owner have host coverage; launcher behavior remains unobserved. |
| Consistency | 4 | Purpose identities, theme and canonical owner paths are coherent across the reviewed variants. |
| Error prevention | 3 | Version, target, publication and privacy guards have bounded source/host coverage; no launcher qualification is inferred. |
| Recognition | 3 | Compact identity loss is fixed; minimum tiles still offer less context than larger cards. |
| Efficiency | 3 | Exact entry targets and per-instance choices are present; human task speed is unmeasured. |
| Minimalism | 4 | Restrained hierarchy and readable wrapping without decorative clutter. |
| Recovery | 3 | Matching tests cover failed writes, rotation during commit, late durable receipts and snapshot invalidation. Physical recovery is unobserved. |
| Help | 3 | Clear configuration/refresh explanation; assistive and participant comprehension remain unmeasured. |
| **Total** | **32/40** | **8.0/10, bounded expert widget implementation assessment.** |

Capture-only Assessment A recommended 31/40, with recovery at 2 because its assigned images did not demonstrate failure journeys. Synthesis raises recovery to 3 using the matching `EverydayWidgetApplyOperationTest` results and previously inspected owner source. This evidence-based adjustment does not infer a physical launcher result.

| Visual dimension | /2 |
|---|---:|
| Hierarchy/content focus | 1.9 |
| Typography/readability | 1.8 |
| Recognition of controls/files/tools | 1.8 |
| Geometry/cohesion | 1.9 |
| Visible state/orientation | 1.8 |
| **Total** | **9.2/10** |

The remaining widget rating limits describe compact-content tradeoffs and evidence ceilings; they are not a list of additional production fixes. No numerical participant rating, whole-Halo score or overall issue-63 completion is assigned. Real launcher installation/resize/update/privacy behavior, lock/unlock and Doze, human TalkBack/Switch Access and task completion/error/ease remain outside this confirmation.

### Separate disclosure correction — source disposition

The original integration P1 is **source-addressed; matching release result pending**. The green 305-test handoff includes the three earlier same-Kids held-read regressions. The subsequent `release-source.json` contains the additional canonical ordering correction, which is not retroactively covered by that result.

`EverydayDisclosureSnapshot.kt:10–12` reads organizer IDs/versions, source IDs/versions and BriefSettings version in one Room transaction. `ManualDependencyGraph.kt:201–202` supplies that snapshot and child-library state to both tool and brief owners. `EverydayToolExecutor.kt:60–71` captures atomic admission before the coherent canonical read, compares the canonical state again after result encoding, then checks foreground/family/coroutine and the final atomic fence. `EverydayBriefService.kt:49–79` applies the equivalent sequence around filtering and child-local grants. This addresses both an already-held read and a new read admitted while an earlier writer is still uncommitted.

The new regression `EverydayToolExecutorTest.kt:103–116` uses a file-backed WAL database, holds the writer after invalidation, admits a new reader, requires it to remain unresolved until the writer commits, then asserts rejection without the record title while the Kids session remains active. Read-only source inspection found no residual in this ordering mechanism. Its execution result is still pending at this addendum; neither the widget score nor the earlier 305 result substitutes for it.

The confirmation detector ran once on four targets, exit 0 and JSON `[]`. It provides no native file/rule coverage inventory, so this is zero emitted findings rather than Android accessibility certification. A browser overlay does not apply to these native RemoteViews. No reviewer ran an app build, edited implementation/tests or operated the phone.

Questions skipped: zero remaining widget priority issues; the separate privacy verification gate is already assigned to the root agent, with no user design decision needed.
