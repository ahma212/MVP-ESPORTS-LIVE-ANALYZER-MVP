package com.example.model

enum class VideoResolution(val label: String, val width: Int, val height: Int, val defaultBitrateMbps: Int) {
    RES_360P("360p SD", 640, 360, 2),
    RES_480P("480p SD", 854, 480, 3),
    RES_720P("720p HD", 1280, 720, 5),
    RES_1080P("1080p FHD", 1920, 1080, 8),
    RES_1440P("1440p 2K", 2560, 1440, 12),
    RES_4K("4K UHD", 3840, 2160, 20)
}

enum class VideoFps(val label: String, val fpsValue: Int) {
    FPS_30("30 FPS", 30),
    FPS_60("60 FPS", 60),
    FPS_90("90 FPS (Pro)", 90),
    FPS_120("120 FPS (Ultra)", 120)
}

enum class VideoCodec(val label: String, val mimeType: String) {
    H264("H.264 (AVC - High Compatibility)", "video/avc"),
    HEVC("HEVC (H.265 - Efficient)", "video/hevc"),
    AV1("AV1 (Next-Gen)", "video/av01")
}

enum class VideoOrientation(val label: String) {
    AUTO("Auto Detect"),
    LANDSCAPE("Landscape (Gaming)"),
    PORTRAIT("Portrait (Shorts/Feed)")
}

enum class FacecamShape(val label: String) {
    HEXAGON("Esports Hexagon"),
    CIRCLE("Circle"),
    ROUNDED_RECT("Rounded Rect")
}

enum class ColorLutPreset(val label: String, val description: String) {
    VIBRANT_ESPORTS("Vibrant Esports", "Boosts enemy visibility and saturated team colors"),
    HIGH_CONTRAST_FPS("FPS Precision", "Elevates shadows and sharpens crosshair targets"),
    NIGHT_OPS("Night Ops Tactical", "Reduces harsh glares and deepens dark tones"),
    CYBERPUNK("Cyberpunk Neon", "Stylized teal & magenta gaming aesthetic"),
    NATURAL("Natural Game Color", "Standard uncompressed game feed color")
}

enum class PointerStyle(val label: String) {
    CYBER_RING("Cyber Ring"),
    GAMING_CROSSHAIR("Esports Crosshair"),
    NEON_DOT("Neon Dot")
}

enum class RecordingState {
    IDLE,
    PREPARING,
    RECORDING,
    PAUSED,
    SAVING
}

data class RecordingConfig(
    val resolution: VideoResolution = VideoResolution.RES_1080P,
    val fps: VideoFps = VideoFps.FPS_60,
    val bitrateMbps: Int = 12,
    val codec: VideoCodec = VideoCodec.H264,
    val orientation: VideoOrientation = VideoOrientation.LANDSCAPE
)

data class AudioConfig(
    val masterVolume: Float = 1.0f,
    val isMasterMuted: Boolean = false,
    val internalAudioEnabled: Boolean = true,
    val internalAudioMuted: Boolean = false,
    val internalAudioVolume: Float = 0.85f,
    val internalPeakLevel: Float = 0f,
    val micEnabled: Boolean = true,
    val micMuted: Boolean = false,
    val micVolume: Float = 1.0f,
    val micPeakLevel: Float = 0f,
    val noiseSuppression: Boolean = true,
    val echoCancellation: Boolean = true,
    val voiceClarityBoost: Boolean = true,
    val isNoiseSuppressorActive: Boolean = false,
    val isAcousticEchoCancelerActive: Boolean = false,
    val isAutomaticGainControlActive: Boolean = false,
    val musicEnabled: Boolean = true,
    val musicMuted: Boolean = false,
    val musicVolume: Float = 0.40f,
    val musicLooping: Boolean = true,
    val musicPlaybackState: com.example.engine.audio.MusicPlaybackState = com.example.engine.audio.MusicPlaybackState.STOPPED,
    val musicTrackTitle: String? = null,
    val musicTrackArtist: String? = null,
    val musicTrackUri: String? = null,
    val musicDurationMs: Long = 0L,
    val musicCurrentPositionMs: Long = 0L,
    val musicPeakLevel: Float = 0f,
    val audioDucking: Boolean = true,
    val duckingStrength: Float = 0.60f,
    val masterPeakLevel: Float = 0f
)

data class OverlayConfig(
    val facecamEnabled: Boolean = true,
    val facecamShape: FacecamShape = FacecamShape.HEXAGON,
    val facecamBorderNeon: Boolean = true,
    val facecamOpacity: Float = 1.0f,
    val watermarkEnabled: Boolean = true,
    val watermarkText: String = "MVP ESPORTS LIVE",
    val teamLogoEnabled: Boolean = true,
    val memeStingersEnabled: Boolean = true
)

data class BannerStripConfig(
    val stripEnabled: Boolean = true,
    val tickerText: String = "FOLLOW @MVP_ESPORTS ON YOUTUBE | GRAND FINALS TODAY",
    val socialHandle: String = "@mvp_esports",
    val sponsorName: String = "TITAN RIGS • APEX FUEL",
    val animateTicker: Boolean = true
)

data class VideoAdjustmentConfig(
    val isEnabled: Boolean = true,
    val brightnessEnabled: Boolean = true,
    val brightness: Float = 0.0f,          // Range: -0.50f to +0.50f, Default: 0.0f
    val contrastEnabled: Boolean = true,
    val contrast: Float = 1.0f,            // Range: 0.20f to 2.00f, Default: 1.0f (1.0 = 100% neutral)
    val saturationEnabled: Boolean = true,
    val saturation: Float = 1.0f,          // Range: 0.00f to 2.50f, Default: 1.0f (1.0 = 100% neutral, 0 = grayscale)
    val sharpness: Float = 0.0f,           // Range: 0.0f to 1.0f
    val colorLutPreset: ColorLutPreset = ColorLutPreset.NATURAL
) {
    companion object {
        const val DEFAULT_BRIGHTNESS = 0.0f
        const val DEFAULT_CONTRAST = 1.0f
        const val DEFAULT_SATURATION = 1.0f

        const val MIN_BRIGHTNESS = -0.50f
        const val MAX_BRIGHTNESS = 0.50f

        const val MIN_CONTRAST = 0.20f
        const val MAX_CONTRAST = 2.00f

        const val MIN_SATURATION = 0.00f
        const val MAX_SATURATION = 2.50f
    }

    val isDefault: Boolean
        get() = brightness == DEFAULT_BRIGHTNESS &&
                contrast == DEFAULT_CONTRAST &&
                saturation == DEFAULT_SATURATION &&
                colorLutPreset == ColorLutPreset.NATURAL

    val effectiveBrightness: Float
        get() = if (isEnabled && brightnessEnabled) brightness.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS) else DEFAULT_BRIGHTNESS

    val effectiveContrast: Float
        get() = if (isEnabled && contrastEnabled) contrast.coerceIn(MIN_CONTRAST, MAX_CONTRAST) else DEFAULT_CONTRAST

    val effectiveSaturation: Float
        get() = if (isEnabled && saturationEnabled) saturation.coerceIn(MIN_SATURATION, MAX_SATURATION) else DEFAULT_SATURATION
}

data class PointerConfig(
    val showTouches: Boolean = true,
    val pointerStyle: PointerStyle = PointerStyle.CYBER_RING,
    val pointerSizeDp: Int = 32,
    val pointerColorHex: String = "#00F0FF"
)

enum class GameScaleMode(val label: String, val description: String) {
    FIT("Aspect Fit", "Preserves exact aspect ratio without distortion or unwanted crop"),
    FILL("Aspect Fill", "Scales to fill 16:9 canvas cleanly (no black bars)"),
    FULLSCREEN("Stretch Full", "Stretches to 100% canvas bounds"),
    CUSTOM("Custom Transform", "Full manual control over position, zoom, scale, and 4-way crop")
}

data class GameVideoConfig(
    val scaleMode: GameScaleMode = GameScaleMode.FIT,
    val xPercent: Float = 0.5f,        // Center X (0.0 to 1.0)
    val yPercent: Float = 0.5f,        // Center Y (0.0 to 1.0)
    val widthPercent: Float = 1.0f,    // Width percent of canvas (0.1 to 2.0)
    val heightPercent: Float = 1.0f,   // Height percent of canvas (0.1 to 2.0)
    val scale: Float = 1.0f,           // Zoom/scale multiplier (0.2x to 3.0x)
    val cropLeft: Float = 0.0f,        // 0.0 to 0.45
    val cropTop: Float = 0.0f,         // 0.0 to 0.45
    val cropRight: Float = 0.0f,       // 0.0 to 0.45
    val cropBottom: Float = 0.0f,      // 0.0 to 0.45
    val rotationDeg: Float = 0.0f,     // -180 to 180
    val opacity: Float = 1.0f,         // 0.0 to 1.0
    val isVisible: Boolean = true,
    val backgroundColorHex: String = "#FF050A14"
)

enum class CompositionElementType(val label: String, val badgeText: String) {
    PHOTO("Photo / Image", "IMG"),
    PNG("PNG Graphic / Logo", "PNG"),
    MEME("Meme / Stinger", "MEME"),
    VIDEO("Video Clip / PiP", "VIDEO"),
    BANNER("Top Sponsor Banner", "BANNER"),
    BOTTOM_STRIP("Bottom Marquee Strip", "STRIP"),
    CUSTOM_GRAPHICS("Custom Graphics Frame", "GFX")
}

data class CompositionElement(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: CompositionElementType,
    val isVisible: Boolean = true,
    val xPercent: Float = 0.5f,       // Center X normalized (0.0 to 1.0)
    val yPercent: Float = 0.5f,       // Center Y normalized (0.0 to 1.0)
    val widthPercent: Float = 0.35f,   // Width relative to canvas (0.05 to 1.0)
    val heightPercent: Float = 0.20f,  // Height relative to canvas (0.05 to 1.0)
    val cropLeft: Float = 0.0f,       // 0.0 to 0.49
    val cropTop: Float = 0.0f,
    val cropRight: Float = 0.0f,
    val cropBottom: Float = 0.0f,
    val scale: Float = 1.0f,          // 0.2 to 3.0
    val rotationDeg: Float = 0.0f,     // -180 to 180
    val opacity: Float = 1.0f,        // 0.0 to 1.0
    val zIndex: Int = 0,              // Layer stack order
    val contentUri: String? = null,   // Gallery Image / Video URI
    val titleText: String? = null,    // Text for Banner / Bottom Strip / Meme
    val subtitleText: String? = null, // Secondary text / Marquee subtext
    val bannerBgColorHex: String = "#E60A101C",
    val accentColorHex: String = "#FF00F0FF",
    val loopVideo: Boolean = true,
    val isVideoPlaying: Boolean = true
)

data class CompositionConfig(
    val gameVideoConfig: GameVideoConfig = GameVideoConfig(),
    val elements: List<CompositionElement> = listOf(
        CompositionElement(
            id = "default_bottom_strip",
            name = "Esports Ticker Marquee",
            type = CompositionElementType.BOTTOM_STRIP,
            isVisible = true,
            xPercent = 0.5f,
            yPercent = 0.94f,
            widthPercent = 0.96f,
            heightPercent = 0.08f,
            titleText = "🔴 LIVE ESPORTS • GRAND FINALS • ROAD TO CONQUEROR",
            subtitleText = "DONATE / SUPPORT: MVP_ESPORTS • SUBSCRIBER GOAL: 4.8K/5K",
            accentColorHex = "#FF00F0FF",
            bannerBgColorHex = "#E60B0F19",
            zIndex = 10
        ),
        CompositionElement(
            id = "default_top_banner",
            name = "Top Sponsor Header",
            type = CompositionElementType.BANNER,
            isVisible = false,
            xPercent = 0.5f,
            yPercent = 0.06f,
            widthPercent = 0.92f,
            heightPercent = 0.07f,
            titleText = "TITAN RIGS • APEX PERFORMANCE FUEL • PRO GAMING",
            subtitleText = "USE CODE: MVP10 FOR 15% OFF",
            accentColorHex = "#FFFFB700",
            bannerBgColorHex = "#E60A101C",
            zIndex = 11
        ),
        CompositionElement(
            id = "default_watermark_png",
            name = "MVP Team Crest / Logo",
            type = CompositionElementType.PNG,
            isVisible = true,
            xPercent = 0.90f,
            yPercent = 0.12f,
            widthPercent = 0.14f,
            heightPercent = 0.14f,
            titleText = "MVP ESPORTS",
            opacity = 0.85f,
            zIndex = 12
        ),
        CompositionElement(
            id = "default_meme_stinger",
            name = "GG WP Stinger Meme",
            type = CompositionElementType.MEME,
            isVisible = false,
            xPercent = 0.5f,
            yPercent = 0.5f,
            widthPercent = 0.35f,
            heightPercent = 0.25f,
            titleText = "GG WELL PLAYED!",
            subtitleText = "VICTORY ROYALE",
            accentColorHex = "#FF39FF14",
            zIndex = 15
        )
    ),
    val selectedLayerId: String = "GAME_VIDEO", // "GAME_VIDEO" or element ID
    val selectedElementId: String? = null,
    val canvasWidth: Int = 1920,
    val canvasHeight: Int = 1080,
    val showGridOverlay: Boolean = true,
    val videoAdjustmentConfig: VideoAdjustmentConfig = VideoAdjustmentConfig()
) {
    val isGameVideoSelected: Boolean get() = selectedLayerId == "GAME_VIDEO"
}

data class StorageConfig(
    val autoSaveToGallery: Boolean = true,
    val targetDirectory: String = "Movies/MVP_Recordings",
    val availableSpaceGb: Double = 42.8,
    val estimatedRecordMinutesRemaining: Int = 310
)

data class MvpStationUiState(
    val recordingState: RecordingState = RecordingState.IDLE,
    val recordingSeconds: Long = 0,
    val recordingConfig: RecordingConfig = RecordingConfig(),
    val audioConfig: AudioConfig = AudioConfig(),
    val overlayConfig: OverlayConfig = OverlayConfig(),
    val bannerStripConfig: BannerStripConfig = BannerStripConfig(),
    val videoAdjustmentConfig: VideoAdjustmentConfig = VideoAdjustmentConfig(),
    val pointerConfig: PointerConfig = PointerConfig(),
    val compositionConfig: CompositionConfig = CompositionConfig(),
    val storageConfig: StorageConfig = StorageConfig(),
    val floatingControlEnabled: Boolean = true,
    val lastRecordedFilePath: String? = null,
    val lastRecordedUri: String? = null,
    val lastRecordedFileSizeMb: Float = 0f,
    val isHardwareEncoderActive: Boolean = false,
    val codecHardwareName: String = "MediaCodec H.264/AVC (Hardware)",
    val configuredWidth: Int = 1920,
    val configuredHeight: Int = 1080,
    val encodedFramesCount: Long = 0,
    val activeKeyframeIntervalSeconds: Int = 2,
    val controlLayerIsolated: Boolean = true,
    val recordingErrorMessage: String? = null
)
