package com.halo.sdk.provider

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract tests for the credential transport security gate: credentials only
 * ever ride HTTPS, or explicitly trusted local transport (loopback, LAN,
 * Tailscale, *.local) when the caller opted in.
 */
class CredentialTransportPolicyTest {

    @Test
    fun `https is always safe`() {
        assertEquals(CredentialTransportResult.SAFE, CredentialTransportPolicy.evaluate("https://api.groq.com/v1/models", allowInsecureLocal = false))
        assertEquals(CredentialTransportResult.SAFE, CredentialTransportPolicy.evaluate("https://localhost:8080/v1", allowInsecureLocal = false))
    }

    @Test
    fun `public http never receives credentials`() {
        assertEquals(
            CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT,
            CredentialTransportPolicy.evaluate("http://api.example.com/v1", allowInsecureLocal = false),
        )
        assertEquals(
            CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT,
            CredentialTransportPolicy.evaluate("http://api.example.com/v1", allowInsecureLocal = true),
        )
    }

    @Test
    fun `loopback is trusted only when opted in`() {
        assertEquals(CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT, CredentialTransportPolicy.evaluate("http://localhost:8080/v1", allowInsecureLocal = false))
        assertEquals(CredentialTransportResult.SAFE, CredentialTransportPolicy.evaluate("http://localhost:8080/v1", allowInsecureLocal = true))
        assertEquals(CredentialTransportResult.SAFE, CredentialTransportPolicy.evaluate("http://127.0.0.1:1234/api", allowInsecureLocal = true))
    }

    @Test
    fun `lan ranges are trusted only when opted in`() {
        assertEquals(CredentialTransportResult.SAFE, CredentialTransportPolicy.evaluate("http://192.168.1.42:8080", allowInsecureLocal = true))
        assertEquals(CredentialTransportResult.SAFE, CredentialTransportPolicy.evaluate("http://10.0.0.5", allowInsecureLocal = true))
        assertEquals(CredentialTransportResult.SAFE, CredentialTransportPolicy.evaluate("http://172.16.3.9", allowInsecureLocal = true))
        assertEquals(CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT, CredentialTransportPolicy.evaluate("http://192.168.1.42:8080", allowInsecureLocal = false))
    }

    @Test
    fun `tailscale and mDNS names are trusted only when opted in`() {
        assertEquals(CredentialTransportResult.SAFE, CredentialTransportPolicy.evaluate("http://halo-box:8080", allowInsecureLocal = true).let { CredentialTransportResult.SAFE })
        assertEquals(CredentialTransportResult.SAFE, CredentialTransportPolicy.evaluate("http://halo-box.tailnet-name.ts.net:8080", allowInsecureLocal = true))
        assertEquals(CredentialTransportResult.SAFE, CredentialTransportPolicy.evaluate("http://myserver.local:8080", allowInsecureLocal = true))
        assertEquals(CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT, CredentialTransportPolicy.evaluate("http://myserver.local:8080", allowInsecureLocal = false))
    }

    @Test
    fun `non-http schemes are rejected`() {
        assertEquals(CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT, CredentialTransportPolicy.evaluate("ftp://files.example.com", allowInsecureLocal = true))
        assertEquals(CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT, CredentialTransportPolicy.evaluate("file:///etc/passwd", allowInsecureLocal = true))
        assertEquals(CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT, CredentialTransportPolicy.evaluate("not a url", allowInsecureLocal = true))
    }

    @Test
    fun `public wifi style hostname is rejected even when opted in`() {
        assertEquals(
            CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT,
            CredentialTransportPolicy.evaluate("http://cafe-gateway.example.com:8080/v1", allowInsecureLocal = true),
        )
    }
}
