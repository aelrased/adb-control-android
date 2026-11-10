package dev.oss.adbcontrol

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Syncs real icons, labels and versions from a *remote* device by installing
 * our open-source bridge APK over ADB, running it, and pulling a tar archive.
 */
object RemoteSync {

    const val BRIDGE_PKG = "com.adbappcontrol.bridge"
    private const val REMOTE_FILES = "/sdcard/Android/data/$BRIDGE_PKG/files"

    suspend fun sync(
        context: Context,
        adb: AdbManager,
        deviceId: String,
        progress: suspend (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = bridgeDir(context, deviceId).apply { mkdirs() }
            progress("تجهيز الجسر…")
            val apk = File(context.cacheDir, "bridge.apk")
            context.assets.open("bridge.apk").use { input ->
                apk.outputStream().use { input.copyTo(it) }
            }

            if (!adb.push(apk, "/data/local/tmp/adbcontrol-bridge.apk")) {
                return@withContext Result.failure(IllegalStateException("فشل رفع الجسر إلى الجهاز"))
            }
            var install = adb.shell("pm install -r /data/local/tmp/adbcontrol-bridge.apk")
                ?: return@withContext Result.failure(IllegalStateException("لا يوجد اتصال"))
            if (!install.contains("Success")) {
                // The bridge may be installed with a different signing key (older build).
                // Uninstall and retry once with the current signature.
                adb.shell("pm uninstall $BRIDGE_PKG")
                install = adb.shell("pm install /data/local/tmp/adbcontrol-bridge.apk")
                    ?: return@withContext Result.failure(IllegalStateException("لا يوجد اتصال"))
            }
            if (!install.contains("Success")) {
                return@withContext Result.failure(IllegalStateException(install.trim()))
            }

            progress("قراءة أيقونات الجهاز…")
            adb.shell("rm -f $REMOTE_FILES/ready")

            var ready = false
            var attempt = 0
            while (!ready && attempt < 3) {
                adb.shell("am force-stop $BRIDGE_PKG")
                adb.shell("am start -n $BRIDGE_PKG/.MainActivity")
                repeat(20) {
                    if (!ready) {
                        val out = adb.shell("cat $REMOTE_FILES/ready")
                        if (out?.contains("\"apps\"") == true) ready = true else delay(700)
                    }
                }
                attempt++
                if (!ready) delay(1500)
            }
            if (!ready) {
                return@withContext Result.failure(IllegalStateException("انتهت المهلة قبل تجهيز الأيقونات"))
            }

            progress("سحب الأيقونات…")
            adb.shell("cd $REMOTE_FILES && tar -cf /data/local/tmp/adbcontrol-icons.tar icons apps.json")
            val tar = File(dir, "icons.tar")
            if (!adb.pull("/data/local/tmp/adbcontrol-icons.tar", tar)) {
                return@withContext Result.failure(IllegalStateException("فشل سحب الأرشيف"))
            }
            adb.shell("rm -f /data/local/tmp/adbcontrol-icons.tar /data/local/tmp/adbcontrol-bridge.apk")

            extractTar(tar, dir)
            tar.delete()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun bridgeDir(context: Context, deviceId: String): File =
        File(context.filesDir, "remote/" + deviceId.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    fun hasBridgeData(context: Context, deviceId: String): Boolean {
        val dir = bridgeDir(context, deviceId)
        return File(dir, "apps.json").isFile && File(dir, "icons").isDirectory
    }

    private fun extractTar(tar: File, dest: File) {
        TarArchiveInputStream(BufferedInputStream(FileInputStream(tar))).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/').split('/').last()
                    if (name.endsWith(".json") || name.endsWith(".png")) {
                        val parent = if (entry.name.startsWith("icons/")) {
                            File(dest, "icons").apply { mkdirs() }
                        } else {
                            dest
                        }
                        File(parent, name).outputStream().use { input.copyTo(it) }
                    }
                }
                entry = input.nextEntry
            }
        }
    }
}
