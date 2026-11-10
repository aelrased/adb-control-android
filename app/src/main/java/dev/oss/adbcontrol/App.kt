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
        // Persist the ADB host identity so pairing survives app restarts.
        val keyFile = File(filesDir, "adbkey.pem")
        KadbCert.configure(
            store = OkioFilePrivateKeyStore(keyFile.absolutePath.toPath()),
            policy = KadbCertPolicy(),
            additionalPrivateKeysPem = emptyList()
        )
    }
}
