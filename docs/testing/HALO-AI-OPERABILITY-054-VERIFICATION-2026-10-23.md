# Assistant operability #54 — verification in progress

Base main: 559eda6da73e73e1440033b594d2da23a112cf34, fetched 0/0 before the source freeze. User requested complete source/regression batch first, then a focused build/test gate. No Actions.

## Source integration

Added versioned portable ToolOperability metadata and a review report over actual ToolRegistry descriptors/schemas. Existing ToolDescriptor security, capability requirements, execution timeout/cancellation and replay semantics remain authoritative. Discovery includes bounded result/source-version/replay descriptions. High-value Workspace/File/Studio/Canvas/Study operations have explicit domain owners and result ID schemas; their capabilities reuse existing workspace.read/write publication. No parallel engine, approval flow, Memory store or provider service.

Six typed Canvas actions use NotebookCanvasRepository, the editor's same owner: create, bounded paginated read, add/edit text, move and connect. Writes compare the board revision inside the same Room transaction. Linked source content/Ink/sketches cannot be overwritten through text editing. Card contents remain untrusted data. Studio and File insertion now require both source and destination versions.

Study's assistant start requires the observed Workspace source version. startExactMaterial separates context identity (including attachment hash) from actual Study citation revision, validates the resolved revision and checks original context identity inside session publication. Corrected false keyed-replay declarations on keyless note/screen/Study creators to NEVER_REPLAY.

Home binds functional Studio/File/Canvas/Study operations rather than only navigation. Image AI binds the existing Studio review action and remains explicitly review-required; no automatic generation/payment claim. Calculator/Python remain in development. Updated engineering constitution and added the reusable AI-controllable tool verification template and integration inventory.

## Source review and authored regressions

Independent read-only review found the attachment-note context/Study revision mismatch. Corrected before first gate and reviewed again with no remaining actionable findings. Regression sources cover attached-photo success, attachment mutation between resolution/publication, Canvas persisted edits/stale target/malformed inputs, actual registered contracts/discovery/replay, Home bindings, three typed cross-tool workflows and an isolated native Canvas/attached-Study owner case. Existing runtime policy/skill result categories include all new actions.

First integrated gate passed in 12m10s. Production/test compilation and both APK assemblies passed. All 100 selected desktop cases across 11 classes passed, zero failures/errors/skips. Runtime contract/discovery/registry/skill/policy checks passed in 1m3s: 26 cases across 7 classes, zero failures/errors/skips. No source changes while checks run.

Built app: 287287643 bytes, SHA-256 `25e747daac917f4c5de09e13230af49a2ee6210a5033532ed2820d1e3c7c45df`. Instrumentation APK: 1963558 bytes, SHA-256 `ed5fdc7171020809bf8ef429c53d8b031cfcb530398b885554f5822550ded434`. ZIP/Dex inspection confirms new Canvas and native operability classes are present. Neither package is installed.

## Acceptance boundaries

Latest ADB inventory is empty. Native fixture NOT_RUN; no new APK installation. Three typed semantic-owner workflows are authored, not actual natural-language/STT sessions. Actual spoken recognition, three repeated workflows on a physical on-device route and a consented Power provider, and real external-effect approval qualification remain pending. No private prompts, audio, model output or credentials are retained in repository evidence. #54 remains OPEN until its required real-route acceptance is demonstrated.

Compiled production trees: app/src/main `c1fef6355a831a6fdb3f8714c2dd3f3b72d170df`; runtime/cognition/src/main `1f20c2bb968c9bc09cbd92868f3d2cfcf7c5dfe3`. Logs, XML and manifest are retained in the issue54 artifact folder. No source changes after the integrated app assembly.
