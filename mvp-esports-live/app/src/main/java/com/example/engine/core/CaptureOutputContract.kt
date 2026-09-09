package com.example.engine.core

import android.view.WindowManager

/**
 * CaptureOutputContract defines the strict architectural separation between:
 *
 * 1. CONTROL LAYER:
 *    Interactive user-facing UI, floating controls, settings, and touch feedback.
 *    These MUST NEVER appear in any recorded video file or live stream transmission.
 *
 * 2. OUTPUT LAYER:
 *    Screen capture input, composed overlays, color grading, hardware encoder, and output sinks.
 *    This contains exclusively gameplay/device video and intentionally authorized broadcast graphics.
 */
object CaptureOutputContract {

    /**
     * WindowManager flags that ensure Android's native compositor and screen capture system
     * (MediaProjection / VirtualDisplay) automatically exclude all control chrome.
     */
    const val ISOLATED_CONTROL_WINDOW_FLAGS = (
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_SECURE // Prevents capture into MediaProjection VirtualDisplay
    )

    enum class LayerClassification {
        CONTROL_LAYER_ONLY,
        OUTPUT_LAYER_PIPELINE
    }

    enum class ControlComponent(val label: String, val isolationMechanism: String) {
        FLOATING_POINTER("Floating Pointer Indicator", "WindowManager Secure Overlay"),
        FLOATING_BUTTON("Floating Action / Ball Button", "WindowManager Secure Window"),
        POINTER_MENU("Pointer Quick Menu", "Secure Floating Popup"),
        CHAT_UI("Live Chat Moderation HUD", "In-App Private View Hierarchy"),
        MUSIC_SELECTION_UI("Music & BGM Selection Drawer", "In-App Private View Hierarchy"),
        RECORDING_UI("Recording Start/Stop/Pause HUD", "In-App Private View Hierarchy"),
        LIVE_CONTROLS("YouTube Broadcast Live Controls", "In-App Private View Hierarchy"),
        SETTINGS_PANELS("Settings & Configuration Panels", "In-App Private View Hierarchy"),
        APP_PANELS("Station Navigation & Management Panels", "Activity DecorView"),
        TOUCH_CONTROLS("On-Screen Touch Feedback Visualizer", "Secure Touch Overlay Window");

        val classification: LayerClassification = LayerClassification.CONTROL_LAYER_ONLY
    }

    enum class OutputComponent(val label: String, val routingPipeline: String) {
        GAMEPLAY_CONTENT("Device Screen / Game Video", "MediaProjection -> Hardware Input Surface"),
        VISUAL_OVERLAYS("Facecam PiP / Watermark", "Hardware Shader / Canvas Composition"),
        BANNERS_AND_MEMES("Sponsor Ticker / Memes / Photos", "Hardware Overlay Composition"),
        COLOR_ADJUSTMENTS("Brightness / Contrast / Saturation / LUT", "EGL / MediaCodec Color Pipeline"),
        FINAL_AUDIO_MIX("Game Internal Audio + Clear Mic Audio", "AudioRecord / AudioPlaybackCapture -> AAC Muxer");

        val classification: LayerClassification = LayerClassification.OUTPUT_LAYER_PIPELINE
    }

    /**
     * Validates whether a component is permitted to enter the hardware encoder surface.
     */
    fun isAllowedInOutputPipeline(elementName: String): Boolean {
        return OutputComponent.values().any { it.name.equals(elementName, ignoreCase = true) || it.label.equals(elementName, ignoreCase = true) }
    }
}
