package dev.oss.adbcontrol

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CytProfile(
    val id: String,
    val title: String,
    val os: String,
    val brand: String
)

/** Bundled CleanYourTV profiles (examples/<model>/debloat.txt) from Gathin23/cleanyourtv. */
class CytRepository(private val context: Context) {

    private val root = "cleanyourtv/examples"
    private var cachedProfiles: List<CytProfile>? = null
    private val textCache = HashMap<String, String>()

    suspend fun profiles(): List<CytProfile> = withContext(Dispatchers.IO) {
        cachedProfiles?.let { return@withContext it }
        val ids = context.assets.list(root)?.toList() ?: emptyList()
        val list = ids.mapNotNull { id ->
            val text = readText(id) ?: return@mapNotNull null
            var title = id
            var os = ""
            var brand = ""
            runCatching {
                context.assets.open("$root/$id/tv.json").bufferedReader().use { reader ->
                    val obj = JSONObject(reader.readText())
                    title = obj.optString("tv", id)
                    os = obj.optString("os")
                    brand = obj.optString("brand")
                }
            }
            textCache[id] = text
            CytProfile(id, title, os, brand)
        }.sortedBy { it.title.lowercase() }
        cachedProfiles = list
        list
    }

    suspend fun packages(profile: String): List<String> = withContext(Dispatchers.IO) {
        val text = textCache[profile] ?: readText(profile) ?: return@withContext emptyList()
        parse(text)
    }

    suspend fun match(adb: AdbManager): String? = withContext(Dispatchers.IO) {
        val model = adb.shell("getprop ro.product.model")?.trim()?.lowercase().orEmpty()
        val device = adb.shell("getprop ro.product.device")?.trim()?.lowercase().orEmpty()
        val brand = adb.shell("getprop ro.product.brand")?.trim()?.lowercase().orEmpty()
        if (model.isEmpty() && device.isEmpty()) return@withContext null

        var best: Pair<Int, String>? = null
        for (profile in profiles()) {
            val text = (textCache[profile.id] ?: readText(profile.id)?.also { textCache[profile.id] = it })
                ?.lowercase() ?: continue
            var score = 0
            if (model.isNotEmpty()) {
                score += 8 * Regex(Regex.escape(model)).findAll(text).count()
            }
            if (device.isNotEmpty()) {
                score += 8 * Regex(Regex.escape(device)).findAll(text).count()
            }
            if (brand.isNotEmpty()) {
                score += 2 * Regex(Regex.escape(brand)).findAll(text).count()
            }
            if (model.isNotEmpty() && profile.id.lowercase().contains(model)) score += 10
            if (score > 0 && (best == null || score > best.first)) {
                best = score to profile.id
            }
        }
        best?.second
    }

    private fun readText(profile: String): String? = runCatching {
        context.assets.open("$root/$profile/debloat.txt").bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun parse(text: String): List<String> {
        val out = LinkedHashSet<String>()
        for (line in text.lineSequence()) {
            val cleaned = line.removePrefix("\uFEFF").substringBefore('#').trim()
            if (cleaned.isEmpty()) continue
            val pkg = cleaned.split(Regex("\\s+")).firstOrNull() ?: continue
            if (pkg.contains('.') && pkg.all { it.isLetterOrDigit() || it == '.' || it == '_' }) {
                out += pkg
            }
        }
        return out.toList()
    }
}
