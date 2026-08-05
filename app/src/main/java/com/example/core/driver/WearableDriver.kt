package com.example.core.driver

interface WearableDriver {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun startMicrophone()
    suspend fun stopMicrophone()
    suspend fun playAudio(data: ByteArray)
    suspend fun vibrate()
    suspend fun batteryLevel(): Int
    suspend fun isConnected(): Boolean
}
