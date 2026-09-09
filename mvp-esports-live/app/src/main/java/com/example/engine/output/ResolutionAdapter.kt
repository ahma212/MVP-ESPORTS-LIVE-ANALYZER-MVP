package com.example.engine.output

import com.example.model.VideoOrientation
import com.example.model.VideoResolution
import kotlin.math.roundToInt

/**
 * ResolutionAdapter computes precise dimensions for the hardware video encoder
 * while preserving the native aspect ratio of the captured game / device screen.
 *
 * Hardware encoders (MediaCodec H.264/AVC) require width and height to be strictly
 * divisible by 16 (macroblock alignment) or at minimum 2 (even numbers).
 *
 * This adapter ensures:
 * 1. Aspect ratio is preserved without stretching or distortion.
 * 2. Unwanted cropping is avoided.
 * 3. Unnecessary black bars are avoided by fitting or matching device aspect ratio.
 * 4. Full support for 360p, 480p, 720p, 1080p, etc.
 */
object ResolutionAdapter {

    data class OutputDimensions(
        val width: Int,
        val height: Int,
        val aspectRatio: Float,
        val isMacroblockAligned: Boolean
    )

    /**
     * Calculates the encoder resolution matching the device aspect ratio or requested orientation.
     *
     * @param targetResolution Baseline resolution (360p, 480p, 720p, 1080p)
     * @param deviceScreenWidth Physical device screen width in pixels
     * @param deviceScreenHeight Physical device screen height in pixels
     * @param orientation Preferred video orientation (Landscape, Portrait, or Auto)
     */
    fun calculateOptimalDimensions(
        targetResolution: VideoResolution,
        deviceScreenWidth: Int = 1080,
        deviceScreenHeight: Int = 2400,
        orientation: VideoOrientation = VideoOrientation.LANDSCAPE
    ): OutputDimensions {
        val rawWidth = deviceScreenWidth.coerceAtLeast(320)
        val rawHeight = deviceScreenHeight.coerceAtLeast(320)

        // Determine if device is in landscape or portrait
        val isDeviceLandscape = rawWidth >= rawHeight
        val baseLong = maxOf(rawWidth, rawHeight)
        val baseShort = minOf(rawWidth, rawHeight)
        val deviceRatio = baseLong.toFloat() / baseShort.toFloat() // e.g. 2400 / 1080 = 2.22 (20:9)

        val targetBaseHeight = targetResolution.height // 360, 480, 720, 1080

        val (calcWidth, calcHeight) = when (orientation) {
            VideoOrientation.LANDSCAPE -> {
                // Short edge is target height (e.g. 720 or 1080)
                val width = (targetBaseHeight * deviceRatio).roundToInt()
                val height = targetBaseHeight
                alignTo16(width) to alignTo16(height)
            }
            VideoOrientation.PORTRAIT -> {
                // Short edge is target width
                val width = targetBaseHeight
                val height = (targetBaseHeight * deviceRatio).roundToInt()
                alignTo16(width) to alignTo16(height)
            }
            VideoOrientation.AUTO -> {
                if (isDeviceLandscape) {
                    val width = (targetBaseHeight * deviceRatio).roundToInt()
                    val height = targetBaseHeight
                    alignTo16(width) to alignTo16(height)
                } else {
                    val width = targetBaseHeight
                    val height = (targetBaseHeight * deviceRatio).roundToInt()
                    alignTo16(width) to alignTo16(height)
                }
            }
        }

        return OutputDimensions(
            width = calcWidth,
            height = calcHeight,
            aspectRatio = calcWidth.toFloat() / calcHeight.toFloat(),
            isMacroblockAligned = (calcWidth % 16 == 0) && (calcHeight % 16 == 0)
        )
    }

    /**
     * Standard 16:9 output calculations for YouTube Live Ingest compatibility
     * (e.g., 640x360, 854x480 -> 848x480 or 864x480, 1280x720, 1920x1080).
     */
    fun getStandard16x9Dimensions(resolution: VideoResolution): OutputDimensions {
        val width = alignTo16(resolution.width)
        val height = alignTo16(resolution.height)
        return OutputDimensions(
            width = width,
            height = height,
            aspectRatio = width.toFloat() / height.toFloat(),
            isMacroblockAligned = (width % 16 == 0) && (height % 16 == 0)
        )
    }

    /**
     * Aligns a dimension up to the nearest multiple of 16 for hardware encoder macroblocks.
     */
    fun alignTo16(dimension: Int): Int {
        val remainder = dimension % 16
        return if (remainder == 0) dimension else dimension + (16 - remainder)
    }
}
