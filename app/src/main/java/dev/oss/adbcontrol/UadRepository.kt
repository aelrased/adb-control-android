package dev.oss.adbcontrol

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UadInfo(
    val removal: String,
    val list: String,
    val description: String
) {
    val isUnsafe: Boolean get() = removal.equals("Unsafe", true)
    val isExpert: Boolean get() = removal.equals("Expert", true)
    val arabicLabel: String
        get() = when (removal.lowercase()) {
            "recommended" -> "موصى به — آمن للحذف"
            "advanced" -> "متقدم — للحذر"
            "expert" -> "خبير — قد يؤثر على النظام"
            "unsafe" -> "غير آمن — لا تحذفه"
            else -> removal
        }
}

/** Universal Android Debloater Next Generation list (bundled snapshot). */
class UadRepository(private val context: Context) {
    private var cache: Map<String, UadInfo>? = null

    suspend fun load(): Map<String, UadInfo> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }
        val map = HashMap<String, UadInfo>()
        runCatching {
            val text = context.assets.open("uad/uad_lists.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val obj = root.optJSONObject(pkg) ?: continue
                map[pkg] = UadInfo(
                    removal = obj.optString("removal", ""),
                    list = obj.optString("list", ""),
                    description = obj.optString("description", "")
                )
            }
        }
        cache = map
        map
    }

    suspend fun info(packageName: String): UadInfo? = withContext(Dispatchers.IO) {
        load()[packageName]
    }
}
