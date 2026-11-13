package dev.oss.adbcontrol

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Verifies the ADB AppControl compatible save format:
 *  - `<Label> (<package>) [v.<version>].apk|.apks`
 *  - untouched original files, untouched original split names
 *  - `.apks` entries stored without recompression
 */
@RunWith(AndroidJUnit4::class)
class ApkSaveFormatTest {

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun digest(file: File): ByteArray = file.inputStream().use { input ->
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            md.update(buffer, 0, read)
        }
        md.digest()
    }

    private fun findSaved(app: Application, name: String): Uri? {
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        app.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            selection,
            arrayOf(name, "${Storage.relativePath}%"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0)
                )
            }
        }
        return null
    }

    private suspend fun awaitSaved(app: Application, name: String): Uri =
        withTimeout(90_000) {
            var uri: Uri? = null
            while (uri == null) {
                uri = findSaved(app, name)
                if (uri == null) delay(300)
            }
            uri
        }

    private fun copyToCache(app: Application, uri: Uri, suffix: String): File {
        val file = File(app.cacheDir, "verify-${System.currentTimeMillis()}$suffix")
        app.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("cannot read saved file")
        return file
    }

    @Test
    fun splitAppSavesStoredZipWithOriginalNames() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pm = app.packageManager
        val pkg = "org.videolan.vlc"
        val info = pm.getApplicationInfo(pkg, 0)
        val label = pm.getApplicationLabel(info).toString()
        val version = pm.getPackageInfo(pkg, 0).versionName ?: ""

        val source = buildList {
            info.sourceDir?.let { add(File(it)) }
            info.splitSourceDirs?.let { addAll(it.map(::File)) }
        }
        assertTrue("test needs a split package", source.size > 1)

        val name = "$label ($pkg) [v.$version].apks"
        val vm = AppViewModel(app)
        vm.perform(
            AppAction.EXTRACT,
            AppEntry(pkg, label, version, false, true, 0L, 0L, null)
        )

        val uri = awaitSaved(app, name)
        val copy = copyToCache(app, uri, ".apks")
        try {
            ZipFile(copy).use { zip ->
                val entries = zip.entries().toList()
                assertEquals(source.size, entries.size)
                assertEquals(
                    source.map { it.name }.sorted(),
                    entries.map { it.name }.sorted()
                )
                for (entry in entries) {
                    assertTrue(
                        "${entry.name} must not be recompressed",
                        entry.method == ZipEntry.STORED
                    )
                    val original = source.first { it.name == entry.name }
                    val saved = zip.getInputStream(entry).use { it.readBytes() }
                    assertArrayEquals(digest(original), digest(saved))
                }
            }
        } finally {
            copy.delete()
            app.contentResolver.delete(uri, null, null)
        }
    }

    @Test
    fun singleApkKeepsOriginalBytesAndFriendlyName() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pm = app.packageManager
        val pkg = app.packageName
        val info = pm.getApplicationInfo(pkg, 0)
        val label = pm.getApplicationLabel(info).toString()
        val version = pm.getPackageInfo(pkg, 0).versionName ?: ""
        val original = File(info.sourceDir)
        assertNotNull(original)

        val name = "$label ($pkg) [v.$version].apk"
        val vm = AppViewModel(app)
        vm.perform(
            AppAction.EXTRACT,
            AppEntry(pkg, label, version, false, true, 0L, 0L, null)
        )

        val uri = awaitSaved(app, name)
        val copy = copyToCache(app, uri, ".apk")
        try {
            assertArrayEquals(digest(original), digest(copy))
        } finally {
            copy.delete()
            app.contentResolver.delete(uri, null, null)
        }
    }
}
