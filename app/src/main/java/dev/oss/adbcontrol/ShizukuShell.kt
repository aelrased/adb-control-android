package dev.oss.adbcontrol

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Optional shell backend via Shizuku (https://shizuku.rikka.app/).
 * Gives shell-level commands without keeping our own ADB connection.
 * Used automatically when ADB is not connected.
 */
object ShizukuShell {
    private const val PERMISSION_REQUEST = 4421

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    val ready: Boolean get() = hasPermission()

    fun requestPermission() {
        if (!isRunning() || hasPermission()) return
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }
    }

    fun version(): String? = runCatching { Shizuku.getVersion().toString() }.getOrNull()

    private val newProcess: java.lang.reflect.Method? by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        }.getOrNull()
    }

    /** Runs a shell command as the shell uid and returns stdout+stderr. */
    fun run(command: String): String? = runCatching {
        val method = newProcess ?: return@runCatching null
        val process = method.invoke(
            null,
            arrayOf("sh", "-c", command),
            null,
            null
        ) as Process
        val out = process.inputStream.bufferedReader().use { it.readText() }
        val err = process.errorStream.bufferedReader().use { it.readText() }
        process.waitFor()
        (out + err).trim()
    }.getOrNull()
}
