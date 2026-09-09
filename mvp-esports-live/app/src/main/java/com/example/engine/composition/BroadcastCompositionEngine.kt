package com.example.engine.composition

import android.view.WindowManager

/**
 * OutputCompositionEngine enforces the strict separation between:
 * 1. StreamOutputComposition (Game surface + authorized stream overlays: facecam, lower-third sponsor ticker, watermark)
 * 2. StudioControlLayer (Floating buttons, chat moderation drawers, sliders, settings, dialogs, app navigation)
 *
 * CRITICAL RULE:
 * Any control chrome, floating buttons, settings panels, or recording HUDs must NEVER be routed
 * to the MediaCodec encoder or the YouTube RTMP stream.
 */
object OutputCompositionEngine {

    /**
     * WindowManager layout flags applied to all floating controls, HUDs, and setting overlays
     * to prevent them from being captured into screen recording surfaces.
     */
    const val OVERLAY_CONTROL_FLAGS = (
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    )

    /**
     * Identifies which visual elements are authorized to enter the encoder surface vs control layer.
     */
    enum class OutputTarget {
        /** Sent strictly to MediaCodec / YouTube RTMP VirtualDisplay */
        ENCODER_OUTPUT_STREAM,

        /** Rendered on device display only, completely isolated from encoder */
        DEVICE_CONTROL_SURFACE_ONLY
    }

    data class LayerRule(
        val elementName: String,
        val target: OutputTarget,
        val isolationMethod: String
    )

    val PIPELINE_RULES = listOf(
        LayerRule("Game / Screen Video Frame", OutputTarget.ENCODER_OUTPUT_STREAM, "MediaProjection VirtualDisplay"),
        LayerRule("Streamer Facecam PiP", OutputTarget.ENCODER_OUTPUT_STREAM, "Composed in OpenGL Shader Pipeline"),
        LayerRule("Lower-Third Sponsor Ticker", OutputTarget.ENCODER_OUTPUT_STREAM, "Composed in OpenGL Shader Pipeline"),
        LayerRule("Esports Brand Watermark", OutputTarget.ENCODER_OUTPUT_STREAM, "Composed in OpenGL Shader Pipeline"),
        LayerRule("Floating Control Button (Ball)", OutputTarget.DEVICE_CONTROL_SURFACE_ONLY, "WindowManager Overlay Surface (Excluded from VirtualDisplay)"),
        LayerRule("Live Chat Moderation Drawer", OutputTarget.DEVICE_CONTROL_SURFACE_ONLY, "App View Hierarchy Window"),
        LayerRule("Recording & Bitrate Controls", OutputTarget.DEVICE_CONTROL_SURFACE_ONLY, "App View Hierarchy Window"),
        LayerRule("Audio Mixers & Music Sliders", OutputTarget.DEVICE_CONTROL_SURFACE_ONLY, "App View Hierarchy Window"),
        LayerRule("Settings & Permission Dialogs", OutputTarget.DEVICE_CONTROL_SURFACE_ONLY, "App View Hierarchy Window"),
        LayerRule("Touch Pointer Visualizer", OutputTarget.DEVICE_CONTROL_SURFACE_ONLY, "Window Overlay Layer")
    )
}
