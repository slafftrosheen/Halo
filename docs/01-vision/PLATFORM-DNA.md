> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# HPS-3000: Platform DNA

This document outlines the philosophy expressed through engineering that makes Halo feel like Halo.

## HPS-3100: Design DNA
Halo is an environment, not just an assistant. The assistant is one workload.
**Intent First:** Voice, buttons, gestures all produce Intent. Intent drives the Runtime.
**Context First:** Without context, AI hallucinates. Context (location, time, activity, battery, connected devices) grounds the model.
**Interaction Model:** User -> Intent -> Workflow -> Capability -> Provider -> Result -> User. (AI is abstracted).

## HPS-3200: Capability System
Everything advertises capabilities instead of hardcoded integrations.
* **Driver:** Audio Input/Output, Buttons, Camera, Battery.
* **Provider:** Streaming, Vision, Reasoning, Embeddings.
* **Plugin:** Memory, Calendar, Translation, GitHub.
Runtime builds a Capability Graph and negotiates which service handles an intent based on scores (e.g., offline availability, priority).

## HPS-3300: Policy Engine
Rules are defined as policies, not hardcoded.
* **Privacy:** Never upload photos.
* **Battery:** Disable local LLM below 20%.
* **Meeting:** Voice only, no notifications.
* **Travel:** Prefer offline models.

## HPS-3400: Runtime Intelligence
Runtime continuously computes scores: Privacy (100 - local), Battery (82 - healthy), Attention (41 - walking), Confidence (92), Latency (18ms).

## HPS-3500: Attention Engine
Halo estimates user attention before acting.
* Signals: Motion, Phone Locked, Calendar, Audio, Walking, BLE.
* Result: Meeting -> Notifications delayed. Driving -> Voice only. Desk -> Long responses.

## HPS-3600 & HPS-3700: Trust & Explainability
Every action receives a Trust score (e.g., GTFS = 100, LLM = Generated).
Every answer has provenance. Tap "Explain" to see exactly what happened (Memory used, Network used, Provider). Halo produces evidence, not magic.

## HPS-3800 & HPS-3900: Orchestration & Service Mesh
* **Skill Orchestration:** Pipelines instead of giant prompts (Email -> Summarize -> Extract -> Notify).
* **Service Mesh:** Plugins never call each other directly. Everything routes securely through the Runtime Command Bus.

## HPS-4000: Marketplace Philosophy
No ads, no featured plugins, no sponsored providers. 
A Repository Index, similar to APT, Homebrew, or Cargo.

## HPS-4100: Engineering Economics
Every PR must declare its cost: Battery, Memory, CPU, Network, Storage, and Latency. Features that regress performance budgets fail CI.

## HPS-4200: Runtime Observatory
Real-time operational dashboard (Health, Context updated, Voice status, Workflows, Memory searching, Provider active, CPU %, Battery rate).

## HPS-4300 & HPS-4400: Digital Twin & Time Machine
* **Digital Twin:** Simulate entire devices (fake glasses, fake GPS, fake voice) in CI.
* **Time Machine:** Replay yesterday's event stream exactly as it happened for debugging.

## HPS-4500 & HPS-4600: CLI & Doctor
* **CLI:** `halo devices`, `halo providers`, `halo plugins`, `halo trace voice`.
* **Doctor:** `halo doctor` checks health of Runtime, Drivers, Audio, Memory, Privacy.
