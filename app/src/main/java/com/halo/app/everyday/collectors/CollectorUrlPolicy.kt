package com.halo.app.everyday.collectors

import com.halo.sdk.provider.PublicNetworkPolicy
import java.net.InetAddress
import okhttp3.Dns

/** Collector URLs share the transport's public-network boundary. Parsing never resolves DNS. */
object CollectorUrlPolicy {
    fun validate(value: String) = PublicNetworkPolicy.validate(value.also {
        require(it.length <= 4096) { "Source URL is too long" }
    })
    fun publicAddress(address: InetAddress) = PublicNetworkPolicy.publicAddress(address)
    fun dns(delegate: Dns = Dns.SYSTEM) = PublicNetworkPolicy.dns(delegate)
}
