package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.model.VideoClip
import com.example.service.MediaStoreExporter
import com.example.service.VideoMergerEngine
import com.example.util.VideoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ExportState {
    object Idle : ExportState
    data class Merging(val progress: Float, val currentClip: Int, val totalClips: Int) : ExportState
    data class Success(
        val file: File,
        val durationMs: Long,
        val sizeBytes: Long,
        val galleryUri: Uri? = null
    ) : ExportState
    data class Error(val message: String) : ExportState
}

class VideoMergerViewModel : ViewModel() {

    private val _clips = MutableStateFlow<List<VideoClip>>(emptyList())
    val clips: StateFlow<List<VideoClip>> = _clips.asStateFlow()

    private val _previewClip = MutableStateFlow<VideoClip?>(null)
    val previewClip: StateFlow<VideoClip?> = _previewClip.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _isLoadingSamples = MutableStateFlow(false)
    val isLoadingSamples: StateFlow<Boolean> = _isLoadingSamples.asStateFlow()

    private val _isAnalyzingVideos = MutableStateFlow(false)
    val isAnalyzingVideos: StateFlow<Boolean> = _isAnalyzingVideos.asStateFlow()

    private val _exportFileName = MutableStateFlow("Sequence_Merged_HD")
    val exportFileName: StateFlow<String> = _exportFileName.asStateFlow()

    val totalDurationMs: Long
        get() = _clips.value.sumOf { it.durationMs }

    val totalEstimatedBytes: Long
        get() = _clips.value.sumOf { it.sizeBytes }

    fun setExportFileName(name: String) {
        _exportFileName.value = name
    }

    fun addClips(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isAnalyzingVideos.value = true
            val parsedList = mutableListOf<VideoClip>()
            for (uri in uris) {
                try {
                    val clip = VideoUtils.parseVideoClip(context, uri)
                    parsedList.add(clip)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _clips.value = _clips.value + parsedList
            _isAnalyzingVideos.value = false
        }
    }

    fun loadSampleClips(context: Context) {
        viewModelScope.launch {
            _isLoadingSamples.value = true
            try {
                val f1 = VideoUtils.copyRawResourceToFile(context, R.raw.clip1_intro, "clip1_intro.mp4")
                val f2 = VideoUtils.copyRawResourceToFile(context, R.raw.clip2_bars, "clip2_bars.mp4")
                val f3 = VideoUtils.copyRawResourceToFile(context, R.raw.clip3_outro, "clip3_outro.mp4")

                val c1 = VideoUtils.parseVideoClip(context, Uri.fromFile(f1), isSample = true).copy(title = "Scene 1: Neon Testsrc")
                val c2 = VideoUtils.parseVideoClip(context, Uri.fromFile(f2), isSample = true).copy(title = "Scene 2: Studio Colorbars")
                val c3 = VideoUtils.parseVideoClip(context, Uri.fromFile(f3), isSample = true).copy(title = "Scene 3: Dynamic RGB Outro")

                _clips.value = listOf(c1, c2, c3)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingSamples.value = false
            }
        }
    }

    fun moveClipUp(index: Int) {
        if (index <= 0 || index >= _clips.value.size) return
        val currentList = _clips.value.toMutableList()
        val item = currentList.removeAt(index)
        currentList.add(index - 1, item)
        _clips.value = currentList
    }

    fun moveClipDown(index: Int) {
        if (index < 0 || index >= _clips.value.size - 1) return
        val currentList = _clips.value.toMutableList()
        val item = currentList.removeAt(index)
        currentList.add(index + 1, item)
        _clips.value = currentList
    }

    fun removeClip(index: Int) {
        if (index in _clips.value.indices) {
            val currentList = _clips.value.toMutableList()
            currentList.removeAt(index)
            _clips.value = currentList
        }
    }

    fun clearAll() {
        _clips.value = emptyList()
    }

    fun openPreview(clip: VideoClip) {
        _previewClip.value = clip
    }

    fun closePreview() {
        _previewClip.value = null
    }

    fun startExport(context: Context) {
        val currentClips = _clips.value
        if (currentClips.isEmpty()) {
            _exportState.value = ExportState.Error("Please add at least one video to merge.")
            return
        }

        viewModelScope.launch {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val cleanName = _exportFileName.value.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Merged_HD" }
            val outputFile = File(exportDir, "${cleanName}_$timeStamp.mp4")

            _exportState.value = ExportState.Merging(0f, 1, currentClips.size)

            val result = VideoMergerEngine.mergeClips(
                context = context,
                clips = currentClips,
                outputFile = outputFile,
                onProgress = { progress, current, total ->
                    _exportState.value = ExportState.Merging(progress, current, total)
                }
            )

            result.fold(
                onSuccess = { file ->
                    val totalDuration = currentClips.sumOf { it.durationMs }
                    _exportState.value = ExportState.Success(
                        file = file,
                        durationMs = totalDuration,
                        sizeBytes = file.length()
                    )
                },
                onFailure = { error ->
                    _exportState.value = ExportState.Error(
                        error.localizedMessage ?: "Video merge failed. Ensure clips have compatible codecs."
                    )
                }
            )
        }
    }

    fun saveExportedVideoToGallery(context: Context, onSuccess: (Uri) -> Unit, onError: (String) -> Unit) {
        val state = _exportState.value
        if (state !is ExportState.Success) return

        viewModelScope.launch {
            val result = MediaStoreExporter.exportToGallery(context, state.file, _exportFileName.value)
            result.fold(
                onSuccess = { uri ->
                    _exportState.value = state.copy(galleryUri = uri)
                    onSuccess(uri)
                },
                onFailure = { err ->
                    onError(err.localizedMessage ?: "Failed to save video to Gallery")
                }
            )
        }
    }

    fun dismissExport() {
        _exportState.value = ExportState.Idle
    }
}
