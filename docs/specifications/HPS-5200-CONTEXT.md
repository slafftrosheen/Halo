---
title: HPS-5200-CONTEXT
status: Draft
maturity: Alpha
---

> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# HPS-600: Context Specification

Context is Halo's biggest differentiator. Instead of just knowing the conversation, Halo knows the environment.

## Context Object
Generated every second. Immutable.
Contains: Time, Location, Activity, Battery, Current App, Media, Bluetooth, Wearable, Calendar, Weather, Movement, Focus, Network, Charging, Sleep, Noise Level.

## Context Score
Everything gets scores.
Instead of `Walking: true`, it's `Walking: 0.82`. Much richer.
