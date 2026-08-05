---
title: HPS-8300-DRIVER
status: Draft
maturity: Alpha
---
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

