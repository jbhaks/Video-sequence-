package com.example.model

import android.net.Uri
import java.util.Locale
import java.util.UUID

data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    val sizeBytes: Long = 0L,
    val isSample: Boolean = false
) {
    val displayWidth: Int
        get() = if (rotation == 90 || rotation == 270) height else width

    val displayHeight: Int
        get() = if (rotation == 90 || rotation == 270) width else height

    val resolutionBadge: String
        get() {
            val maxDim = maxOf(displayWidth, displayHeight)
            return when {
                maxDim >= 3800 -> "4K UHD"
                maxDim >= 2500 -> "1440p QHD"
                maxDim >= 1800 -> "1080p HD"
                maxDim >= 1200 -> "720p HD"
                maxDim > 0 -> "${displayWidth}x${displayHeight}"
                else -> "HD Video"
            }
        }

    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "HD Stream"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                else -> String.format(Locale.US, "%.0f KB", kb)
            }
        }
}
