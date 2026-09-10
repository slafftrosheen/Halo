package com.halo.drivers.heycyan.protocol

interface HeyCyanCommandTransport {
    suspend fun send(bytes: ByteArray): Result<Unit>
}
