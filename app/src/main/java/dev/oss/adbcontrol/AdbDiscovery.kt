package dev.oss.adbcontrol

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.Inet4Address

/**
 * Minimal mDNS discovery for Android's Wireless Debugging endpoints.
 * Service types: _adb-tls-pairing._tcp and _adb-tls-connect._tcp
 */
class AdbDiscovery(
    context: Context,
    private val onEndpoints: (Endpoint?, Endpoint?) -> Unit
) {
    data class Endpoint(val host: String, val port: Int)

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var pairing: Endpoint? = null
    private var connect: Endpoint? = null

    fun start() {
        discover("_adb-tls-pairing._tcp", pairing = true)
        discover("_adb-tls-connect._tcp", pairing = false)
    }

    @Suppress("DEPRECATION")
    private fun discover(type: String, pairing: Boolean) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType != type) return
                runCatching {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val addresses = serviceInfo.hostAddresses
                            val address = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull()
                            val host = address?.hostAddress ?: return
                            val endpoint = Endpoint(host, serviceInfo.port)
                            if (pairing) this@AdbDiscovery.pairing = endpoint
                            else this@AdbDiscovery.connect = endpoint
                            onEndpoints(this@AdbDiscovery.pairing, this@AdbDiscovery.connect)
                        }
                    })
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        listeners += listener
        runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stop() {
        listeners.forEach { listener -> runCatching { nsd.stopServiceDiscovery(listener) } }
        listeners.clear()
    }
}
