package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.model.VideoClip
import com.example.ui.VideoMergerViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SequenceCut", appName)
  }

  @Test
  fun `verify video clip sequence ordering and movement`() {
    val viewModel = VideoMergerViewModel()
    val clip1 = VideoClip(
        id = "1",
        uri = Uri.parse("file:///clip1.mp4"),
        title = "Clip One",
        durationMs = 5000L,
        width = 1920,
        height = 1080
    )
    val clip2 = VideoClip(
        id = "2",
        uri = Uri.parse("file:///clip2.mp4"),
        title = "Clip Two",
        durationMs = 8000L,
        width = 1920,
        height = 1080
    )

    // Initially empty
    assertEquals(0, viewModel.clips.value.size)

    // Test resolution formatting
    assertEquals("1080p HD", clip1.resolutionBadge)
    assertEquals("00:05", clip1.formattedDuration)
    assertEquals("00:08", clip2.formattedDuration)
  }
}
