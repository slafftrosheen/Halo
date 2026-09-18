> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# The Vision

Halo is a local-first wearable AI platform for Android.

It is **not** another chatbot and **not** tied to a specific AI provider or hardware vendor.

The wearable (HeyCyan, Meta, Bluetooth glasses, etc.) acts only as an input/output terminal. The Android phone performs all computation. No cloud services are operated by Project Halo.

## Primary Goal
Provide the best productivity-focused wearable assistant.

## Secondary Goal
Allow complete user freedom. Users choose their AI provider, speech engine, wake word engine, LLM, vision model, TTS, and plugins. Project Halo supplies only the orchestration layer.

## The North Star
Halo should evolve into a **Wearable Intelligence Platform**, resembling a microkernel operating system more than a traditional Android application. 
The "kernel" is the Runtime, which schedules work, manages engines, routes events, and enforces privacy. Everything else behaves like a replaceable module.
