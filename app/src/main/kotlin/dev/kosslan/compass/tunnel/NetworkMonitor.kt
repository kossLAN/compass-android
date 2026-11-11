package dev.kosslan.compass.tunnel

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dev.kosslan.compass.ffi.IpPrefix
import dev.kosslan.compass.ffi.UnderlyingNetwork
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class NetworkMonitor(
    private val connectivity: ConnectivityManager,
) {
    private val physical: Flow<LinkProperties?> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    private var current: Network? = null

                    override fun onLinkPropertiesChanged(
                        network: Network,
                        properties: LinkProperties,
                    ) {
                        current = network
                        trySend(properties)
                    }

                    override fun onLost(network: Network) {
                        if (network == current) {
                            current = null
                            trySend(null)
                        }
                    }
                }

            connectivity.requestNetwork(request, callback)
            awaitClose { connectivity.unregisterNetworkCallback(callback) }
        }

    val underlying: Flow<UnderlyingNetwork> =
        physical
            .map { it?.underlying() ?: UnderlyingNetwork(emptyList(), emptyList()) }
            .distinctUntilChanged()

    val privateDnsServer: Flow<String?> =
        physical.map { it?.privateDnsServerName }.distinctUntilChanged()

    val routedTunnel: Flow<String> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onLinkPropertiesChanged(
                        network: Network,
                        properties: LinkProperties,
                    ) {
                        properties.interfaceName?.let(::trySend)
                    }
                }

            connectivity.registerNetworkCallback(tunnelRequest, callback)
            awaitClose { connectivity.unregisterNetworkCallback(callback) }
        }
}

private val tunnelRequest: NetworkRequest =
    NetworkRequest
        .Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .build()

private val request: NetworkRequest =
    NetworkRequest
        .Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .build()

private fun LinkProperties.underlying() =
    UnderlyingNetwork(
        localAddresses =
            linkAddresses
                .filterNot { it.address.isLoopbackAddress || it.address.isLinkLocalAddress }
                .map {
                    IpPrefix(
                        address = it.address.hostAddress.orEmpty(),
                        prefixLength = it.prefixLength.toUByte(),
                    )
                },
        dnsServers = dnsServers.mapNotNull { it.hostAddress },
    )
