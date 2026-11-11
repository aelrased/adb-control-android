package dev.oss.adbcontrol

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
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
                if (!info.serviceType.startsWith(type)) return
                resolve(info, type, pairing, attempts = 0)
            }

            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        listeners += listener
        runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun resolve(info: NsdServiceInfo, type: String, pairing: Boolean, attempts: Int) {
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (attempts < 3) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            resolve(serviceInfo, type, pairing, attempts + 1)
                        }, 1200)
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = if (Build.VERSION.SDK_INT >= 34) {
                        val addresses = serviceInfo.hostAddresses
                        (addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull())
                            ?.hostAddress
                    } else {
                        @Suppress("DEPRECATION")
                        serviceInfo.host?.hostAddress
                    } ?: return
                    val endpoint = Endpoint(host, serviceInfo.port)
                    if (pairing) this@AdbDiscovery.pairing = endpoint
                    else this@AdbDiscovery.connect = endpoint
                    onEndpoints(this@AdbDiscovery.pairing, this@AdbDiscovery.connect)
                }
            })
        }
    }

    fun stop() {
        listeners.forEach { listener -> runCatching { nsd.stopServiceDiscovery(listener) } }
        listeners.clear()
    }
}
