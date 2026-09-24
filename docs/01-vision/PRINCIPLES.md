# Halo Engineering Principles

Status: DESIGN TARGET. Reviewed 2026-09-24.

These core directives govern all architectural and implementation choices within the Halo repository.

1. **Runtime over host**: Halo is a deterministic Cognitive Runtime. The Runtime Kernel owns lifecycle, event bus, scheduling, DI, module registration, runtime state, and health monitoring. Hosts plug into it.
2. **Native before automation**: The host should use native OS APIs wherever possible. Structured integrations come next. Accessibility/UI automation is a fallback.
3. **Capabilities over device identities**: Evaluate devices by capabilities (AudioInput, ButtonInput, etc.), not identity (isHeyCyan, isMeta).
4. **Local first**: Local execution is the default direction.
5. **User owns data and models**: The workspace, memories, and configured models belong to the user.
6. **No mandatory account**: Halo operates without a required cloud or account login.
7. **No required helper apps**: Halo must not depend on external taskers or companion apps to fulfill core functionality.
8. **Providers are replaceable**: AI models are reasoning backends, not the runtime itself.
9. **Devices are replaceable**: Smart glasses, earbuds, and sensors participate through capabilities.
10. **Host OS is replaceable**: Architecture is portable. Android is just the first host.
11. **Actions must be observable and cancellable**: The user must always maintain control and visibility.
12. **Do not report dispatch as completion**: Firing an intent is not the same as verifying a task was completed.
13. **Context has provenance**: Interactions remember where they originated (e.g., wearable vs phone) and route responses accordingly.
14. **Workspace and memory remain user-manageable**: Users can inspect, export, and delete their cognitive history.
15. **External integrations are optional**: Third-party services extend Halo, but do not complete it.
16. **Real hardware evidence outranks assumptions**: Never promote protocol behavior from hypothesis to production without a reproducible real-device capture or authoritative documentation.
17. **Simplicity beats architectural theatre**: Before implementing a change, ask: *Does this make the cognitive runtime stronger, or am I only making it bigger?*
