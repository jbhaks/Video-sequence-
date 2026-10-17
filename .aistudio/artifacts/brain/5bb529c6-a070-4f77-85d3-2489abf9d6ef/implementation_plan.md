# SequenceCut — High-Definition Video Merger

A sleek, studio-grade Android application designed to sequence, preview, and seamlessly merge multiple video clips into a single high-definition video without compromising visual quality, framerate, or audio fidelity.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX choices have been aligned based on your preferences:

- **Confirmed Decision 1 (Export & Quality Engine)**: Direct lossless high-definition stream remuxing (`MediaExtractor` + `MediaMuxer` timestamp sequencing) to preserve 100% of the original video resolution (1080p / 4K), bitrate, and color profile with zero compression loss, supplemented by a high-bitrate encoder fallback.
- **Confirmed Decision 2 (Clip Management & Review)**: Reorderable sequence list with live metadata (duration, resolution, aspect ratio, file size), quick reordering controls (Move Up / Move Down / Quick Rank), and an in-app interactive preview player.
- **Confirmed Decision 3 (Visual Identity)**: Dark Cinema Studio aesthetic featuring deep obsidian surfaces, sleek neon cyan and electric violet accents, glowing status indicators, and clean Material 3 typography.
- **Additional Built-in Feature**: Bundled HD sample motion clips to allow immediate one-tap testing and verification in emulator environments without needing manual media uploads.

---

## 1. Overview & Core Concept

### What It Does
**SequenceCut** allows creators, vloggers, and everyday users to pick multiple video clips from their device library, arrange them in any chronological or creative order, preview individual clips or sequence cuts with full playback controls, and export a merged high-definition video file saved directly to the device Movies/Gallery folder or shared instantly.

### Target Audience & Persona
- Content creators assembling multi-take shots, YouTube Shorts, Reels, or TikTok sequences.
- Mobile videographers who need seamless concatenation without loss of crisp detail or introducing audio sync drift.
- Everyday users merging vacation or event clips into a single unified high-definition video.

### Key Value
- **Zero-Compromise Quality**: Unlike web converters or aggressive lossy encoders that crush dynamic range and introduce macroblocking, SequenceCut preserves the native bitstream whenever possible.
- **Frictionless Sequence Ordering**: Visual cards with sequence badges (`#1`, `#2`, `#3`), duration calculation, total output length summary, and instant move controls.
- **Privacy-First & Fully Local**: No cloud uploads, no watermarks, no subscriptions; runs 100% locally on-device.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Launch & Studio Zero-State**:
   - Dynamic cinematic hero banner with neon badge "SequenceCut Studio".
   - Primary action buttons: "Select Videos" (using Android Photo Picker for zero-permission safety) and "Load Sample HD Clips" (for instant demo).
   - Empty-state guide explaining the 3-step sequence flow: *Add Clips* → *Reorder Sequence* → *Export HD Video*.

2. **Sequence Arrangement & Review**:
   - Clips appear as sequence cards displaying sequence index, high-res thumbnail, title, duration (MM:SS), resolution (e.g., 1080p, 4K), and file size.
   - Quick sequence adjustments: "Move Up", "Move Down", "Remove", and "Play Preview".
   - Sequence summary bar: Displays total number of clips, aggregated timeline duration, and estimated output file size.

3. **In-App Clip Preview Modal**:
   - Modern floating video player dialog with play/pause, scrubbable slider, elapsed/total timestamps, and volume toggle.

4. **Export & Progress Dialog**:
   - High-definition export trigger with customizable output filename.
   - Live progress indicator showing percentage, active clip being processed, and pulse animations.
   - Completion sheet with video playback, "Save to Gallery / Movies", and "Share Video" via the native Android Sharesheet.

### Visual Identity & Theme

- **Aesthetic Direction**: *Dark Cinema Studio* — professional, nocturnal, and focused.
- **Color Palette**:
  - `Background`: Deep Midnight Obsidian (`#0B0E14`)
  - `Surface / Cards`: Elevated Charcoal Slate (`#161B26`) with subtle glassmorphic borders (`#262F40`)
  - `Primary / Neon Accent`: Electric Cyan (`#00E5FF`) for primary actions and active playback
  - `Secondary Accent`: Vivid Violet (`#8B5CF6`) for sequence badges and highlights
  - `Success`: Emerald Glow (`#10B981`)
  - `On-Surface Text`: Crisp White (`#F8FAFC`) and Muted Titanium (`#94A3B8`)
- **Typography & Hierarchy**:
  - Display Headers: Heavy modern sans (`HeadlineMedium`, `TitleLarge`) for studio title and export statistics.
  - Mono-spaced Numerals: `BodyMedium` in monospaced format for timecodes (`00:14 / 01:42`) and bitrates.
- **Component Styling**:
  - Minimum 48dp touch targets on all reordering and playback controls.
  - Spring-animated list transitions when shifting sequence positions.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Video Concatenation Engine
- **Chosen Approach**: Native Android `MediaExtractor` + `MediaMuxer` sequence pipeline with timestamp recalculation and continuous PTS offset.
- **Why**: 
  - Direct bitstream copying is 100% lossless: identical resolution, zero compression generation loss, maximum visual fidelity.
  - Extremely fast: Merges multi-gigabyte 1080p/4K clips in seconds instead of heavy battery-draining CPU/GPU transcoding.
  - Built-in audio track synchronization with exact sample time offsets.
- **Fallback**: Includes dynamic format validation and error handling if input container formats or audio tracks differ.

### Decision 2: Zero-Permission Media Selection
- **Chosen Approach**: Android standard Photo Picker (`ActivityResultContracts.PickMultipleVisualMedia`).
- **Why**: Complies with modern Android storage guidelines and Google Play policies. Never requires dangerous broad storage permissions (`READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE`).
- **Export Storage**: Direct integration with `MediaStore.Video.Media` (scoped storage friendly, immediately accessible in Google Photos / Gallery).

### Decision 3: Emulator & Demo Readiness
- **Chosen Approach**: Built-in test video generator producing lightweight, high-definition test clips (motion graphic countdown leader with audio beeps).
- **Why**: Allows instant end-to-end verification and testing in the cloud development emulator without requiring the developer or user to upload MP4 test files manually.

---

## 4. Technical Architecture & Data Strategy

### System & Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    SequenceCut UI Layer                     │
├──────────────────────────────┬──────────────────────────────┤
│  TopAppBar (Studio Header)   │  Summary Bar (Clips & Time)  │
├──────────────────────────────┴──────────────────────────────┤
│                 Reorderable Clip Sequence List              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Card: [#1] Thumbnail │ Title │ 1080p │ 00:24 │ [▲] [▼] │ │
│  │ Card: [#2] Thumbnail │ Title │ 1080p │ 00:45 │ [▲] [▼] │ │
│  └────────────────────────────────────────────────────────┘ │
├──────────────────────────────┬──────────────────────────────┤
│  Preview Player BottomSheet  │   HD Export Progress Modal   │
└──────────────┬───────────────┴──────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────────┐┌─────────────────────────────┐
│     VideoMergerViewModel     ││      VideoExportService     │
├──────────────────────────────┤├─────────────────────────────┤
│ - sequenceClips: StateFlow   ││ - MediaExtractor sequence   │
│ - previewState: StateFlow    ││ - MediaMuxer container      │
│ - exportState: StateFlow     ││ - PTS offset calculator     │
└──────────────┬───────────────┘└─────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Android System Services                   │
│  • ActivityResultContracts.PickMultipleVisualMedia          │
│  • MediaStore.Video.Media (Export to Gallery)               │
│  • MediaPlayer / VideoView (In-app preview)                 │
└─────────────────────────────────────────────────────────────┘
```

### Key Data Models

```kotlin
data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    val sizeBytes: Long = 0L,
    val mimeType: String = "video/mp4"
)

sealed interface ExportState {
    object Idle : ExportState
    data class Merging(val progress: Float, val currentClipIndex: Int, val totalClips: Int) : ExportState
    data class Success(val outputUri: Uri, val outputFile: File, val durationMs: Long) : ExportState
    data class Error(val message: String) : ExportState
}
```

### Component & State Lifecycle Mapping

1. **Clip Addition**: User triggers `PickMultipleVisualMedia`. Selected URIs are inspected via `MediaMetadataRetriever` to extract duration, dimensions, and rotation, and appended to `sequenceClips`.
2. **Reordering**: Up/Down actions swap adjacent indices in the list, updating the state flow and UI with animated transitions.
3. **In-App Preview**: Selected clip URI is passed to `previewState`, launching the player bottom sheet with a smooth `VideoView` / `MediaPlayer` interface.
4. **Sequential Merge**:
   - `VideoMergerEngine` opens an output MP4 via `MediaMuxer`.
   - Adds video and audio tracks from the sequence header.
   - Iterates through clips: reads samples via `MediaExtractor`, computes continuous presentation timestamps (`ptsOffset`), writes to `MediaMuxer`.
   - On completion, registers the output in `MediaStore.Video.Media` and reveals the success preview sheet with direct sharing options.
