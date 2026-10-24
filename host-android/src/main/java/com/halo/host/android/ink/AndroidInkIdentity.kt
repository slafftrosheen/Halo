package com.halo.host.android.ink

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.halo.host.ink.InkTlsIdentity
import com.halo.sdk.runtime.ink.InkActivationPolicy
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.security.auth.x500.X500Principal

/** Private key is non-exportable in AndroidKeyStore. Registry stores only public certificate pins. */
class AndroidInkIdentity(private val activation: InkActivationPolicy = InkActivationPolicy.QUALIFICATION_PENDING,
    private val alias: String = ALIAS) {
    init { require(alias.startsWith("halo.ink.") && alias.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) }
    @Synchronized private fun keys(): KeyStore {
        check(activation.enabled()) { "Ink activation pending" }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            val now = System.currentTimeMillis()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    // Conscrypt supplies the TLS transcript digest to the protected signing key.
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    // TLS delegates padded transcript signing through RSA/ECB/NoPadding.
                    // AndroidKeyStore maps private-key ENCRYPT_MODE to PURPOSE_SIGN.
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setCertificateSubject(X500Principal("CN=halo ink"))
                    .setCertificateSerialNumber(BigInteger(128, java.security.SecureRandom()).max(BigInteger.ONE))
                    .setCertificateNotBefore(Date(now - 60_000L))
                    .setCertificateNotAfter(Date(now + 5L * 365 * 24 * 60 * 60 * 1000))
                    .build())
                generateKeyPair()
            }
        }
        return store
    }
    fun certificate(): X509Certificate = (keys().getCertificate(alias) as X509Certificate).also { it.checkValidity() }
    fun fingerprint(): String = InkTlsIdentity.fingerprint(certificate())
    fun context(confirmedPeerPins: Set<String>): SSLContext = InkTlsIdentity.context(keys(), null, confirmedPeerPins, keyAlias = alias)
    private companion object { const val ALIAS = "halo.ink.local.tls.v1" }
}
