# Orb launchers implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline. The user requires complete source and tests before executing builds/tests; their direct-main publication authorization applies.

**Goal:** Give Home, Screen and Mini Halo one quiet, accessible orb action contract that delegates to existing voice and Tools owners.

**Architecture:** Shared presence presentation owns only disclosure and chosen shortcut preferences. HomeViewModel carries a transient typed request to reveal the canonical Home Tools sheet; browsing/selection never executes a tool. Existing runtime, approval, audio, permission and Android navigation owners remain authoritative.

**Tech stack:** Kotlin, Compose Material3, existing AppearancePreferences, StateFlow, Robolectric/Roborazzi.

- [x] Add typed orb shortcut/group request and pure primary-action projection. Create, Find, Notebook and Phone are bounded configurable shortcuts. Notebook targets the existing Work/catalog entry. No arbitrary routes or actions in persisted configuration.
- [x] Extend AppearancePreferences with independent, asynchronously persisted shortcut selection, bounded decoding and write-generation protection. Keep all existing mode behavior. Empty shortcuts still leave All tools and voice/review/Stop available.
- [x] Add one shared action sheet and quiet launcher beside the supplied Open Ring. Idle orb opens the minimal chooser; active capture/work exposes existing Stop, speaking exposes existing interruption, review opens Activity. A separate labelled 48dp disclosure stays reachable while active. Default collapsed; transient disclosure collapses on background/state changes. No orbiting or overlay service. Narrow/large-text/reduced-motion/TalkBack use the same linear sheet, with inline chip-style satellites only where width/font permits.
- [x] Add a transient typed catalog-open request to HomeViewModel and optional initial group/entry in HomeToolsSheet. Consume only while Home is STARTED; cancel on explicit close. Reuse the complete existing Tools resolver/actions/recoveries. Cross-surface launch opens Home, preserving its draft and privacy/approval guards.
- [x] Integrate HomePresenceHeader, Screen and Mini Halo through the shared launcher. Remove Mini's separate executable My tools list; its discovery entry uses the shared contract. Project microphone-open copy from actual input capture, never a requested start. Keep critical Stop and Android permission setup available through existing callbacks.
- [x] Add EN/LV/RU resources and a state/layout prototype record for collapsed/open/capture/work/review/error/offline/reduced motion at360/412/tablet widths. No PL support claim beyond existing English fallback.
- [x] Complete pure policy, preference restart/race, catalog group handoff, lifecycle/dismissal, no-auto-execution, actual capture truth, compact/200% and native layout cases. Review the complete source in a clean context, then freeze it.
- [x] Run serial debug build and focused app regressions with native recording. Use3GB local heap after #45's measured2GB saturation, one worker/no parallel. Install/readback exact APK on connected phone and check named interactions/settings restoration. Record unqualified voice/provider/accessibility cases under #43. Commit evidence, push main without force, verify0/0clean, close #46 implementation and continue backlog.

## Boundaries

No new voice/session/permission/authorization owner, executor, execution state store, cloud request or foreground/overlay/AOD promise. User preferences are presentation data. An opened group is discovery intent only; capability readiness is re-read on Use by #45. Existing approval and Kids gates remain active. Profile enforcement stays #47, Screen card redesign #37 and full release acceptance #43.
