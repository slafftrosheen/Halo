package com.halo.host.ink

import com.halo.sdk.runtime.ink.*
import java.io.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Public material only, exchanged explicitly by the person using a local file/share surface. */
data class InkPairingBundle(val displayName: String, val endpoint: InkEndpoint, val certificate: X509Certificate) {
    init {
        require(displayName.length in 1..80 && displayName.none { it.isISOControl() })
        certificate.checkValidity()
        require(endpoint.deviceId == deviceId(certificate))
    }
    fun association() = InkAssociation(endpoint, InkTlsIdentity.fingerprint(certificate))
    companion object {
        fun deviceId(certificate: X509Certificate) = "ink-" + InkTlsIdentity.fingerprint(certificate)
    }
}

/** Import is validation only. Only DeviceSessionManager.ink.associate persists explicit trust. */
object InkPairingBundleCodec {
    const val MAX_BYTES = 20_000
    fun encode(bundle: InkPairingBundle): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use {
            it.writeInt(0x48495031) // HIP1
            it.writeUTF(bundle.displayName)
            it.writeInt(bundle.endpoint.width); it.writeInt(bundle.endpoint.height)
            it.writeInt(bundle.endpoint.rotationDegrees)
            it.writeBoolean(bundle.endpoint.pressure); it.writeBoolean(bundle.endpoint.tilt)
            it.writeBoolean(bundle.endpoint.eraser); it.writeBoolean(bundle.endpoint.hover)
            val certificate = bundle.certificate.encoded
            require(certificate.size in 1..16_384)
            it.writeInt(certificate.size); it.write(certificate)
        }
        bytes.toByteArray().also { require(it.size <= MAX_BYTES) }
    }
    fun decode(bytes: ByteArray): InkPairingBundle? {
        if (bytes.size !in 1..MAX_BYTES) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use {
                require(it.readInt() == 0x48495031)
                val name = it.readUTF()
                val width = it.readInt(); val height = it.readInt(); val rotation = it.readInt()
                val pressure = it.readBoolean(); val tilt = it.readBoolean(); val eraser = it.readBoolean(); val hover = it.readBoolean()
                val length = it.readInt(); require(length in 1..16_384 && length == it.available())
                val certificateBytes = ByteArray(length).also(it::readFully)
                val certificateInput = ByteArrayInputStream(certificateBytes)
                val certificate = CertificateFactory.getInstance("X.509").generateCertificate(certificateInput) as X509Certificate
                require(certificateInput.available() == 0)
                InkPairingBundle(name, InkEndpoint(InkPairingBundle.deviceId(certificate), pressure, tilt, eraser,
                    hover, width, height, rotation), certificate)
            }
        } catch (_: Exception) { null }
    }
}
