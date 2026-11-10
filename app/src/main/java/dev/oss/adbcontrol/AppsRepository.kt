package dev.oss.adbcontrol

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppsRepository(private val context: Context) {

    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { ai -> runCatching { toEntry(pm, ai) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun loadRemote(
        shell: suspend (String) -> String?,
        bridgeDir: File? = null
    ): List<AppEntry> =
        withContext(Dispatchers.IO) {
            val packages = shell("pm list packages -f") ?: return@withContext emptyList()
            val user = (shell("pm list packages -3") ?: "").lines()
                .mapNotNull { it.trim().removePrefix("package:").takeIf { p -> p.isNotEmpty() } }
                .toSet()
            val disabled = (shell("pm list packages -d") ?: "").lines()
                .mapNotNull { it.trim().removePrefix("package:").takeIf { p -> p.isNotEmpty() } }
                .toSet()
            val versions = HashMap<String, String>()
            var current: String? = null
            (shell("dumpsys package packages | grep -E \"Package \\[|versionName=\"") ?: "").lines()
                .forEach { line ->
                    val text = line.trim()
                    if (text.startsWith("Package [")) {
                        current = text.substringAfter('[').substringBefore(']')
                    } else if (text.startsWith("versionName=")) {
                        current?.let { versions[it] = text.removePrefix("versionName=").trim() }
                    }
                }

            val apps = packages.lines().mapNotNull { line ->
                val rest = line.trim().removePrefix("package:")
                val index = rest.lastIndexOf('=')
                if (index <= 0) return@mapNotNull null
                val pkg = rest.substring(index + 1)
                if (pkg.isBlank()) return@mapNotNull null
                AppEntry(
                    packageName = pkg,
                    label = prettyLabel(pkg),
                    version = versions[pkg]?.takeIf { it.isNotBlank() && it != "null" } ?: "",
                    system = !user.contains(pkg),
                    enabled = !disabled.contains(pkg),
                    sizeBytes = 0,
                    updatedAt = 0,
                    icon = null
                )
            }.toMutableList()

            if (bridgeDir != null) mergeBridge(apps, bridgeDir)
            apps.sortedBy { it.label.lowercase() }
        }

    private fun mergeBridge(apps: MutableList<AppEntry>, dir: File) {
        val jsonFile = File(dir, "apps.json")
        if (!jsonFile.isFile) return
        val array = runCatching { JSONArray(jsonFile.readText()) }.getOrNull() ?: return
        val entries = HashMap<String, JSONObject>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            entries[obj.optString("package")] = obj
        }
        val iconsDir = File(dir, "icons")
        for (i in apps.indices) {
            val app = apps[i]
            val obj = entries[app.packageName] ?: continue
            val label = obj.optString("label")
                .takeIf { it.isNotBlank() && it != "null" } ?: app.label
            val version = obj.optString("version")
                .takeIf { it.isNotBlank() && it != "null" } ?: app.version
            val system = if (obj.has("system")) obj.optBoolean("system", app.system) else app.system
            val size = obj.optLong("sizeBytes", 0L)
            val iconFile = File(iconsDir, "${app.packageName}.png")
            val icon = if (iconFile.isFile) {
                runCatching { BitmapFactory.decodeFile(iconFile.absolutePath)?.asImageBitmap() }.getOrNull()
            } else null
            apps[i] = app.copy(
                label = label,
                version = version,
                system = system,
                sizeBytes = if (size > 0) size else app.sizeBytes,
                icon = icon
            )
        }
    }

    private fun prettyLabel(pkg: String): String {
        val last = pkg.substringAfterLast('.')
        return last.split('_', '-').filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            .ifEmpty { pkg }
    }

    private fun toEntry(pm: PackageManager, ai: ApplicationInfo): AppEntry {
        val info = pm.getPackageInfo(ai.packageName, 0)
        val icon = runCatching {
            ai.loadIcon(pm).toBitmap(width = 96, height = 96).asImageBitmap()
        }.getOrNull()
        return AppEntry(
            packageName = ai.packageName,
            label = ai.loadLabel(pm).toString(),
            version = info.versionName ?: "",
            system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            enabled = ai.enabled,
            sizeBytes = File(ai.sourceDir ?: "").length(),
            updatedAt = info.lastUpdateTime,
            icon = icon
        )
    }
}
