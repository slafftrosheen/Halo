# HPS-8000: Development Roadmap

This document outlines the path from conceptual architecture to a fully functional Halo Platform.

## Sprint 0: Foundation
* Create the monorepo.
* Set up Gradle modules (Core, Runtime, Drivers, Providers, Plugins).
* Configure CI/CD.
* Publish the documentation site (versioned specifications).
* Establish coding standards and RFC process.

## Sprint 1: Platform Skeleton
* Implement the HAL Runtime Kernel (HRK).
* Implement Event, Command, and Query buses.
* Implement Service Registry and Lifecycle Management.
* Add foundational Logging and Diagnostics (Observatory).

## Sprint 2: SDKs & Contracts
* Finalize Driver SDK (Discovery, Connection, Events, Capabilities).
* Finalize Provider SDK (Streaming, Vision, Embeddings, Memory).
* Finalize Plugin SDK (Manifests, Permissions, Lifecycle, Commands).
* Build sample implementations and a Fake Device Simulator for testing.

## Sprint 3: First Working Vertical Slice
* **Driver:** Integrate `HeyCyan` driver.
* **Provider (Local):** Local `llama.cpp` provider.
* **Speech:** Whisper/Moonshine STT and Piper TTS.
* **Workflow:** "Press button -> Ask question -> Receive spoken answer."

Once Sprint 3 is complete, Halo ceases to be an idea and becomes a platform. Every subsequent feature (translation, memory, GitHub, automation) becomes another vertical slice built on the same proven foundation.
