package dev.oss.adbcontrol

import android.content.Context
import android.os.Build
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

data class DeviceInfo(
    val id: String,
    val label: String,
    val serial: String,
    val host: String,
    val port: Int,
    val isSelf: Boolean
)

data class AdbUiState(
    val devices: List<DeviceInfo> = emptyList(),
    val activeId: String? = null,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: String = "",
    val pairingEndpoint: AdbDiscovery.Endpoint? = null,
    val connectEndpoint: AdbDiscovery.Endpoint? = null
) {
    val active: DeviceInfo? get() = devices.firstOrNull { it.id == activeId }
    val serial: String get() = active?.serial ?: ""
    val label: String get() = active?.label ?: ""
}

/**
 * Multi-device ADB manager.
 *  - "self" device: self-pairing through Wireless Debugging (Android 11+).
 *  - network devices: classic ADB TCP (5555) or TLS connect port after pairing.
 */
class AdbManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)
    private val clients = mutableMapOf<String, Kadb>()
    private val shellMutex = Mutex()

    private val _state = MutableStateFlow(AdbUiState(devices = loadDevices()))
    val state: StateFlow<AdbUiState> = _state

    private var mdns: AdbDiscovery? = null

    fun startDiscovery() {
        if (mdns != null) return
        val discovery = AdbDiscovery(context.applicationContext) { pairing, connect ->
            _state.value = _state.value.copy(pairingEndpoint = pairing, connectEndpoint = connect)
        }
        mdns = discovery
        discovery.start()
    }

    fun stopDiscovery() {
        mdns?.stop()
        mdns = null
    }

    /** Pair and connect to this device using Wireless Debugging. */
    suspend fun pairSelf(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { KadbCert.ensureReady() }
        val endpoint = _state.value.pairingEndpoint
            ?: return@withContext Result.failure(
                IllegalStateException("لم يتم العثور على منفذ الاقتران. افتح Wireless debugging ← Pair device with pairing code.")
            )
        _state.value = _state.value.copy(busy = true, message = "جارٍ الاقتران…")
        try {
            Kadb.pair(endpoint.host, endpoint.port, code.trim())
            _state.value = _state.value.copy(message = "تم الاقتران، جارٍ الاتصال…")
            connectSelfInternal()
        } catch (t: Throwable) {
            _state.value = _state.value.copy(busy = false, message = "فشل الاقتران: ${t.message}")
            Result.failure(t)
        }
    }

    suspend fun connectSelf(): Result<Unit> = withContext(Dispatchers.IO) { connectSelfInternal() }

    private suspend fun connectSelfInternal(): Result<Unit> {
        runCatching { KadbCert.ensureReady() }
        _state.value = _state.value.copy(busy = true, message = "جارٍ الاتصال…")
        var last: Throwable? = null
        repeat(10) {
            val endpoint = _state.value.connectEndpoint
            if (endpoint != null) {
                try {
                    val client = Kadb.create(endpoint.host, endpoint.port, connectTimeout = 8000, socketTimeout = 8000)
                    val model = client.shell("getprop ro.product.model").output.trim().ifEmpty { Build.MODEL }
                    val serial = client.shell("getprop ro.serialno").output.trim().ifEmpty { "self" }
                    val device = DeviceInfo("self", model, serial, endpoint.host, endpoint.port, true)
                    clients["self"]?.close()
                    clients["self"] = client
                    _state.value = _state.value.copy(
                        devices = (_state.value.devices.filterNot { it.id == "self" } + device),
                        activeId = "self",
                        connected = true,
                        busy = false,
                        message = "متصل: $model"
                    )
                    return Result.success(Unit)
                } catch (t: Throwable) {
                    last = t
                }
            }
            delay(700)
        }
        _state.value = _state.value.copy(
            busy = false,
            connected = false,
            message = "تعذّر الاتصال: ${last?.message ?: "لم يتم العثور على منفذ الاتصال"}"
        )
        return Result.failure(last ?: IllegalStateException("no connect endpoint"))
    }

    /**
     * Add or reconnect a network device.
     * If [pairingPort] and [code] are provided, wireless-debugging pairing runs first.
     */
    suspend fun addDevice(
        host: String,
        port: Int,
        pairingPort: Int? = null,
        code: String? = null
    ): Result<DeviceInfo> = withContext(Dispatchers.IO) {
        runCatching { KadbCert.ensureReady() }
        _state.value = _state.value.copy(busy = true, message = "جارٍ الاتصال بـ $host:$port…")
        try {
            if (!code.isNullOrBlank() && pairingPort != null) {
                Kadb.pair(host, pairingPort, code.trim())
            }
            val client = Kadb.create(host, port, connectTimeout = 8000, socketTimeout = 8000)
            val model = client.shell("getprop ro.product.model").output.trim().ifEmpty { host }
            val serial = client.shell("getprop ro.serialno").output.trim()
            val id = "net:$host:$port"
            clients[id]?.close()
            clients[id] = client
            val device = DeviceInfo(id, model, serial, host, port, false)
            val devices = _state.value.devices.filterNot { it.id == id } + device
            saveDevices(devices)
            _state.value = _state.value.copy(
                devices = devices,
                activeId = id,
                connected = true,
                busy = false,
                message = "متصل: $model"
            )
            Result.success(device)
        } catch (t: Throwable) {
            _state.value = _state.value.copy(busy = false, message = "تعذّر الاتصال: ${t.message}")
            Result.failure(t)
        }
    }

    suspend fun selectDevice(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val device = _state.value.devices.firstOrNull { it.id == id }
            ?: return@withContext Result.failure(IllegalStateException("جهاز غير معروف"))
        if (clients[id] != null) {
            _state.value = _state.value.copy(activeId = id, connected = true, message = "متصل: ${device.label}")
            return@withContext Result.success(Unit)
        }
        if (device.isSelf) {
            startDiscovery()
            connectSelfInternal()
        } else {
            addDevice(device.host, device.port).map { }
        }
    }

    fun removeDevice(id: String) {
        clients.remove(id)?.close()
        val devices = _state.value.devices.filterNot { it.id == id }
        saveDevices(devices)
        val active = if (_state.value.activeId == id) null else _state.value.activeId
        _state.value = _state.value.copy(
            devices = devices,
            activeId = active,
            connected = active != null && clients[active] != null,
            message = ""
        )
    }

    suspend fun shell(command: String): String? = withContext(Dispatchers.IO) {
        val id = _state.value.activeId ?: return@withContext null
        val client = clients[id] ?: return@withContext null
        runCatching {
            shellMutex.withLock { withTimeout(25_000) { client.shell(command).output } }
        }.getOrNull()
    }

    fun activeDeviceId(): String? = _state.value.activeId

    suspend fun push(localFile: File, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val id = _state.value.activeId ?: return@withContext false
        val client = clients[id] ?: return@withContext false
        runCatching {
            client.push(localFile, remotePath)
            true
        }.getOrDefault(false)
    }

    suspend fun pull(remotePath: String, localFile: File): Boolean = withContext(Dispatchers.IO) {
        val id = _state.value.activeId ?: return@withContext false
        val client = clients[id] ?: return@withContext false
        runCatching {
            localFile.parentFile?.mkdirs()
            client.pull(localFile, remotePath)
            true
        }.getOrDefault(false)
    }

    fun close() {
        stopDiscovery()
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
    }

    private fun saveDevices(devices: List<DeviceInfo>) {
        val saved = devices.filterNot { it.isSelf }.map { device ->
            listOf(device.id, device.label.replace("|", " "), device.serial, device.host, device.port).joinToString("|")
        }.toSet()
        prefs.edit().putStringSet("devices", saved).apply()
    }

    private fun loadDevices(): List<DeviceInfo> {
        val saved = prefs.getStringSet("devices", emptySet()) ?: emptySet()
        return saved.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 5) return@mapNotNull null
            runCatching {
                DeviceInfo(
                    id = parts[0],
                    label = parts[1],
                    serial = parts[2],
                    host = parts[3],
                    port = parts[4].toInt(),
                    isSelf = false
                )
            }.getOrNull()
        }
    }
}
