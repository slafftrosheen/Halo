# Halo companion UI overhaul

Source changes completed 2026-10-01. This follows the provider stabilization work in the same working tree.

## Direction

A calm companion workspace: soft neutral backgrounds, one mint action accent, clear typography, opaque surfaces and quiet borders. Light, dark and system appearance use the same semantic roles. Glass compatibility components now resolve the active Material theme; the previous fixed near-black palette and separate typography are removed from their rendering paths. Legacy diagnostic primitives also follow the theme. Headers and screen gutters share a hierarchy; large displays constrain the application content to 1040 dp.

## Everyday flows

- Home keeps its composer outside the scrolling conversation and bounds multiline input. The resting orb greets the user; replies use a compact orb. Actions collects photo capture, image attachment, note creation and conversation controls. Connection/context metadata is available on request. Pending review is signposted on Actions, with review destinations preserved. Provider and microphone setup remain visible when required.
- Settings groups companion preferences, access/data, appearance and system integration. Appearance options have centered labels and radio-group semantics.
- Intelligence has a clear active conversation summary and whole-row destinations. Provider lists show connection essentials; configuration and detailed evidence stay on provider detail screens. Model filters can be expanded without being present on every visit.
- Workspace keeps creation and context actions in a compact toolbar instead of a repeated tile dashboard. Activity exposes processing history on request while leaving actionable updates visible.
- Bottom navigation and the wide-screen rail use the same route ownership, including all settings aliases. Tab changes save/restore destination state and avoid building duplicate stacks. Detail screens keep their parent tab selected.

## Native Android assistant window

The VoiceInteractionSession window now uses the same saved light/dark/system appearance as the full app, shared HaloSignalFace, typography, mint buttons, quiet border and rounded surface. The state copy is conversational and consistent with Home. Screen-context metadata appears only when context is included. Requests remain in the shared semantic renderer in COMPACT mode, inside a bounded scrolling area; the footer keeps Stop/Close and Open Halo available. The panel accounts for keyboard/navigation insets and limits its height to 560 dp and width to 720 dp. An input-request surface changes the orb to waiting; ordinary result surfaces do not. Status text has polite accessibility announcements. Composition and subscriptions follow the native session lifecycle; hiding or destroying the window stops orb motion and collection.

## Orb and interaction

The orb has a soft illuminated core, a gentle breathing ring and a small friendly face for ready/listening/speaking/success. Busy, waiting, warning, error and offline states retain state glyphs; progress is still based on supplied runtime progress. A press responds within a 90 ms animation. It uses the selected app appearance, not only the system theme. Screen-reader state descriptions remain explicit.

Reduced-motion and stopped lifecycle states do not start an orb animation loop. Compact markers are static. Legacy switches use Material controls with switch semantics and minimum touch targets. Cards receive touch feedback; icon buttons preserve at least 48 dp targets. Primary/error colors are paired with their appropriate content colors.

## Frame work removed at source

- Orb phase reads are confined to drawing and press reads to graphics layers. Brushes are cached; one phase replaces independent pulse/orbit animations. The previous per-frame composition reads and layered ring decorations are removed.
- Legacy surfaces use one clipped opaque Surface, removing stacked highlight/alpha layers and high elevation defaults.
- UI flow subscriptions use lifecycle-aware collection across screens and the shell.
- Streaming responses render ordinary text; Markdown parsing is deferred until the completed response. Completed formatted spans and Markdown line splitting are remembered.
- Workspace item filtering/collection choices and model-provider lookup are cached. Provider grouping is remembered. Learning visibility policy is read on entry/resume instead of every Home recomposition.
- Navigation no longer animates two full screens through overlapping transitions. Pulse and progress animations read their values only in drawing/layer phases; reduced-motion progress remains static.

## Verification boundary

Source inspection and Git whitespace review only. No Gradle, compilation, tests, lint, emulator, device session or benchmark was run under the existing source-only restriction. No measured frame-time improvement or screenshot-level verification is claimed.

When runtime verification is enabled, review light/dark/system appearance, large font sizes, narrow phones and tablets, grayscale hierarchy, contrast, spacing and elevation across all destinations. Exercise long replies while typing, voice capture, scrolling, tab restoration, provider failures and reduced motion. Measure frame times on a physical device during those flows; target smooth frame delivery at the device refresh rate and use traces to identify any remaining main-thread or drawing bottlenecks.
