---
title: HPS-8100-SDK
status: Draft
maturity: Alpha
---

> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# HPS-301: SDK Specification (HAL)

Hardware Abstraction Layer (HAL). Everything uses interfaces.

## Driver
Must implement:
* Discovery
* Connection
* Streaming
* Capabilities
* Diagnostics

Never AI. Never UI.

## Provider
Must implement:
* Chat
* Vision
* Streaming
* Embeddings
* Metadata

## Plugin
Must implement:
* Manifest
* Capabilities
* Commands
* Events
* Configuration
* Permissions

## Runtime API
Every engine receives: `Context`, `CommandBus`, `QueryBus`, `EventBus`, `Logger`, `Configuration`, `Clock`.
Nothing Android.
