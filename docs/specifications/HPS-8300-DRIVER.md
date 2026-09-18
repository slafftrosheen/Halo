---
title: HPS-8300-DRIVER
status: Draft
maturity: Alpha
---

> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# HPS-900: Driver Contract

Drivers abstract the hardware. They never expose their underlying SDKs.

## Driver Interface
```kotlin
interface Driver {
    suspend fun connect()
    suspend fun disconnect()
    fun getCapabilities()
    fun getMicrophone()
    fun getSpeaker()
    fun getCamera()
    fun getButtons()
    fun getBattery()
    fun getFirmware()
    fun getDiagnostics()
}
```
SDK hidden forever. Drivers never know about AI or UI.
