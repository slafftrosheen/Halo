# Halo Product Requirements Document

## FEATURE-070 / DAILY-ASSISTANT-001 — 2026-10-08

Home is conversation-first with a deterministic Today projection from existing Attention, agent journal, Inbox, Study, learning, routine and device/audio owners. One primary and at most three secondary items link to exact existing owners; a bottom sheet shows the rest. Private notification/suggestion bodies stay out of Home. Daily owns presentation priority only, with no inference, domain writes, scheduler, polling loop or Room migration. Active task progress remains on the existing Halo card. Automated gate passed: 604 tests (runtime SDK 48, cognition 216, app 340), zero failures/errors/skips; debug APK assembled. APK installation and process launch succeeded on the development phone. Keyguard remained active with the screen off, so the single manual feature pass is NOT RUN.

See [Daily Assistant implementation](../engineering/DAILY-ASSISTANT-001.md).

Status: DESIGN TARGET. Reviewed 2026-09-24.

Halo is a host-native personal cognitive runtime that connects a user's operating system, models, workspace and physical devices into one coherent assistant.

Halo remains useful as a phone assistant without any wearable attached. Wearables enhance its reach.

## Primary Surfaces

The product is defined around five primary navigational surfaces:

1. **Home**: Conversation-first Halo presence with a deterministic Today projection of current needs and next actions.
2. **Activity**: What happened and why (timeline of interactions, errors, and system events).
3. **Notebook**: The persistent cognitive workspace.
4. **Devices**: Where Halo can perceive and act (managing connected hardware capabilities).
5. **Settings**: How Halo behaves (provider configuration, permissions, preferences).

## User Outcomes

- **Ubiquitous Access**: Users can invoke their intelligence layer naturally from the phone, wearables, or ambient devices.
- **Contextual Continuity**: The system understands where the user is and what they are looking at, routing responses appropriately (e.g., earbud microphone to earbud speaker).
- **Persistent Memory**: The Notebook acts as an accessible, durable mind palace that is user-manageable and transparent.
- **Local Control**: The user owns their models and their data, choosing local or remote processing independently.
