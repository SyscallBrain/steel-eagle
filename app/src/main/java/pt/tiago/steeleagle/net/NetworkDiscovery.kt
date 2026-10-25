package pt.tiago.steeleagle.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import pt.tiago.steeleagle.core.net.NetProtocol
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.CopyOnWriteArrayList

/** A game advertised on the local network. */
data class LanService(
    val name: String,
    val host: String,
    val port: Int,
)

/**
 * NSD registration/discovery, plus the local IPv4 shown for the
 * manual-IP fallback. Discovery holds a multicast lock so mDNS works on Wi-Fi.
 */
class NetworkDiscovery(context: Context) {

    private val nsd = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifi = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val services = CopyOnWriteArrayList<LanService>()
    private val resolveLock = Any()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var resolveStartedAt = 0L

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    @Volatile
    var registrationError: String? = null
        private set

    @Volatile
    var discoveryError: String? = null
        private set

    fun games(): List<LanService> = services.sortedBy { it.name }

    /** First site-local IPv4 of an up, non-loopback interface. */
    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    /** Advertises this device as a Steel Eagle host; manual IP still works on failure. */
    fun register(port: Int): Boolean {
        val manager = nsd ?: return false
        unregister()
        registrationError = null
        val info = NsdServiceInfo().apply {
            serviceName = serviceName()
            serviceType = NetProtocol.SERVICE_TYPE
            this.port = port
            setAttribute("version", NetProtocol.VERSION.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                registrationError = "NSD ERROR $errorCode"
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Unit
            }
        }
        return try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            registration = listener
            true
        } catch (exception: Exception) {
            registrationError = exception.message ?: "NSD ERROR"
            false
        }
    }

    fun unregister() {
        val manager = nsd ?: return
        val listener = registration ?: return
        registration = null
        try {
            manager.unregisterService(listener)
        } catch (_: Exception) {
            Unit
        }
    }

    fun startDiscovery(): Boolean {
        val manager = nsd ?: return false
        if (discovery != null) return true
        discoveryError = null
        acquireMulticastLock()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryError = "NSD ERROR $errorCode"
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryError = "NSD ERROR $errorCode"
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!typeMatches(serviceInfo.serviceType)) return
                enqueueResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                services.removeAll { it.name == serviceInfo.serviceName }
            }
        }
        return try {
            manager.discoverServices(NetProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            discovery = listener
            true
        } catch (exception: Exception) {
            discoveryError = exception.message ?: "NSD ERROR"
            releaseMulticastLock()
            false
        }
    }

    private fun typeMatches(type: String?): Boolean =
        type?.removeSuffix(".") == NetProtocol.SERVICE_TYPE.removeSuffix(".")

    /** Queues found services: some stacks ignore resolve calls while busy. */
    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        val next = synchronized(resolveLock) {
            resolveQueue.addLast(serviceInfo)
            val now = System.currentTimeMillis()
            if (resolving && now - resolveStartedAt > RESOLVE_TIMEOUT_MS) {
                resolving = false
            }
            if (resolving) return
            resolving = true
            resolveStartedAt = now
            resolveQueue.removeFirstOrNull()
        } ?: return
        resolveNow(next)
    }

    @Suppress("DEPRECATION")
    private fun resolveNow(serviceInfo: NsdServiceInfo) {
        val manager = nsd
        if (manager == null) {
            finishResolve()
            return
        }
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                finishResolve()
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val host = resolved.host?.hostAddress
                if (host != null) {
                    val service = LanService(
                        name = resolved.serviceName ?: host,
                        host = host,
                        port = resolved.port,
                    )
                    services.removeAll { it.name == service.name }
                    services += service
                }
                finishResolve()
            }
        }
        try {
            manager.resolveService(serviceInfo, listener)
        } catch (_: Exception) {
            finishResolve()
        }
    }

    private fun finishResolve() {
        val next = synchronized(resolveLock) {
            val queued = resolveQueue.removeFirstOrNull()
            if (queued == null) {
                resolving = false
                null
            } else {
                resolveStartedAt = System.currentTimeMillis()
                queued
            }
        }
        if (next != null) resolveNow(next)
    }

    fun stopDiscovery() {
        services.clear()
        synchronized(resolveLock) {
            resolveQueue.clear()
            resolving = false
        }
        val manager = nsd ?: return
        val listener = discovery
        discovery = null
        if (listener != null) {
            try {
                manager.stopServiceDiscovery(listener)
            } catch (_: Exception) {
                Unit
            }
        }
        releaseMulticastLock()
    }

    fun close() {
        stopDiscovery()
        unregister()
        services.clear()
    }

    private fun acquireMulticastLock() {
        if (multicastLock != null) return
        multicastLock = try {
            wifi?.createMulticastLock("steeleagle-nsd")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.release()
        } catch (_: Exception) {
            Unit
        }
        multicastLock = null
    }

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 3_000L
    }

    private fun serviceName(): String {
        val ip = localIpv4()
        val suffix = ip?.substringAfterLast('.') ?: "1"
        return "Steel Eagle $suffix"
    }
}
