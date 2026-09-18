---
title: HPS-6000-KERNEL
status: Draft
maturity: Alpha
---

> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# HPS-6000: HAL Runtime Kernel (HRK)

## The Kernel
The Runtime is treated as a kernel (HRK). Everything else is a module.
Responsibilities include Service Lifecycle, Scheduling, Resource Management, Event Routing, Capability Routing, Permission Enforcement, Observability, Health Monitoring.

## Service Registry
Modules register themselves. The Runtime never directly imports implementations (like `WhisperSpeechRecognizer`). It asks the Service Registry for the best implementation of a service.

## Dependency & Capability Graphs
* **Dependency Graph:** Modules publish their dependencies. Validated at startup to prevent cycles.
* **Capability Graph:** Halo's secret weapon. Workflows ask the Capability Graph (e.g., "who can translate?") rather than a specific Provider. Drivers, Providers, and Plugins all contribute capabilities to this graph.

## Resource Manager
Runtime owns all resources (CPU, GPU, RAM, Storage, BLE, Mic, Camera). Modules request resources, and the Runtime approves or denies based on policies and current system state.

## Observability & Debugging
* **Runtime Observatory:** Real-time metrics (Events/sec, Memory, Battery rate).
* **Runtime Profiler:** Every workflow is profiled automatically to track latency across stages (Speech -> Intent -> Translation -> TTS).
* **Event Recorder & Replay Engine:** Records the entire Event Graph. Developers can replay exact event streams to reproduce bugs without needing the original hardware or API responses.

## The "Nothing Engine"
An engine whose sole purpose is deciding **not** to interrupt the user.
Evaluates urgency, attention, confidence, cognitive load, current activity. If the interruption cost is higher than the value of the information, nothing happens. Silence is a feature.

## Halo Identity Terminology
* Engine (formerly Service)
* Workflow (formerly Activity)
* Panel (formerly Fragment)
* Signal (formerly Notification)
* Control Center (formerly Settings)
* Capability Module (formerly Plugin)
* Adapter (formerly Driver)
* Intelligence Provider (formerly Provider)
* Kernel (formerly Runtime)
