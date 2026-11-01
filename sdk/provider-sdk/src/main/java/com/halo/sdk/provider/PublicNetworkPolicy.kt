package com.halo.sdk.provider

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.asContextElement

/** Public sources and model artifacts have no authority to reach a private network. No DNS in validate. */
object PublicNetworkPolicy {
    fun validate(value: String): HttpUrl {
        require(value.length <= 8192 && value.none { it.isISOControl() }) { "Invalid or oversized public URL" }
        val url = requireNotNull(value.toHttpUrlOrNull()) { "Enter an HTTPS URL" }
        require(url.scheme == "https" && url.port == 443) { "Public downloads require HTTPS on the standard port" }
        require(url.username.isEmpty() && url.password.isEmpty()) { "Credentials in URLs are not supported" }
        val host = url.host.trimEnd('.')
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") &&
            !host.endsWith(".internal") && !host.endsWith(".home.arpa")) { "Local network destinations are not supported" }
        // OkHttp can bypass Dns for literal IPs. Validate these without a hostname lookup.
        if (':' in host || host.all { it.isDigit() || it == '.' })
            require(publicAddress(InetAddress.getByName(host))) { "Destination address is not public" }
        return url.newBuilder().fragment(null).build()
    }

    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        if (bytes.size == 4) {
            val a = bytes[0]; val b = bytes[1]; val c = bytes[2]
            return !(a == 0 || a == 10 || a == 127 || a >= 224 ||
                a == 100 && b in 64..127 || a == 169 && b == 254 || a == 172 && b in 16..31 ||
                a == 192 && (b == 168 || b == 0 && c in setOf(0, 2) || b == 88 && c == 99) ||
                a == 198 && (b in 18..19 || b == 51 && c == 100) || a == 203 && b == 0 && c == 113)
        }
        if (bytes.size != 16 || (bytes[0] and 0xe0) != 0x20) return false
        if (bytes[0] == 0x20 && bytes[1] == 0x02) return false
        if (bytes[0] == 0x20 && bytes[1] == 0x01) {
            if (bytes[2] == 0x0d && bytes[3] == 0xb8) return false
            if (bytes[2] == 0 && (bytes[3] in setOf(0, 2) || (bytes[3] and 0xf0) in setOf(0x10, 0x20))) return false
        }
        return true
    }

    /** The connection uses this exact answer; there is no second lookup after validation. */
    fun dns(delegate: Dns = Dns.SYSTEM): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = delegate.lookup(hostname)
            if (addresses.isEmpty() || addresses.any { !publicAddress(it) }) throw UnknownHostException("Destination address is not public")
            return addresses
        }
    }
}

/** A missing host authority must not silently become an unrestricted network client. */
object DenyRemoteDispatchPolicy : RemoteDispatchPolicy {
    override val revision: Long = -1
    override fun acquire(request: RemoteDispatchRequest, expectedRevision: Long, recordActivity: Boolean,
        cancel: () -> Unit): RemoteDispatchLease = throw RemoteDispatchBlocked()
}

/** Carries the revision of an explicit queued action through WorkManager and redirect hops. */
data class RemoteRequestRevision(val value: Long)

const val MODEL_DOWNLOAD_OWNER = "model.download"
const val PUBLIC_COLLECTOR_OWNER = "collector.public"

const val MODEL_DOWNLOAD_BLOCKED_MESSAGE = "Download paused by the global mode or family settings. Enable Power with downloads allowed, then start the download again. Installed models and file imports remain available."

/** A multi-stage acquisition retains its accepted intent even during local verification between requests. */
fun RemoteDispatchPolicy.modelAcquisitionRevision(): Long = RemoteDispatchContext.provenance.get()?.revision
    ?: RemoteDispatchContext.intent.get()?.revision ?: revision

fun modelAcquisitionContext(revision: Long): kotlin.coroutines.CoroutineContext =
    RemoteDispatchContext.intent.asContextElement(RemoteDispatchIntent(
        RemoteDispatchRequest(MODEL_DOWNLOAD_OWNER, RemoteOperation.CONNECTED_SERVICE, setOf(RemoteDataCategory.ACCOUNT_METADATA)), revision))
