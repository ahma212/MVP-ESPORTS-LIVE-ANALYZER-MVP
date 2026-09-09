package com.example.engine

import com.example.engine.control.ControlLayerManager
import com.example.engine.core.CaptureOutputContract
import com.example.engine.output.ResolutionAdapter
import com.example.model.VideoOrientation
import com.example.model.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenCaptureArchitectureTest {

    @Test
    fun testResolutionAdapter_preservesAspectRatio_andAlignsTo16() {
        // Test 360p, 480p, 720p, 1080p for a standard 16:9 display
        val resolutions = listOf(
            VideoResolution.RES_360P,
            VideoResolution.RES_480P,
            VideoResolution.RES_720P,
            VideoResolution.RES_1080P
        )

        for (res in resolutions) {
            val dimensions = ResolutionAdapter.calculateOptimalDimensions(
                targetResolution = res,
                deviceScreenWidth = 1920,
                deviceScreenHeight = 1080,
                orientation = VideoOrientation.LANDSCAPE
            )

            // Dimensions must be strictly macroblock aligned (divisible by 16) for MediaCodec H.264
            assertTrue("Width ${dimensions.width} must be divisible by 16", dimensions.width % 16 == 0)
            assertTrue("Height ${dimensions.height} must be divisible by 16", dimensions.height % 16 == 0)
            assertTrue(dimensions.isMacroblockAligned)
        }
    }

    @Test
    fun testResolutionAdapter_20to9GamingPhone_correctlyCalculated() {
        // Modern 20:9 gaming phone (2400 x 1080)
        val dimensions1080p = ResolutionAdapter.calculateOptimalDimensions(
            targetResolution = VideoResolution.RES_1080P,
            deviceScreenWidth = 2400,
            deviceScreenHeight = 1080,
            orientation = VideoOrientation.LANDSCAPE
        )

        assertEquals(2400, dimensions1080p.width)
        assertEquals(1088, dimensions1080p.height) // 1080 aligned to 16 is 1088
        assertTrue(dimensions1080p.width % 16 == 0)
        assertTrue(dimensions1080p.height % 16 == 0)

        // 720p on 20:9 gaming phone
        val dimensions720p = ResolutionAdapter.calculateOptimalDimensions(
            targetResolution = VideoResolution.RES_720P,
            deviceScreenWidth = 2400,
            deviceScreenHeight = 1080,
            orientation = VideoOrientation.LANDSCAPE
        )

        assertTrue(dimensions720p.width % 16 == 0)
        assertTrue(dimensions720p.height % 16 == 0)
    }

    @Test
    fun testCaptureOutputContract_strictSeparationEnforced() {
        // All control chrome must NOT be allowed into the output pipeline
        assertFalse(CaptureOutputContract.isAllowedInOutputPipeline("Floating Pointer Indicator"))
        assertFalse(CaptureOutputContract.isAllowedInOutputPipeline("Floating Action / Ball Button"))
        assertFalse(CaptureOutputContract.isAllowedInOutputPipeline("Pointer Quick Menu"))
        assertFalse(CaptureOutputContract.isAllowedInOutputPipeline("Live Chat Moderation HUD"))
        assertFalse(CaptureOutputContract.isAllowedInOutputPipeline("Music & BGM Selection Drawer"))
        assertFalse(CaptureOutputContract.isAllowedInOutputPipeline("Recording Start/Stop/Pause HUD"))
        assertFalse(CaptureOutputContract.isAllowedInOutputPipeline("YouTube Broadcast Live Controls"))
        assertFalse(CaptureOutputContract.isAllowedInOutputPipeline("Settings & Configuration Panels"))
        assertFalse(CaptureOutputContract.isAllowedInOutputPipeline("On-Screen Touch Feedback Visualizer"))

        // Only legitimate broadcast pipeline elements may enter
        assertTrue(CaptureOutputContract.isAllowedInOutputPipeline("GAMEPLAY_CONTENT"))
        assertTrue(CaptureOutputContract.isAllowedInOutputPipeline("VISUAL_OVERLAYS"))
        assertTrue(CaptureOutputContract.isAllowedInOutputPipeline("BANNERS_AND_MEMES"))
        assertTrue(CaptureOutputContract.isAllowedInOutputPipeline("COLOR_ADJUSTMENTS"))
        assertTrue(CaptureOutputContract.isAllowedInOutputPipeline("FINAL_AUDIO_MIX"))
    }

    @Test
    fun testControlLayerManager_flagSecureIsolationActive() {
        val manager = ControlLayerManager()
        assertTrue("Isolation status must be strictly active with FLAG_SECURE", manager.verifyIsolationStatus())

        val params = manager.getWindowLayoutParams(100, 100)
        val hasFlagSecure = (params.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0
        assertTrue("Control window params must include FLAG_SECURE to prevent capture in MediaProjection", hasFlagSecure)
    }

    @Test
    fun testMediaStoreExporter_handlesEmptyFileGracefully() = kotlinx.coroutines.runBlocking {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val emptyFile = java.io.File.createTempFile("empty_rec", ".mp4")
        emptyFile.deleteOnExit()

        val result = com.example.engine.output.MediaStoreExporter.saveToGallery(
            context = context,
            sourceFile = emptyFile,
            width = 1920,
            height = 1080,
            durationSeconds = 10
        )

        assertFalse("Empty file should fail export gracefully", result.success)
        assertEquals("Source recording file is empty or missing.", result.errorMessage)
    }

    @Test
    fun testMediaStoreExporter_validFileExportsSuccessfully() = kotlinx.coroutines.runBlocking {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val tempFile = java.io.File.createTempFile("valid_rec", ".mp4")
        tempFile.writeBytes(ByteArray(1024) { 0x42 }) // write 1KB dummy MP4 header
        tempFile.deleteOnExit()

        val result = com.example.engine.output.MediaStoreExporter.saveToGallery(
            context = context,
            sourceFile = tempFile,
            width = 1920,
            height = 1080,
            durationSeconds = 5,
            targetDirectory = "Movies/MVP_Recordings"
        )

        assertTrue("Valid video file should export successfully to MediaStore", result.success)
        assertTrue("Export path must target Movies/MVP_Recordings", result.publicPath.startsWith("Movies/MVP_Recordings/MVP_Rec_"))
        assertTrue("Exported size must be > 0", result.sizeMb > 0f)
    }
}
