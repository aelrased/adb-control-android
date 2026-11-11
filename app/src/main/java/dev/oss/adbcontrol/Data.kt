package dev.oss.adbcontrol

import androidx.compose.ui.graphics.ImageBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    val version: String,
    val system: Boolean,
    val enabled: Boolean,
    val sizeBytes: Long,
    val updatedAt: Long,
    val icon: ImageBitmap?
)

enum class AppFilter { ALL, USER, SYSTEM, DISABLED, ENABLED }

enum class AppSort { NAME, DATE, SIZE }

enum class AppAction { OPEN, INFO, DISABLE, ENABLE, CLEAR, CLEAR_CACHE, EXTRACT, UNINSTALL }
