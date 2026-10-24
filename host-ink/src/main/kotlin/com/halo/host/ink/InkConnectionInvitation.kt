package com.halo.host.ink

import java.net.InetAddress

/** Public local connection coordinates only. TLS + prior explicit pairing remain mandatory. */
data class InkConnectionInvitation(val sessionId: String, val address: String, val port: Int, val hostCertificateSha256: String) {
    init {
        require(sessionId.matches(Regex("[A-Za-z0-9_-]{1,200}")))
        require(port in 1..65535)
        require(hostCertificateSha256.matches(Regex("[a-f0-9]{64}")))
        // Literal IPv4 only in v1: never resolve an imported DNS name or contact a public host.
        require(address.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")))
        val numbers = address.split('.').map { it.toInt() }
        require(numbers.all { it in 0..255 } && numbers.joinToString(".") == address)
        val inet = InetAddress.getByAddress(numbers.map { it.toByte() }.toByteArray())
        require(inet.isSiteLocalAddress || inet.isLoopbackAddress || inet.isLinkLocalAddress)
    }
    fun encode(): ByteArray = "halo-ink-connect-v1\n$sessionId\n$address\n$port\n$hostCertificateSha256".toByteArray(Charsets.US_ASCII)
    companion object {
        const val MAX_BYTES = 1024
        fun decode(bytes: ByteArray): InkConnectionInvitation? {
            if (bytes.size !in 1..MAX_BYTES || bytes.any { it !in 0..127 }) return null
            return runCatching {
                val fields = bytes.toString(Charsets.US_ASCII).split('\n')
                require(fields.size == 5 && fields[0] == "halo-ink-connect-v1")
                InkConnectionInvitation(fields[1], fields[2], fields[3].toInt(), fields[4])
            }.getOrNull()
        }
    }
}
