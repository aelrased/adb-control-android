package dev.oss.adbcontrol

import android.app.Application
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import okio.Path.Companion.toPath
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Android 9 needs this so parsed RSA keys keep their CRT parameters.
        RsaCrtFix.install()
        // Apply the saved UI language before any screen composes (keeps layout direction correct).
        runCatching {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            L10n.set(prefs.getString("lang", "ar") ?: "ar")
        }
        // Persist the ADB host identity so pairing survives app restarts.
        val keyFile = File(filesDir, "adbkey.pem")
        // Key generation can take seconds on slow TV boxes; never block the main thread.
        Thread {
            runCatching {
                KadbCert.configure(
                    store = OkioFilePrivateKeyStore(keyFile.absolutePath.toPath()),
                    policy = KadbCertPolicy(),
                    additionalPrivateKeysPem = emptyList()
                )
            }
        }.start()
    }
}
