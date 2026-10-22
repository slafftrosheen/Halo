# Issue #45 — Home tools and attachments

Implement the approved GitHub #45 contract on top of the conversation-first Home
delivered in c73ac1ce. Finish all source and regression cases before building/testing.
Use existing BrandBook tokens, EN/LV/RU copy and 48dp native controls.

1. Add one typed, host presentation catalog: stable entry ID, group, icon, resource
   labels, canonical tool/route binding, mode/offline eligibility, readiness reason,
   recovery and intended UI action. This is a projection, never another registry,
   executor or authorizer. Read ToolRegistry, ToolAvailabilityResolver, capability and
   permission truth, selected answer-route configuration and actual web-owner access.
2. Replace the Home Tools popup with a grouped, scrollable bottom sheet and concise
   detail/recovery view. Keep Create / Find / Work / On this phone discoverable. Only
   explicit use prepares a draft, opens an existing editor/form, or changes Voice Mode
   through its current owner. Recheck readiness on use; cancel pending UI work on dismiss.
3. Keep one attachment/context sheet. Add File/PDF and Audio entry points through
   existing Workspace import/Inbox owners, retain Camera/Photos/Notebook, and explain
   selection and AI-readability limits. Readable files can use existing context binding;
   PDF pages stay in Study and audio stays saved audio until a real transcription path
   exists. Screen/Ink never fabricates a selected source. Preserve source provenance,
   removal, denial and privacy controls.
4. Distinguish assistant-callable operations from editor-only support and Android's
   human selection boundary. Existing image generation/editing opens Notebook so the
   user can choose a note/Canvas editor, with an honest Limited state. Planned calculations/Python expose purpose only,
   no Run action or tool dispatch. Maps prepare intent for the canonical handoff tool;
   the UI never claims navigation started.
5. Add source-first cases for missing/disabled tools, capability/permission changes,
   Private/Kids/offline/provider states, planned-entry non-execution, cancellation,
   targeted recovery, 360dp/200% text and attachment draft retention. Independently
   review the complete source, then run serial build/focused regressions and recorded
   host renders. Use a connected phone when available and report separate evidence.
6. Publish only verified source to main with [skip ci]. Record unresolved physical,
   provider and network qualification under #43; never promote an untested route to PASS.

## Scope boundaries

Do not add a Python executor, map engine, provider integration, image model, alternate
permission/approval policy or new cloud-sharing profile in this slice. #47 retains the
profile/dispatch enforcement work and #54 the broader assistant-operability gaps.
No network/model probe runs merely because Tools opens. Credentials are never copied
into catalog state, logs or evidence.

## Future Python qualification contract

An eventual interpreter requires a separate isolated execution environment, filesystem
and network denied by default, bounded CPU/RAM/time, deterministic cancellation and
process cleanup, explicit user-triggered execution, a vetted package policy and typed
results/provenance. Generated code and imported files are untrusted data. A catalog
preview is not evidence that this environment exists or has been qualified.
