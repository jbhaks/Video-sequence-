package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object VideoUtils {

    suspend fun parseVideoClip(context: Context, uri: Uri, isSample: Boolean = false): VideoClip = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 1920
        var height = 1080
        var rotation = 0
        var title = if (isSample) "Sample HD Clip" else "Video Clip"
        var sizeBytes = 0L

        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            // Try to resolve file name and size from content resolver
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            cursor.getString(nameIndex)?.let { title = it }
                        }
                        if (sizeIndex != -1) {
                            sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } else if (uri.scheme == "file") {
                uri.path?.let { path ->
                    val file = File(path)
                    title = file.name
                    sizeBytes = file.length()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        VideoClip(
            id = UUID.randomUUID().toString(),
            uri = uri,
            title = title,
            durationMs = durationMs,
            width = width,
            height = height,
            rotation = rotation,
            sizeBytes = sizeBytes,
            isSample = isSample
        )
    }

    suspend fun extractThumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            // Retrieve frame at 0.5s or first frame
            retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    suspend fun copyRawResourceToFile(context: Context, rawResId: Int, fileName: String): File = withContext(Dispatchers.IO) {
        val sampleDir = File(context.cacheDir, "sample_videos")
        if (!sampleDir.exists()) sampleDir.mkdirs()
        val destFile = File(sampleDir, fileName)
        if (!destFile.exists() || destFile.length() == 0L) {
            context.resources.openRawResource(rawResId).use { input: InputStream ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        destFile
    }
}
