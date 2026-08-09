package com.halo.drivers.heycyan.codec

import java.io.File

interface CaptureSessionStore {
    fun save(capture: BleCapture): File

    fun load(file: File): BleCapture
}