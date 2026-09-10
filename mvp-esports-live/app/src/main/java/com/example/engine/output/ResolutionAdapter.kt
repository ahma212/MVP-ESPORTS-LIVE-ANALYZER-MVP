package com.example.engine.output

import com.example.model.VideoOrientation
import com.example.model.VideoResolution

/**
 * ResolutionAdapter
 *
 * The selected VideoResolution is the actual OUTPUT resolution.
 *
 * Landscape output:
 * 360p  = 640x360
 * 480p  = 854x480
 * 720p  = 1280x720
 * 1080p = 1920x1080
 * 1440p = 2560x1440
 * 4K    = 3840x2160
 *
 * The physical device aspect ratio is NOT used to change the
 * requested encoder output resolution.
 *
 * The device/game screen is captured into this fixed output canvas
 * and the GPU compositor handles the visual scaling/composition.
 */
object ResolutionAdapter {

    data class OutputDimensions(
        val width: Int,
        val height: Int,
        val aspectRatio: Float,
        val isMacroblockAligned: Boolean
    )

    /**
     * Returns the exact standard output dimensions requested by the user.
     *
     * The previous implementation changed the output dimensions
     * according to the physical device aspect ratio. That could turn
     * a 1080p request on a 20:9 phone into approximately 2400x1080.
     *
     * That is NOT desired for the broadcast output.
     *
     * For landscape gameplay, the selected resolution itself defines
     * the encoder canvas.
     */
    fun calculateOptimalDimensions(
        targetResolution: VideoResolution,
        deviceScreenWidth: Int = 1080,
        deviceScreenHeight: Int = 2400,
        orientation: VideoOrientation = VideoOrientation.LANDSCAPE
    ): OutputDimensions {

        return when (orientation) {

            VideoOrientation.LANDSCAPE -> {
                getStandard16x9Dimensions(targetResolution)
            }

            VideoOrientation.PORTRAIT -> {
                getStandard9x16Dimensions(targetResolution)
            }

            VideoOrientation.AUTO -> {
                val isDeviceLandscape =
                    deviceScreenWidth >= deviceScreenHeight

                if (isDeviceLandscape) {
                    getStandard16x9Dimensions(targetResolution)
                } else {
                    getStandard9x16Dimensions(targetResolution)
                }
            }
        }
    }

    /**
     * Standard landscape 16:9 output.
     *
     * IMPORTANT:
     * Do NOT round 854x480 up to 864x480.
     * 854x480 is already an even MediaCodec-compatible dimension.
     *
     * The selected resolution must remain the selected resolution.
     */
    fun getStandard16x9Dimensions(
        resolution: VideoResolution
    ): OutputDimensions {

        val width = ensureEven(resolution.width)
        val height = ensureEven(resolution.height)

        return OutputDimensions(
            width = width,
            height = height,
            aspectRatio = width.toFloat() / height.toFloat(),
            isMacroblockAligned =
                (width % 16 == 0) && (height % 16 == 0)
        )
    }

    /**
     * Standard portrait 9:16 output.
     *
     * Example:
     * 360p  = 360x640
     * 480p  = 480x854
     * 720p  = 720x1280
     * 1080p = 1080x1920
     */
    fun getStandard9x16Dimensions(
        resolution: VideoResolution
    ): OutputDimensions {

        val width = ensureEven(resolution.height)
        val height = ensureEven(resolution.width)

        return OutputDimensions(
            width = width,
            height = height,
            aspectRatio = width.toFloat() / height.toFloat(),
            isMacroblockAligned =
                (width % 16 == 0) && (height % 16 == 0)
        )
    }

    /**
     * MediaCodec requires dimensions to be even.
     *
     * We deliberately do NOT force every dimension to a multiple
     * of 16 because doing that would change the requested resolution.
     *
     * Example:
     * 854x480 must remain 854x480.
     */
    private fun ensureEven(dimension: Int): Int {
        return if (dimension % 2 == 0) {
            dimension
        } else {
            dimension + 1
        }
    }

    /**
     * Kept for compatibility with existing code that may use this helper.
     *
     * This method is intentionally explicit rather than being used
     * automatically for the selected broadcast resolution, because
     * rounding a requested resolution upward would change the user's
     * selected output.
     */
    fun alignTo16(dimension: Int): Int {
        val remainder = dimension % 16
        return if (remainder == 0) {
            dimension
        } else {
            dimension + (16 - remainder)
        }
    }
}