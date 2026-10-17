package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoClip
import com.example.ui.VideoMergerViewModel
import com.example.ui.components.ExportModal
import com.example.ui.components.SequenceClipCard
import com.example.ui.components.SequenceSummaryBar
import com.example.ui.components.StudioEmptyState
import com.example.ui.components.StudioHeader
import com.example.ui.components.VideoPreviewDialog
import com.example.ui.theme.CinemaBackground
import com.example.ui.theme.CinemaSurfaceBorder
import com.example.ui.theme.CinemaSurfaceElevated
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SequenceCutTheme
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

class MainActivity : ComponentActivity() {

    private val viewModel: VideoMergerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SequenceCutTheme {
                SequenceCutApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SequenceCutApp(
    viewModel: VideoMergerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clips by viewModel.clips.collectAsState()
    val previewClip by viewModel.previewClip.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val isLoadingSamples by viewModel.isLoadingSamples.collectAsState()
    val isAnalyzingVideos by viewModel.isAnalyzingVideos.collectAsState()

    // Android Zero-Permission Photo/Video Picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addClips(context, uris)
        }
    }

    val launchPicker = {
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = CinemaBackground,
        topBar = {
            StudioHeader(
                clipCount = clips.size,
                onExportClicked = { viewModel.startExport(context) },
                onLoadSamplesClicked = { viewModel.loadSampleClips(context) },
                onClearAllClicked = { viewModel.clearAll() }
            )
        },
        bottomBar = {
            if (clips.isNotEmpty()) {
                BottomStudioBar(
                    clipCount = clips.size,
                    onAddMore = launchPicker,
                    onExport = { viewModel.startExport(context) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (clips.isEmpty()) {
                StudioEmptyState(
                    onSelectVideos = launchPicker,
                    onLoadDemoClips = { viewModel.loadSampleClips(context) },
                    isLoadingSamples = isLoadingSamples,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        SequenceSummaryBar(
                            clipCount = clips.size,
                            totalDurationMs = viewModel.totalDurationMs,
                            totalBytes = viewModel.totalEstimatedBytes,
                            onAddMoreClicked = launchPicker
                        )
                    }

                    item {
                        TimelineSectionHeader(clipCount = clips.size)
                    }

                    itemsIndexed(
                        items = clips,
                        key = { _, item -> item.id }
                    ) { index, clip ->
                        SequenceClipCard(
                            clip = clip,
                            index = index,
                            totalClips = clips.size,
                            onMoveUp = { viewModel.moveClipUp(index) },
                            onMoveDown = { viewModel.moveClipDown(index) },
                            onRemove = { viewModel.removeClip(index) },
                            onPreview = { viewModel.openPreview(clip) }
                        )
                    }
                }
            }

            // Loading overlay for video analysis
            AnimatedVisibility(
                visible = isAnalyzingVideos,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CinemaBackground.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(CinemaSurfaceElevated, RoundedCornerShape(16.dp))
                            .border(1.dp, CinemaSurfaceBorder, RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        CircularProgressIndicator(color = NeonCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Analyzing Video Metadata...",
                            color = TextWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // Clip Preview Dialog
    previewClip?.let { clip ->
        VideoPreviewDialog(
            clip = clip,
            onDismiss = { viewModel.closePreview() }
        )
    }

    // Export Progress & Success Modal
    ExportModal(
        state = exportState,
        onDismiss = { viewModel.dismissExport() },
        onSaveToGallery = { ctx, onSuccess, onError ->
            viewModel.saveExportedVideoToGallery(ctx, onSuccess, onError)
        }
    )
}

@Composable
private fun TimelineSectionHeader(clipCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "SEQUENCE TIMELINE",
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Text(
            text = "Plays #1 → #$clipCount in order",
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun BottomStudioBar(
    clipCount: Int,
    onAddMore: () -> Unit,
    onExport: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CinemaBackground)
            .border(width = 1.dp, color = CinemaSurfaceBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onAddMore,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaSurfaceElevated,
                    contentColor = TextWhite
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.45f)
                    .height(48.dp)
                    .testTag("bottom_add_clips_button")
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = NeonCyan
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Videos",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = onExport,
                enabled = clipCount >= 2,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = TextDark,
                    disabledContainerColor = CinemaSurfaceElevated,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.55f)
                    .height(48.dp)
                    .testTag("bottom_export_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Merge & Export HD",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
