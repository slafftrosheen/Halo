# Documentation reconciliation audit

Status: CURRENT SOURCE MAP / AUDIT RECORD. Reviewed 2026-09-21.

## Scope and limits

The user requested code as source of truth, removal of false documentation claims, current
GitHub/web research and incorporation of a supplied UI mockup. This pass rewrites active
implementation entry points and explicitly separates remaining targets and historical research.
It does not certify every historical vendor assertion or every line of source as correct.

Evidence came from Gradle/manifest/workflow configuration, app composition/lifecycle/navigation,
provider/session/tool paths, storage, shared UI rendering and HeyCyan implementations/catalogs.
No Gradle suite, device run, security penetration test or visual app acceptance was performed.
The existing AudioPerceptionEngine edit was not changed.

Current implementation = executable source plus actual wiring/configuration.
Declared contracts, comments, module names and screenshots are insufficient by themselves.

## Corrected claim ledger

| Old claim | Source finding / correction |
|---|---|
| Architecture/specification outranks current implementation facts | Documentation now separates intended constraints from actual behavior |
| Completed PR tables and subsystem quality scores prove readiness | Removed unsupported scores/checkmarks; release acceptance is not established |
| core/domain/application is the repo layout | settings.gradle.kts defines app/runtime/engines/sdk/host/drivers/providers/libraries/lab |
| Older microkernel is the production phone loop | app HaloRuntime explicitly starts Engine/EventBus/Blackboard and excludes old microkernel startup |
| One universal engine signature / one SharedFlow | Actual SDK Engine, Process, agent and bus contracts differ |
| Tool use is entirely future / general phone tools already work | CognitiveSession has bounded rounds; app model registry contains echo/time only |
| System assistant is an interchangeable inference backend | AndroidAssistantProvider implements handoff; no returned model answer |
| Providers/plugins are runtime security sandboxes | Adapters/plugins run in process; interfaces are not isolation |
| Every external request passes an unbypassable privacy gateway | Provider adapters own network clients; context policy has narrower scope |
| API keys are encrypted and logs contain zero secrets | Key is written to ordinary SharedPreferences; masking is UI only; zero-leak proof absent |
| Full set of Room tables, encrypted DB and universal export | AppDatabase declares one Memory entity; cognition/conversation use separate in-memory paths |
| All memory is durable/searchable | Several stores use maps and mocked/all-value search; Room definition alone is not wiring |
| Console/Protocol Lab/Inspector shipped as routes | Absent from current AppShell destination registry |
| Uniform glass-only UI, real blur, reduced motion and 60fps proven | Component families coexist; Material3 used; surface does not consume blurRadius; acceptance unmeasured |
| No decoder or real capture material exists | Driver constructs response router/decoder and raw evidence artifacts exist |
| HeyCyan live microphone is unambiguously supported or absent | AudioSource code exists but catalog marks unsupported; phone engine uses AndroidAudioSource |
| Time-sync action constant means working command | Catalog labels payload blocked |
| 81/82/70/71 decimal equal 0x81/0x82/0x70/0x71 | Correct current catalog bytes are 0x51/0x52/0x46/0x47 |
| Voice-status/BT-connect current constants are 0x48/0x49 | Current source catalog says 0x4C/0x4D; document source, do not invent hardware evidence |
| CI runs instrumentation/API/docs/performance gates | Workflow does not declare those; detekt/ktlint invocations lack inspected Gradle configuration |
| TestFlight release and “all tests pass” are current | Replaced with Android release checklist and explicit absence of a fresh run |
| Recorder requests MTU 517 / emits no writes of any kind | It reads negotiated mtu_size; subscribing notifications may write CCCDs |
| HeyCyan control connected means audio connected | BLE control and Android audio association/profile/endpoint are separate states |
| Any new Bluetooth endpoint proves HeyCyan routing | Route verification now requires the persisted user-approved association address |
| Benchmark build type proves performance | Macrobenchmark/profile source exists; numbers and generated profile require a physical run |

## Source anchors

- [Composition root](../../app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt)
- [Production lifecycle](../../app/src/main/java/com/halo/app/HaloRuntime.kt)
- [HostGraph](../../app/src/main/java/com/halo/app/di/HostGraph.kt)
- [AppShell](../../app/src/main/java/com/halo/app/ui/shell/AppShell.kt)
- [Tool session](../../runtime/cognition/src/main/java/com/halo/runtime/cognition/session/CognitiveSession.kt)
- [Android storage](../../host-android/src/main/java/com/halo/host/android/AndroidStorage.kt)
- [Provider configuration writes](../../app/src/main/java/com/halo/app/ui/screens/settings/IntelligenceViewModel.kt)
- [Room declaration](../../app/src/main/java/com/halo/app/data/AppDatabase.kt)
- [Glass surface](../../app/src/main/java/com/halo/app/ui/glass/components/HaloGlassSurface.kt)
- [Capability catalog](../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/capability/HeyCyanCapabilityCatalog.kt)
- [Audio source](../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/HeyCyanAudioSource.kt)
- [Workflow](../../.github/workflows/android.yml)

## Document treatment

Active summaries/specification entry points were rewritten around inspected source.
Remaining vision/spec/design/checklist documents are explicitly targets, not conformance claims.
Historical protocol notes retain their recorded findings with prominent scope notices;
their assertions are not promoted to current truth. Known conflicting active protocol
summaries were replaced, not merely given a disclaimer.

Raw research/evidence files, third-party vendor documents, protected agent instructions
and hidden generated task artifacts were preserved. Git history retains removed old prose.
The docs checker intentionally excludes hidden tool metadata and preserved evidence/vendor
files. It checks labels and inline local link targets, not semantic truth or external URLs.

## Design update

The user mockup is preserved unchanged in docs/03-design/reference; the
[workspace guide](../03-design/HALO-WORKSPACE-DESIGN.md) defines it as the redesign reference.
Light/dark themes, spatial-cell presence and Notebook are targets. Device names/content
shown in the picture remain illustrative. Current routes and current renderer are
documented separately. The next-phase plan was updated accordingly.

The subsequent Notebook blueprint is preserved as a proposed user design with separate
delivery/reconciliation notes. It is not a source for current schema or route claims.
Its storage-versus-memory distinction, Working Set and provenance requirements now
inform the plan; its no-mechanical-ring direction refines the earlier halo proposal.

## Remaining code/integration work

Credential storage/backup handling, CI task setup, catalog/decoder consistency, wearable
audio wiring, tool-capability integration, durable personal memory, reachable developer
tools and whole-app accessibility need implementation/acceptance work. They were not fixed
by changing docs. The proposed phase and research log track the next decisions.

## Validation

`python tools/check_docs.py` passed: 145 managed documents, 10 preserved evidence/vendor
documents, zero errors and zero unresolved historical local links.
The documentation/tool patch passed `git diff --check` with Windows CRLF recognized
as line endings and scope restricted to Markdown and the documentation checker.
Mockup source and repository copy were compared by SHA-256 and matched.
These checks do not establish that the Android app builds or works on hardware.
