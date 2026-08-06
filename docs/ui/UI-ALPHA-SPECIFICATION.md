Status: ACTIVE
Version: Alpha v0.1
Priority: Critical

Purpose

Define every user-facing screen required for Halo Alpha,
its responsibilities,
expected behaviour,
required runtime integration,
and acceptance criteria.

No additional screens should be added before Alpha unless absolutely required.

All screens must use the existing design system and shared Halo UI components.

Every screen must have:

- ViewModel
- immutable UiState
- StateFlow
- Loading state
- Error state
- Empty state
- Pull-to-refresh (where applicable)

---

# Navigation

```
Bottom Navigation

Home

Timeline

Console

Settings
```

Developer Mode enables

```
Diagnostics
```

No additional navigation items.

---

# Screen 1

# HOME

Purpose

Runtime entry point.

Mission Control.

Primary interaction surface.

---

## Layout

```
--------------------------------------------------

HALO

Runtime Ready

Listening ●

-----------------------------------

Current Runtime Status

Speech

Ready

Reasoning

Ready

Planning

Ready

Execution

Ready

Providers

Connected / Offline

-----------------------------------

Primary Action

[ BIG MICROPHONE BUTTON ]

Tap to speak

-----------------------------------

Recent Activity

Last command

Last execution

-----------------------------------

Quick Status

Battery

Network

Current provider

-----------------------------------
```

---

## Responsibilities

Show runtime state.

Allow manual microphone trigger.

Display last interaction.

Display current runtime status.

Never be empty.

---

## Uses

```
RuntimeHealth

RuntimeState

SpeechState

ProviderState

TimelinePreview
```

---

## Acceptance

```
Runtime state visible

Mic trigger functional

Status updates live

No placeholder content

No developer jargon
```

---

# Screen 2

# TIMELINE

Purpose

Visualize cognition.

---

## Layout

```
Speech

↓

Interpretation

↓

Goal

↓

Plan

↓

Execution

↓

Response
```

Each event

```
Timestamp

Stage

Duration

Status

Expand

Details
```

---

## Event Card

```
Microphone

"Play Spotify"

Latency

42 ms

Status

Success
```

---

## Expand

Shows

```
Raw observation

Intent

Entities

Goal

Plan

Capability

Execution

Duration

Confidence
```

---

## Uses

```
TimelineRepository

TimelineEvents

Blackboard
```

---

## Acceptance

```
Realtime updates

Auto scroll

Expandable events

Search ready

Filter ready
```

---

# Screen 3

# CONSOLE

Purpose

Runtime inspection.

Never hidden.

---

Tabs

```
Runtime

Engines

Blackboard

Providers

Logs

Performance
```

---

## Runtime

Displays

```
Runtime

State

Health

Uptime

Memory

CPU

Version

Thread Count
```

---

## Engines

Each card

```
Engine

Health

Latency

State

Queue

Events

Restart
```

---

## Blackboard

Shows

```
Current Goals

Current Plans

Observations

Thoughts

Memory

Knowledge
```

---

## Providers

Shows

```
Whisper

Healthy

Latency

Loaded Model

Memory

-----------------

Ollama

Connected

Latency

Context

Model
```

---

## Logs

Filter

```
Speech

Runtime

Planning

Execution

Provider

Errors
```

Search.

Copy.

Share.

---

## Performance

```
CPU

RAM

Battery

FPS

Speech Latency

Reasoning Latency

Planning Latency

Execution Latency
```

---

## Acceptance

Everything updates through

```
StateFlow
```

Never polling.

---

# Screen 4

# SETTINGS

Purpose

Configuration only.

---

Sections

```
Speech

Providers

Runtime

Developer

Privacy

About
```

---

Speech

```
Wake Word

Sensitivity

Auto Restart

Always Listening

Manual Listening
```

---

Providers

```
Whisper

Ollama

Future Providers
```

---

Runtime

```
Logs

Metrics

Animations

Experimental

Diagnostics
```

---

Developer

```
Developer Mode

Timeline Debug

Blackboard Debug

Engine Inspector
```

---

Privacy

```
Local Only

Permissions

Storage

Audio
```

---

About

```
Version

Commit

Build

License
```

---

# Developer Screen

Hidden unless enabled.

Purpose

Engineering.

---

Contains

```
Runtime Graph

Dependency Graph

Engine Lifecycle

Coroutine Jobs

Current Tasks

Current Blackboard

Providers

Metrics
```

---

# Splash Screen

```
HALO

Initializing

Runtime

Blackboard

Speech

Providers

Reasoning

Ready
```

Maximum duration

```
2 seconds
```

---

# Permission Flow

Only shown once.

```
Welcome

↓

Microphone

↓

Notifications

↓

Location (optional)

↓

Done
```

No unnecessary permissions.

---

# Error Screen

If runtime fails

```
Runtime Error

Restart Runtime

View Logs

Report

Continue Offline
```

Never crash.

---

# Empty States

Every empty screen explains itself.

Example

```
No cognitive events yet.

Try saying

"Hello Halo"

or

tap the microphone.
```

---

# Shared Components

Mandatory wrappers.

```
HaloCard

HaloSection

HaloMetric

HaloChip

HaloStatus

HaloTimelineItem

HaloEngineCard

HaloProviderCard

HaloButton

HaloFloatingMic

HaloDialog
```

No raw Material components directly inside screens.

---

# ViewModel Contract

Every screen

```
Screen

↓

ViewModel

↓

UseCase

↓

Runtime

↓

Repository

↓

Engine
```

Composable never accesses runtime directly.

---

# UI State Pattern

```
Loading

Ready

Empty

Error
```

No nullable UI.

---

# StateFlow

Every screen

```
StateFlow<UiState>
```

No LiveData.

No callbacks.

---

# Runtime States

Displayed everywhere

```
Initializing

Ready

Listening

Capturing

Recognizing

Reasoning

Planning

Executing

Speaking

Recovering

Stopped
```

---

# Animation Rules

Only functional animations.

Allowed

```
Fade

Scale

Pulse

Slide

Progress

Expand
```

Forbidden

```
Random glow

Particles

Heavy gradients

RGB effects

Neon

Excessive motion
```

---

# Typography

```
Inter

JetBrains Mono
```

Nothing else.

---

# Icons

```
Lucide

Material Symbols fallback
```

---

# Color Tokens

```
Background

Surface

Card

Primary

Secondary

Success

Warning

Danger

Info

Text Primary

Text Secondary
```

Never hardcode colors.

---

# Performance Budget

Home

```
<16ms recomposition
```

Timeline

```
1000 events

smooth scroll
```

Console

```
Realtime updates

without frame drops
```

---

# Alpha Acceptance

UI is complete when

✓ Runtime visible

✓ Mic works

✓ Timeline works

✓ Console useful

✓ Settings complete

✓ Empty states useful

✓ Errors recoverable

✓ No placeholder pages

✓ Shared components everywhere

✓ Stable 60 FPS

✓ Material 3 consistent

✓ Ready for wearable integration

---

# Components

## `HaloFloatingMic`

Not a FAB.

A **runtime state indicator**.

Instead of just a microphone icon, it's the visible state of the runtime.

```
○ Idle
◉ Listening
◎ Wake Word
⬤ Capturing
◐ Processing
◉ Speaking
```

The button itself changes state smoothly. You don't need a status label because the control *is* the runtime.
