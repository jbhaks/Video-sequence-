package com.example.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoMergerEngine {

    suspend fun mergeClips(
        context: Context,
        clips: List<VideoClip>,
        outputFile: File,
        onProgress: (progress: Float, currentClip: Int, totalClips: Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (clips.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No clips selected to merge."))
        }

        if (outputFile.exists()) {
            outputFile.delete()
        }

        var muxer: MediaMuxer? = null

        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Step 1: Scan master video and audio formats from the first viable clip
            var masterVideoFormat: MediaFormat? = null
            var masterAudioFormat: MediaFormat? = null
            var masterRotation = clips.firstOrNull()?.rotation ?: 0

            for (clip in clips) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(context, clip.uri, null)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("video/") && masterVideoFormat == null) {
                            masterVideoFormat = format
                            if (clip.rotation != 0) {
                                masterRotation = clip.rotation
                            }
                        } else if (mime.startsWith("audio/") && masterAudioFormat == null) {
                            masterAudioFormat = format
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    extractor.release()
                }

                if (masterVideoFormat != null && masterAudioFormat != null) {
                    break
                }
            }

            if (masterVideoFormat == null) {
                return@withContext Result.failure(IllegalStateException("No valid video stream detected in the selected clips."))
            }

            val muxerVideoTrackIndex = muxer.addTrack(masterVideoFormat)
            val muxerAudioTrackIndex = if (masterAudioFormat != null) {
                muxer.addTrack(masterAudioFormat)
            } else {
                -1
            }

            if (masterRotation != 0) {
                muxer.setOrientationHint(masterRotation)
            }

            muxer.start()

            // Step 2: Sequentially process each clip's bitstream
            var videoPtsOffsetUs = 0L
            var audioPtsOffsetUs = 0L
            val maxBufferSize = 2 * 1024 * 1024 // 2MB for high-definition video frames
            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            for (clipIndex in clips.indices) {
                val clip = clips[clipIndex]
                val extractor = MediaExtractor()

                try {
                    extractor.setDataSource(context, clip.uri, null)

                    var clipVideoTrack = -1
                    var clipAudioTrack = -1

                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("video/") && clipVideoTrack == -1) {
                            clipVideoTrack = i
                        } else if (mime.startsWith("audio/") && clipAudioTrack == -1) {
                            clipAudioTrack = i
                        }
                    }

                    var clipMaxVideoPts = 0L
                    var clipFirstVideoPts = -1L

                    // 2a. Process Video Track
                    if (clipVideoTrack != -1) {
                        extractor.selectTrack(clipVideoTrack)

                        while (true) {
                            buffer.clear()
                            val sampleSize = extractor.readSampleData(buffer, 0)
                            if (sampleSize < 0) break

                            val rawTime = extractor.sampleTime
                            if (clipFirstVideoPts == -1L) {
                                clipFirstVideoPts = rawTime
                            }

                            val adjustedPts = (rawTime - clipFirstVideoPts) + videoPtsOffsetUs
                            clipMaxVideoPts = maxOf(clipMaxVideoPts, adjustedPts)

                            bufferInfo.offset = 0
                            bufferInfo.size = sampleSize
                            bufferInfo.presentationTimeUs = adjustedPts
                            bufferInfo.flags = extractor.sampleFlags

                            muxer.writeSampleData(muxerVideoTrackIndex, buffer, bufferInfo)
                            extractor.advance()
                        }

                        extractor.unselectTrack(clipVideoTrack)
                    }

                    // 2b. Process Audio Track (if both muxer and clip support audio)
                    var clipMaxAudioPts = 0L
                    var clipFirstAudioPts = -1L

                    if (muxerAudioTrackIndex != -1 && clipAudioTrack != -1) {
                        extractor.selectTrack(clipAudioTrack)

                        while (true) {
                            buffer.clear()
                            val sampleSize = extractor.readSampleData(buffer, 0)
                            if (sampleSize < 0) break

                            val rawTime = extractor.sampleTime
                            if (clipFirstAudioPts == -1L) {
                                clipFirstAudioPts = rawTime
                            }

                            val adjustedPts = (rawTime - clipFirstAudioPts) + audioPtsOffsetUs
                            clipMaxAudioPts = maxOf(clipMaxAudioPts, adjustedPts)

                            bufferInfo.offset = 0
                            bufferInfo.size = sampleSize
                            bufferInfo.presentationTimeUs = adjustedPts
                            bufferInfo.flags = extractor.sampleFlags

                            muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                            extractor.advance()
                        }

                        extractor.unselectTrack(clipAudioTrack)
                    }

                    // Advance sequence PTS offsets for the next clip in chain
                    val declaredDurationUs = if (clip.durationMs > 0) clip.durationMs * 1000L else 3_000_000L
                    val effectiveVideoDuration = if (clipMaxVideoPts > videoPtsOffsetUs) {
                        clipMaxVideoPts - videoPtsOffsetUs + 33_333L // ~1 frame margin
                    } else {
                        declaredDurationUs
                    }

                    videoPtsOffsetUs += effectiveVideoDuration
                    audioPtsOffsetUs += if (clipMaxAudioPts > audioPtsOffsetUs) {
                        clipMaxAudioPts - audioPtsOffsetUs + 23_220L
                    } else {
                        effectiveVideoDuration
                    }

                    val progress = (clipIndex + 1).toFloat() / clips.size
                    onProgress(progress, clipIndex + 1, clips.size)

                } finally {
                    extractor.release()
                }
            }

            try {
                muxer.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try {
                muxer?.release()
            } catch (ignored: Exception) {}
        }
    }
}
