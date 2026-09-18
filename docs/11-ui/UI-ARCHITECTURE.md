# Current UI architecture

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

Redesign target: [the user mockup](../03-design/HALO-WORKSPACE-DESIGN.md), including
light/dark themes, spatial-cell presence and a new Notebook destination. Current
implementation below is distinct from that target.

[AppShell](../../app/src/main/java/com/halo/app/ui/shell/AppShell.kt) registers Compose
destinations and obtains ViewModels from ManualDependencyGraph. ViewModels expose flows
and use runtime/session/provider/device services; a universal UseCase layer is not present.

Shared glass components coexist with older ui/components and theme files. Material3
primitives are used. The previous mandatory loading/error fields and “no raw Material3”
statements were not descriptions of all current screens.

HomeUiMapper combines speech/session state. HaloPresenceProjection also maps speech/runtime
state. HaloStateOrb takes a state enum, not measured audio or task-progress input.

See [route inventory](UI-IMPLEMENTATION-STATUS.md). Accessibility, reduced motion, 60fps,
leak freedom and complete state coverage are acceptance targets requiring verification.
