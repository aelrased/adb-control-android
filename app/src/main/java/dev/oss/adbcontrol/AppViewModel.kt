package dev.oss.adbcontrol

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppsRepository(application)
    private val cyt = CytRepository(application)
    private val adb = AdbManager(application)
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val adbState: StateFlow<AdbUiState> = adb.state

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(AppFilter.ALL)
    val sort = MutableStateFlow(AppSort.NAME)

    val monitoring = MutableStateFlow(prefs.getBoolean("monitoring", false))
    val beta = MutableStateFlow(prefs.getBoolean("beta", false))

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val toast = _toast

    val apkFolder: File
        get() = getApplication<Application>().getExternalFilesDir(null)
            ?: getApplication<Application>().filesDir

    init {
        refresh()
    }

    private fun isRemote(): Boolean {
        val active = adb.state.value.active
        return active != null && !active.isSelf
    }

    private fun remoteBridgeDir(): File? {
        val id = adb.activeDeviceId() ?: return null
        if (!isRemote()) return null
        return RemoteSync.bridgeDir(getApplication(), id)
    }

    fun hasRemoteIcons(): Boolean {
        val id = adb.activeDeviceId() ?: return false
        return isRemote() && RemoteSync.hasBridgeData(getApplication(), id)
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _apps.value = if (isRemote()) {
                repo.loadRemote({ adb.shell(it) }, remoteBridgeDir())
            } else {
                repo.load()
            }
            _loading.value = false
        }
    }

    private suspend fun reloadApps() {
        _apps.value = if (isRemote()) {
            repo.loadRemote({ adb.shell(it) }, remoteBridgeDir())
        } else {
            repo.load()
        }
    }

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection

    fun beginSelection(pkg: String) {
        _selection.value = setOf(pkg)
    }

    fun toggleSelection(pkg: String) {
        val next = _selection.value.toMutableSet()
        if (!next.add(pkg)) next.remove(pkg)
        _selection.value = next
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun selectAll(packages: List<String>) {
        _selection.value = packages.toSet()
    }

    /** Re-enable every app that is currently disabled (Disabled tab shortcut). */
    fun enableAllDisabled() {
        viewModelScope.launch {
            if (!adb.state.value.connected) {
                emit("هذا الإجراء يحتاج اتصال ADB")
                return@launch
            }
            val packages = _apps.value.filter { !it.enabled }.map { it.packageName }
            if (packages.isEmpty()) {
                emit("لا توجد تطبيقات معطّلة")
                return@launch
            }
            emit("جارٍ تفعيل ${packages.size} تطبيق…")
            var done = 0
            for (pkg in packages) {
                val out = adb.shell("pm enable $pkg")
                if (out != null && !out.contains("Exception") &&
                    !out.contains("Failure") && !out.contains("Error")
                ) done++
            }
            emit("تم تفعيل $done/${packages.size}")
            reloadApps()
        }
    }

    fun bulkDisable() = bulkShell("disable")
    fun bulkEnable() = bulkShell("enable")
    fun bulkClear() = bulkShell("clear")
    fun bulkClearCache() = bulkShell("clear_cache")
    fun bulkUninstall() = bulkShell("uninstall")

    private fun bulkShell(kind: String) {
        val packages = _selection.value.toList()
        if (packages.isEmpty()) return
        viewModelScope.launch {
            if (!adb.state.value.connected) {
                emit("هذا الإجراء يحتاج اتصال ADB")
                return@launch
            }
            if (kind == "clear_cache") {
                val sdk = adb.shell("getprop ro.build.version.sdk")?.trim()?.toIntOrNull() ?: 0
                if (sdk < 30) {
                    val out = adb.shell("pm trim-caches 999999999999")
                    emit(
                        if (out != null) "تم تنظيف كاش جميع التطبيقات (Android ${sdk} لا يدعم لكل تطبيق)"
                        else "فشل التنظيف"
                    )
                } else {
                    var cleared = 0
                    packages.forEach { pkg ->
                        val out = adb.shell("pm clear --cache-only --user 0 $pkg")
                        if (out != null && !out.contains("Failed") && !out.contains("Failure")) cleared++
                    }
                    emit("تم مسح كاش $cleared/${packages.size}")
                }
                clearSelection()
                reloadApps()
                return@launch
            }
            var done = 0
            for (pkg in packages) {
                val command = when (kind) {
                    "disable" -> "pm disable-user --user 0 $pkg"
                    "enable" -> "pm enable $pkg"
                    "clear" -> "pm clear --user 0 $pkg"
                    "clear_cache" -> "pm clear --cache-only --user 0 $pkg"
                    "uninstall" -> "pm uninstall --user 0 $pkg"
                    else -> continue
                }
                val output = adb.shell(command)
                if (output != null && !output.contains("Exception") &&
                    !output.contains("Failure") && !output.contains("Error")
                ) done++
            }
            emit("تم تنفيذ الإجراء على $done/${packages.size}")
            clearSelection()
            reloadApps()
        }
    }

    fun backupSelected() {
        val packages = _selection.value.toList()
        if (packages.isEmpty()) return
        viewModelScope.launch {
            emit("جارٍ إنشاء نسخة احتياطية (${packages.size} تطبيق)…")
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val name = "backup-$stamp.zip"
            val uri = withContext(Dispatchers.IO) { createBackup(packages, name) }
            emit(
                if (uri != null) "تم حفظ النسخة في ${Storage.displayPath}/$name"
                else "فشل إنشاء النسخة الاحتياطية"
            )
            clearSelection()
        }
    }

    private suspend fun createBackup(packages: List<String>, name: String): Uri? {
        val context = getApplication<Application>()
        val remote = isRemote()
        val appMap = _apps.value.associateBy { it.packageName }
        data class Group(val pkg: String, val parts: List<File>, val entry: org.json.JSONObject)

        val groups = mutableListOf<Group>()
        val tempDirs = mutableListOf<File>()

        for (pkg in packages) {
            val entry = org.json.JSONObject().put("package", pkg)
            appMap[pkg]?.let {
                entry.put("label", it.label)
                entry.put("version", it.version)
            }
            val parts: List<File> = if (remote) {
                val paths = adb.shell("pm path $pkg")
                    ?.lines()
                    ?.mapNotNull { it.trim().removePrefix("package:").takeIf { p -> p.isNotBlank() } }
                    ?: emptyList()
                val temp = File(context.cacheDir, "backup-${System.currentTimeMillis()}-$pkg").apply { mkdirs() }
                val pulled = paths.mapIndexedNotNull { index, path ->
                    val file = File(temp, if (index == 0) "base.apk" else "split$index.apk")
                    if (adb.pull(path, file)) file else null
                }
                tempDirs += temp
                pulled
            } else {
                val info = context.packageManager.getApplicationInfo(pkg, 0)
                buildList {
                    info.sourceDir?.let { add(File(it)) }
                    info.splitSourceDirs?.let { arr -> addAll(arr.map { File(it) }) }
                }
            }
            entry.put("parts", parts.size)
            groups += Group(pkg, parts, entry)
        }

        val uri = Storage.save(context, name, "application/zip") { out ->
            ZipOutputStream(out).use { zip ->
                val manifest = org.json.JSONArray()
                groups.forEach { group ->
                    group.parts.forEach { file ->
                        zip.putNextEntry(ZipEntry("${group.pkg}/${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    manifest.put(group.entry)
                }
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(manifest.toString().toByteArray())
                zip.closeEntry()
            }
        }
        tempDirs.forEach { it.deleteRecursively() }
        return uri
    }

    suspend fun cytProfiles(): List<CytProfile> = cyt.profiles()

    suspend fun cytPackages(profile: String): List<String> = cyt.packages(profile)

    suspend fun cytMatch(): String? = cyt.match(adb)

    fun applyDebloat(profile: String, enable: Boolean) {
        viewModelScope.launch {
            if (!adb.state.value.connected) {
                emit("يتطلب اتصال ADB بالجهاز")
                return@launch
            }
            val packages = cyt.packages(profile)
            if (packages.isEmpty()) {
                emit("قائمة CleanYourTV فارغة أو غير موجودة")
                return@launch
            }
            var done = 0
            for (pkg in packages) {
                val command = if (enable) {
                    "pm enable $pkg"
                } else {
                    "pm disable-user --user 0 $pkg"
                }
                val out = adb.shell(command)
                if (out != null && !out.contains("Failure") && !out.contains("Exception")) done++
            }
            emit("تم ${if (enable) "استعادة" else "تعطيل"} $done/${packages.size} حزمة")
            reloadApps()
        }
    }

    fun connectLocal() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val ip = localWifiIp(context)
            val targets = buildList {
                if (ip != null) add(ip to 5555)
                add("127.0.0.1" to 5555)
            }.distinct()

            var lastError: String? = null
            for ((host, port) in targets) {
                var attempts = 0
                var waiting = false
                while (attempts < 10) {
                    attempts++
                    emit(
                        if (waiting) {
                            "بانتظار الموافقة على «Allow USB debugging»… ($host:$port)"
                        } else {
                            "محاولة الاتصال بـ $host:$port…"
                        }
                    )
                    val result = adb.addDevice(host, port, null, null)
                    if (result.isSuccess) {
                        emit("تم الاتصال بالجهاز محلياً: $host:$port")
                        refresh()
                        return@launch
                    }
                    val error = result.exceptionOrNull()?.message.orEmpty()
                    lastError = error
                    val needsAuth = error.contains("unauthorized", true) ||
                        error.contains("authoriz", true) ||
                        error.contains("authenticat", true)
                    if (!needsAuth) break
                    waiting = true
                    kotlinx.coroutines.delay(4000)
                }
            }
            emit(
                "تعذّر الاتصال المحلي" +
                    (lastError?.let { ": $it" } ?: "") +
                    " — وافق على Allow USB debugging على شاشة الجهاز ثم أعد المحاولة"
            )
        }
    }

    private fun localWifiIp(context: android.content.Context): String? {
        runCatching {
            val manager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return@runCatching null
            val props = manager.getLinkProperties(network) ?: return@runCatching null
            return@runCatching props.linkAddresses
                .map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()?.let { return it }

        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull {
                    it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress
                }
                ?.hostAddress
        }.getOrNull()
    }

    fun installApk(uri: Uri) {
        viewModelScope.launch {
            try {
                emit("جارٍ تجهيز الملف…")
                val file = withContext(Dispatchers.IO) { copyToCache(uri) }
                val active = adb.state.value.active
                val connected = adb.state.value.connected

                if (connected && active != null) {
                    emit("جارٍ التثبيت على ${active.label}…")
                    val result = withContext(Dispatchers.IO) { installViaAdb(file) }
                    emit(result)
                    refresh()
                } else if (file.name.lowercase().endsWith(".apk")) {
                    launchLocalInstaller(file)
                    emit("أكمل التثبيت من نافذة النظام")
                } else {
                    emit("تثبيت الحزم المقسّمة (.apks) يحتاج اتصال ADB بالجهاز")
                }
            } catch (t: Throwable) {
                emit("فشل التثبيت: ${t.message}")
            }
        }
    }

    fun installFile(file: File) {
        viewModelScope.launch {
            try {
                val active = adb.state.value.active
                if (adb.state.value.connected && active != null) {
                    emit("جارٍ التثبيت على ${active.label}…")
                    emit(withContext(Dispatchers.IO) { installViaAdb(file) })
                    refresh()
                } else if (file.name.lowercase().endsWith(".apk")) {
                    launchLocalInstaller(file)
                    emit("أكمل التثبيت من نافذة النظام")
                } else {
                    emit("تثبيت .apks يحتاج اتصال ADB")
                }
            } catch (t: Throwable) {
                emit("فشل التثبيت: ${t.message}")
            }
        }
    }

    private fun copyToCache(uri: Uri): File {
        val context = getApplication<Application>()
        var name = "app.apk"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(index) ?: name
            }
        }
        val dir = File(context.cacheDir, "install").apply { mkdirs() }
        val file = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val input = if (uri.scheme == "file") {
            File(uri.path ?: "").inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
        input?.use { stream ->
            file.outputStream().use { stream.copyTo(it) }
        } ?: throw IllegalStateException("تعذّر قراءة الملف")
        return file
    }

    private suspend fun installViaAdb(file: File): String {
        val groups: List<Pair<String, List<File>>> = if (file.name.lowercase().endsWith(".apk")) {
            listOf("app" to listOf(file))
        } else {
            extractArchive(file)
        }
        if (groups.isEmpty()) return "لم يتم العثور على ملفات APK داخل الحزمة"

        var ok = 0
        val failures = mutableListOf<String>()
        groups.forEachIndexed { groupIndex, group ->
            val (groupName, apks) = group
            val remote = mutableListOf<String>()
            var pushFailed = false
            apks.forEachIndexed { index, apk ->
                val dst = "/data/local/tmp/adbcontrol-install-$groupIndex-$index.apk"
                if (!adb.push(apk, dst)) pushFailed = true else remote += dst
            }
            if (pushFailed || remote.isEmpty()) {
                failures += "$groupName: فشل رفع الملف"
                return@forEachIndexed
            }
            val command = if (remote.size == 1) {
                "pm install -r ${remote.first()}"
            } else {
                "pm install-multiple -r ${remote.joinToString(" ")}"
            }
            var output = adb.shell(command) ?: ""
            var replaced = false
            if (!output.contains("Success") && needsUninstall(output)) {
                val pkg = archivePackageName(apks.first())
                if (pkg != null) {
                    emit("توقيع النسخة القديمة مختلف — حذف $pkg وإعادة التثبيت…")
                    adb.shell("pm uninstall $pkg")
                    output = adb.shell(command) ?: output
                    replaced = output.contains("Success")
                }
            }
            if (output.contains("Success")) {
                ok++
                if (replaced) emit("$groupName: حُذفت النسخة القديمة (توقيع مختلف) وثُبّتت الجديدة")
            } else {
                failures += "$groupName: ${installHint(output)}"
            }
            adb.shell("rm -f ${remote.joinToString(" ")}")
        }
        return buildString {
            append("تم تثبيت $ok/${groups.size}")
            if (failures.isNotEmpty()) append("\n").append(failures.first())
        }
    }


    private fun needsUninstall(output: String): Boolean {
        val lower = output.lowercase()
        return lower.contains("update_incompatible") ||
            lower.contains("signatures do not match") ||
            lower.contains("signature mismatch")
    }

    private fun installHint(output: String): String {
        val lower = output.lowercase()
        return when {
            needsUninstall(output) ->
                "توقيع النسخة المثبتة مختلف (نسخة معدّلة) — احذف التطبيق القديم ثم أعد التثبيت"
            lower.contains("version_downgrade") ->
                "النسخة المثبتة أحدث من الملف — احذف التطبيق أو استخدم ملفاً أحدث"
            lower.contains("user_restricted") ->
                "الجهاز يمنع التثبيت عبر USB — فعّل «Install via USB» في خيارات المطور"
            lower.contains("insufficient_storage") ->
                "لا توجد مساحة كافية على الجهاز"
            lower.contains("invalid_apk") || lower.contains("parse_failed") ->
                "ملف APK غير صالح (تالف أو ناقص)"
            lower.contains("blocked") || lower.contains("aborted") ->
                "تم رفض التثبيت من النظام (Play Protect أو سياسة الجهاز)"
            lower.contains("no space") -> "لا توجد مساحة كافية على الجهاز"
            else -> output.trim().take(200)
        }
    }

    private fun archivePackageName(apk: File): String? = runCatching {
        val context = getApplication<Application>()
        val info = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            android.content.pm.PackageManager.GET_ACTIVITIES
        )
        info?.packageName
    }.getOrNull()

    private fun extractArchive(archive: File): List<Pair<String, List<File>>> {
        val root = File(archive.parentFile, "extract-${System.currentTimeMillis()}").apply { mkdirs() }
        val groups = LinkedHashMap<String, MutableList<File>>()
        java.util.zip.ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".apk")) {
                    val folder = if (entry.name.contains('/')) entry.name.substringBefore('/') else "app"
                    val out = File(root, folder + "_" + File(entry.name).name)
                    out.outputStream().use { zip.copyTo(it) }
                    groups.getOrPut(folder) { mutableListOf() }.add(out)
                }
                entry = zip.nextEntry
            }
        }
        return groups.map { it.key to it.value.toList() }
    }

    private fun launchLocalInstaller(file: File) {
        val context = getApplication<Application>()
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun syncRemoteIcons() {
        val id = adb.activeDeviceId()
        if (id == null || !isRemote()) {
            viewModelScope.launch { emit("اختر جهازاً بعيداً أولاً") }
            return
        }
        viewModelScope.launch {
            emit("جارٍ تجهيز أيقونات الجهاز البعيد…")
            RemoteSync.sync(getApplication(), adb, id) { message -> emit(message) }
                .onSuccess {
                    emit("تم تحميل الأيقونات والأسماء الحقيقية")
                    refresh()
                }
                .onFailure { emit("فشل: ${it.message}") }
        }
    }

    // ---------- device management ----------

    fun startSelfPairing() = adb.restartDiscovery()

    fun pairSelf(code: String) {
        viewModelScope.launch {
            adb.pairSelf(code)
                .onSuccess { emit("تم الاتصال بجهازك"); refresh() }
                .onFailure { emit(it.message ?: "فشل الاقتران") }
        }
    }

    fun pairRemote(
        host: String,
        pairingPort: Int,
        connectHost: String?,
        connectPort: Int?,
        code: String
    ) {
        viewModelScope.launch {
            adb.pairRemote(host, pairingPort, connectHost, connectPort, code)
                .onSuccess {
                    emit("تم الاقتران")
                    refresh()
                }
                .onFailure { emit(it.message ?: "فشل الاقتران") }
        }
    }

    fun connectSelf() {
        viewModelScope.launch {
            adb.connectSelf()
                .onSuccess { emit("تم الاتصال"); refresh() }
                .onFailure { emit(it.message ?: "فشل الاتصال") }
        }
    }

    fun addDevice(host: String, port: Int, pairingPort: Int?, code: String?) {
        viewModelScope.launch {
            adb.addDevice(host.trim(), port, pairingPort, code)
                .onSuccess { emit("تمت إضافة ${it.label}"); refresh() }
                .onFailure { emit(it.message ?: "تعذّر الاتصال بالجهاز") }
        }
    }

    fun selectDevice(id: String) {
        viewModelScope.launch {
            adb.selectDevice(id)
                .onSuccess { refresh() }
                .onFailure { emit(it.message ?: "تعذّر الاتصال") }
        }
    }

    fun removeDevice(id: String) {
        adb.removeDevice(id)
        viewModelScope.launch { refresh() }
    }

    fun setMonitoring(value: Boolean) {
        prefs.edit().putBoolean("monitoring", value).apply()
        monitoring.value = value
    }

    fun setBeta(value: Boolean) {
        prefs.edit().putBoolean("beta", value).apply()
        beta.value = value
    }

    // ---------- app actions ----------

    fun perform(action: AppAction, app: AppEntry) {
        viewModelScope.launch {
            val remote = isRemote()
            when (action) {
                AppAction.OPEN ->
                    if (remote) shellAction(
                        "monkey -p ${app.packageName} -c android.intent.category.LAUNCHER 1",
                        "تم إرسال أمر التشغيل"
                    ) else launchApp(app.packageName)

                AppAction.INFO ->
                    if (remote) shellAction(
                        "am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:${app.packageName}",
                        "تم فتح معلومات التطبيق"
                    ) else openInfo(app.packageName)

                AppAction.UNINSTALL ->
                    if (remote) shellAction(
                        "pm uninstall --user 0 ${app.packageName}",
                        "تم حذف ${app.label}"
                    ) else uninstall(app.packageName)

                AppAction.DISABLE -> shellAction(
                    "pm disable-user --user 0 ${app.packageName}",
                    "تم تعطيل ${app.label}"
                )

                AppAction.ENABLE -> shellAction(
                    "pm enable ${app.packageName}",
                    "تم تفعيل ${app.label}"
                )

                AppAction.CLEAR -> shellAction(
                    "pm clear --user 0 ${app.packageName}",
                    "تم مسح بيانات ${app.label}"
                )

                AppAction.CLEAR_CACHE -> clearCache(app.packageName, app.label)

                AppAction.EXTRACT -> extract(app)
            }
        }
    }

    private suspend fun clearCache(pkg: String, label: String) {
        val command = "sdk=$(getprop ro.build.version.sdk); " +
            "if [ \"\$sdk\" -ge 30 ]; then pm clear --cache-only --user 0 $pkg; " +
            "else pm trim-caches 999999999999; echo TRIM_ALL; fi"
        val output = adb.shell(command)
        if (output == null) {
            emit("هذا الإجراء يحتاج اتصال ADB")
            return
        }
        if (output.contains("TRIM_ALL")) {
            emit("تم تنظيف كاش جميع التطبيقات (مسح كاش تطبيق واحد متاح من Android 11+)")
        } else if (output.contains("Failed") || output.contains("Failure") || output.contains("Exception")) {
            emit("فشل مسح الكاش: ${output.trim()}")
        } else {
            emit("تم مسح كاش $label")
        }
        delay(300)
        reloadApps()
    }

    private suspend fun shellAction(command: String, success: String) {
        val output = adb.shell(command)
        if (output == null) {
            emit("هذا الإجراء يحتاج اتصال ADB — اختر جهازاً واضغط Connect")
            return
        }
        val text = output.trim()
        emit(
            if (text.contains("Exception") || text.contains("Failure") || text.contains("Failed") || text.contains("Error")) text
            else success
        )
        delay(350)
        reloadApps()
    }

    private suspend fun launchApp(pkg: String) {
        val context = getApplication<Application>()
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            emit("لا يمكن تشغيل هذا التطبيق")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun openInfo(pkg: String) {
        val context = getApplication<Application>()
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun uninstall(pkg: String) {
        val context = getApplication<Application>()
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    suspend fun savedApks(): List<Storage.SavedApk> = withContext(Dispatchers.IO) {
        Storage.list(getApplication())
    }

    private suspend fun extract(app: AppEntry) = withContext(Dispatchers.IO) {
        runCatching {
            val context = getApplication<Application>()
            if (isRemote()) {
                val paths = adb.shell("pm path ${app.packageName}")
                    ?.lines()
                    ?.mapNotNull { it.trim().removePrefix("package:").takeIf { p -> p.isNotBlank() } }
                    ?: emptyList()
                if (paths.isEmpty()) {
                    emit("لم يتم العثور على APK على الجهاز البعيد")
                    return@withContext
                }
                if (paths.size == 1) {
                    val temp = File(context.cacheDir, "extract-${System.currentTimeMillis()}.apk")
                    if (!adb.pull(paths.first(), temp)) {
                        emit("فشل سحب الملف")
                        return@withContext
                    }
                    val name = "${app.packageName}.apk"
                    val uri = Storage.save(context, name, "application/vnd.android.package-archive") { out ->
                        temp.inputStream().use { it.copyTo(out) }
                    }
                    temp.delete()
                    emit(if (uri != null) "تم الحفظ في ${Storage.displayPath}/$name" else "فشل الحفظ")
                } else {
                    val tempDir = File(context.cacheDir, "extract-${System.currentTimeMillis()}").apply { mkdirs() }
                    val parts = paths.mapIndexedNotNull { index, path ->
                        val file = File(tempDir, if (index == 0) "base.apk" else "split$index.apk")
                        if (adb.pull(path, file)) file else null
                    }
                    val name = "${app.packageName}.apks"
                    val uri = Storage.save(context, name, "application/octet-stream") { out ->
                        ZipOutputStream(out).use { zip ->
                            parts.forEach { file ->
                                zip.putNextEntry(ZipEntry(file.name))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                    tempDir.deleteRecursively()
                    emit(if (uri != null) "تم الحفظ (${parts.size} أجزاء) في ${Storage.displayPath}/$name" else "فشل الحفظ")
                }
            } else {
                val info = context.packageManager.getApplicationInfo(app.packageName, 0)
                val parts = buildList {
                    info.sourceDir?.let { add(it) }
                    info.splitSourceDirs?.let { addAll(it.toList()) }
                }
                if (parts.isEmpty()) {
                    emit("لم يتم العثور على ملفات APK")
                    return@withContext
                }
                if (parts.size == 1) {
                    val name = "${app.packageName}.apk"
                    val uri = Storage.save(context, name, "application/vnd.android.package-archive") { out ->
                        File(parts.first()).inputStream().use { it.copyTo(out) }
                    }
                    emit(if (uri != null) "تم الحفظ في ${Storage.displayPath}/$name" else "فشل الحفظ")
                } else {
                    val name = "${app.packageName}.apks"
                    val uri = Storage.save(context, name, "application/octet-stream") { out ->
                        ZipOutputStream(out).use { zip ->
                            parts.forEach { part ->
                                val file = File(part)
                                zip.putNextEntry(ZipEntry(file.name))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                    emit(if (uri != null) "تم الحفظ (${parts.size} أجزاء) في ${Storage.displayPath}/$name" else "فشل الحفظ")
                }
            }
        }.onFailure { emit("فشل الاستخراج: ${it.message}") }
    }

    private suspend fun emit(message: String) {
        _toast.emit(message)
    }

    override fun onCleared() {
        adb.close()
        super.onCleared()
    }
}
