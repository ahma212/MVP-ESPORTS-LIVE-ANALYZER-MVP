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
    fun testResolutionAdapter_returnsExactStandard16x9Dimensions() {
        // Selected resolution must remain exact (no device aspect ratio change)
        val res360 = ResolutionAdapter.calculateOptimalDimensions(
            targetResolution = VideoResolution.RES_360P,
            deviceScreenWidth = 2400,
            deviceScreenHeight = 1080,
            orientation = VideoOrientation.LANDSCAPE
        )
        assertEquals(640, res360.width)
        assertEquals(360, res360.height)

        val res480 = ResolutionAdapter.calculateOptimalDimensions(
            targetResolution = VideoResolution.RES_480P,
            deviceScreenWidth = 2400,
            deviceScreenHeight = 1080,
            orientation = VideoOrientation.LANDSCAPE
        )
        assertEquals(854, res480.width)
        assertEquals(480, res480.height)

        val res720 = ResolutionAdapter.calculateOptimalDimensions(
            targetResolution = VideoResolution.RES_720P,
            deviceScreenWidth = 2400,
            deviceScreenHeight = 1080,
            orientation = VideoOrientation.LANDSCAPE
        )
        assertEquals(1280, res720.width)
        assertEquals(720, res720.height)

        val res1080 = ResolutionAdapter.calculateOptimalDimensions(
            targetResolution = VideoResolution.RES_1080P,
            deviceScreenWidth = 2400,
            deviceScreenHeight = 1080,
            orientation = VideoOrientation.LANDSCAPE
        )
        assertEquals(1920, res1080.width)
        assertEquals(1080, res1080.height)
    }

    @Test
    fun testResolutionAdapter_portraitReturns9x16() {
        val dimensions = ResolutionAdapter.calculateOptimalDimensions(
            targetResolution = VideoResolution.RES_720P,
            deviceScreenWidth = 1080,
            deviceScreenHeight = 2400,
            orientation = VideoOrientation.PORTRAIT
        )
        assertEquals(720, dimensions.width)
        assertEquals(1280, dimensions.height)
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
