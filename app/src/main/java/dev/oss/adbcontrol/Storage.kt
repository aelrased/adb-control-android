package dev.oss.adbcontrol

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/**
 * Saves APK/.apks files into the public Downloads folder
 * (Download/OpenADBControl) so they are reachable from any file manager.
 */
object Storage {

    val relativePath: String = Environment.DIRECTORY_DOWNLOADS + "/OpenADBControl"
    val displayPath: String = "Download/OpenADBControl"

    data class SavedApk(
        val uri: Uri,
        val name: String,
        val size: Long,
        val modified: Long
    )

    fun save(
        context: Context,
        name: String,
        mime: String,
        writer: (OutputStream) -> Unit
    ): Uri? {
        if (Build.VERSION.SDK_INT < 29) {
            return saveLegacy(name, writer)
        }
        val resolver = context.contentResolver
        // Match the desktop app: saving the same app again replaces the file
        // instead of leaving "name (1).apk" duplicates behind.
        deleteExisting(context, name)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output -> writer(output) }
                ?: return null
            val done = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun deleteExisting(context: Context, name: String) {
        val resolver = context.contentResolver
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(name, "$relativePath%")
        runCatching {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                selection,
                args,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    runCatching {
                        resolver.delete(
                            ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                            null,
                            null
                        )
                    }
                }
            }
        }
    }

    fun list(context: Context): List<SavedApk> {
        if (Build.VERSION.SDK_INT < 29) {
            return listLegacy()
        }
        val result = mutableListOf<SavedApk>()
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED
        )
        val selection =
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.IS_PENDING} = 0"
        val args = arrayOf("$relativePath%")
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Downloads.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                val readable = runCatching {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { }
                }.getOrNull() != null
                if (!readable) continue
                result += SavedApk(
                    uri = uri,
                    name = cursor.getString(nameColumn) ?: "file",
                    size = cursor.getLong(sizeColumn),
                    modified = cursor.getLong(dateColumn) * 1000L
                )
            }
        }
        return result
    }
    private fun legacyDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, "OpenADBControl")
    }

    private fun saveLegacy(name: String, writer: (OutputStream) -> Unit): Uri? {
        val dir = legacyDir()
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, name)
        return try {
            file.outputStream().use { output -> writer(output) }
            Uri.fromFile(file)
        } catch (t: Throwable) {
            runCatching { file.delete() }
            null
        }
    }

    private fun listLegacy(): List<SavedApk> {
        val dir = legacyDir()
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                SavedApk(
                    uri = Uri.fromFile(file),
                    name = file.name,
                    size = file.length(),
                    modified = file.lastModified()
                )
            }
            ?: emptyList()
    }

}
