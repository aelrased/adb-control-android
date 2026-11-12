package dev.oss.adbcontrol

import androidx.compose.runtime.mutableStateOf

/**
 * Lightweight runtime localization. Arabic text is the source key, so any
 * missing translation gracefully falls back to Arabic.
 */
object L10n {
    val current = mutableStateOf("ar")

    val isEnglish: Boolean get() = current.value == "en"

    fun set(language: String) {
        current.value = if (language == "en") "en" else "ar"
    }

    fun t(ar: String): String {
        if (!isEnglish) return ar
        EN[ar]?.let { return it }
        for ((prefix, replacement) in PREFIXES) {
            if (ar.startsWith(prefix)) return replacement + ar.removePrefix(prefix)
        }
        return ar
    }

    private val PREFIXES = listOf(
        "تم تعطيل " to "Disabled ",
        "تم تفعيل " to "Enabled ",
        "تم حذف " to "Uninstalled ",
        "تم مسح كاش " to "Cleared cache of ",
        "تم مسح بيانات " to "Cleared data of ",
        "تمت استعادة " to "Restored ",
        "تمت إضافة " to "Added ",
        "تم الاتصال بالجهاز محلياً: " to "Connected locally: ",
        "تم الاتصال بجهازك" to "Connected to this device",
        "تعذّر الاتصال بالجهاز" to "Could not reach device",
        "تعذّر الاتصال" to "Connection failed",
        "فشل: " to "Failed: ",
        "فشل الاقتران" to "Pairing failed",
        "فشل الاتصال" to "Connection failed",
        "جارٍ " to "Working: ",
        "محاولة الاتصال بـ " to "Connecting to ",
    )

    private val EN = mapOf(
        "الكل" to "All",
        "إلغاء" to "Cancel",
        "تأكيد" to "Confirm",
        "حذف" to "Delete",
        "تعطيل" to "Disable",
        "تفعيل" to "Enable",
        "استعادة" to "Restore",
        "مسح الكاش" to "Clear cache",
        "مسح البيانات" to "Clear data",
        "نسخة احتياطية" to "Backup",
        "متصل" to "Connected",
        "غير متصل" to "Not connected",
        "متصل عبر Shizuku" to "Connected via Shizuku",
        "غير متاح" to "Unavailable",
        "جاهز" to "Ready",
        "غير مثبّت أو غير يعمل" to "Not installed or not running",
        "يعمل — يحتاج صلاحية" to "Running — permission needed",
        "مصدر الصلاحيات" to "Permissions source",
        "منح صلاحية Shizuku" to "Grant Shizuku permission",
        "تثبيت Shizuku" to "Install Shizuku",
        "تحديث الحالة" to "Refresh status",
        "إدخال يدوي" to "Manual entry",
        "إعادة البحث عن منفذ الاقتران" to "Search pairing port again",
        "ابحث عن موديل التلفزيون" to "Search TV model",
        "اختر ملفاً" to "Select a profile",
        "استخدام هذا الجهاز (IP الواي فاي تلقائياً)" to "Use this device (auto Wi-Fi IP)",
        "استعادة (Install existing)" to "Restore (install existing)",
        "حذف نهائي" to "Delete permanently",
        "حذف نهائي (نسخة المصنع)" to "Permanent delete (factory)",
        "⚠️ حذف نهائي — لا يمكن التراجع" to "⚠️ Permanent delete — cannot be undone",
        "هل أنت متأكد؟" to "Are you sure?",
        "المحذوفة (استعادة)" to "Removed (restore)",
        "المحذوفة (للمستخدم 0)" to "Removed (user 0)",
        "هذا الجهاز" to "This device",
        "حذف التطبيقات المحددة؟" to "Delete selected apps?",
        "مسح بيانات التطبيقات المحددة؟" to "Clear data of selected apps?",
        "لا توجد مساحة كافية على الجهاز" to "Not enough storage",
        "ملف APK غير صالح (تالف أو ناقص)" to "Invalid APK (corrupt or incomplete)",
        "تم رفض التثبيت من النظام (Play Protect أو سياسة الجهاز)" to
            "Install blocked by the system (Play Protect or device policy)",
        "الجهاز يمنع التثبيت عبر USB — فعّل «Install via USB» في خيارات المطور" to
            "Device blocks USB installs — enable “Install via USB” in developer options",
        "النسخة المثبتة أحدث من الملف — احذف التطبيق أو استخدم ملفاً أحدث" to
            "Installed version is newer — uninstall it or pick a newer file",
        "توقيع النسخة المثبتة مختلف (نسخة معدّلة) — احذف التطبيق القديم ثم أعد التثبيت" to
            "Signature mismatch (modded) — uninstall the old app, then retry",
        "اختر جهازاً بعيداً أولاً" to "Select a remote device first",
        "تحميل الأيقونات" to "Load icons",
        "أيقونات وأسماء الجهاز البعيد" to "Remote device icons and names",
        "تثبيت APK / .apks لهذا الجهاز" to "Install APK / .apks for this device",
        "هذا هو المكان الوحيد للتثبيت: اختر ملفاً من الهاتف، أو اضغط أي ملف في المكتبة لتثبيته." to
            "This is the only install location: pick a file, or tap a library file to install it.",
        "ADB غير متصل" to "ADB not connected",
        "فعّل Wireless debugging ثم اضغط Connect لمنح التطبيق صلاحية shell لإدارة التطبيقات." to
            "Enable Wireless debugging, then tap Connect to grant shell access for app management.",
        "أكمل التثبيت من نافذة النظام" to "Finish the install from the system dialog",
        "تثبيت .apks يحتاج اتصال ADB" to ".apks install requires ADB",
        "تثبيت الحزم المقسّمة (.apks) يحتاج اتصال ADB بالجهاز" to
            "Split packages (.apks) require an ADB connection",
        "لا توجد ملفات بعد. استخدم «Extract APK» من قائمة أي تطبيق أو زر التثبيت أعلاه." to
            "No files yet. Use “Extract APK” from an app menu or the install button above.",
        "اختر جهازاً من القائمة أعلاه. الأجهزة المضافة تُحفظ وتُعاد محاولة الاتصال عند اختيارها." to
            "Pick a device above. Added devices are saved and reconnected when selected.",
        "يمكن الاعتماد على ADB أو Shizuku أو كليهما: إن لم يكن ADB متصلاً يستخدم التطبيق Shizuku تلقائياً " to
            "You can rely on ADB, Shizuku, or both: when ADB is offline the app uses Shizuku automatically ",
        "يتطلب ADB أو Shizuku" to "Requires ADB or Shizuku",
        "هذا الإجراء يحتاج ADB أو Shizuku" to "This action requires ADB or Shizuku",
        "الاستعادة تحتاج ADB أو Shizuku" to "Restore requires ADB or Shizuku",
        "لا يمكن تشغيل هذا التطبيق" to "This app cannot be launched",
        "جارٍ الحذف النهائي…" to "Deleting permanently…",
        "تم فتح معلومات التطبيق" to "App info opened",
        "لم يتم العثور على ملفات APK" to "No APK files found",
        "لم يتم العثور على ملفات APK داخل الحزمة" to "No APK files inside the archive",
        "لم يتم العثور على APK على الجهاز البعيد" to "No APK found on the remote device",
        "فشل الاستخراج: " to "Extraction failed: ",
        "تعطيل فقط عبر pm disable-user وليس حذفاً. استخدم «استعادة» للتراجع." to
            "Disables with pm disable-user only, it does not uninstall. Use “Restore” to undo.",
        "سيتـم تثبيت جسر صغير" to "A small bridge will be installed",
        "الإجراءات المعلّمة بـ ADB تحتاج اتصالاً عبر زر Connect." to
            "Actions marked ADB require a connection via the Connect button.",
        "اختر جهازاً" to "Select a device",
        "افتح Wireless debugging" to "Open Wireless debugging",
        "إشعار عند تثبيت/تحديث التطبيقات (بيتا)" to "Notify on app install/update (beta)",
        "تحديثات تجريبية وميزات مبكرة" to "Beta updates and early features",
    )
}
