package com.example.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.audio.AudioMixerEngine
import com.example.engine.audio.MusicPlaybackState
import com.example.engine.capture.ScreenCaptureManager
import com.example.engine.control.ControlLayerManager
import com.example.engine.output.MediaStoreExporter
import com.example.engine.output.OutputCompositionPipeline
import com.example.engine.service.ScreenCaptureService
import com.example.model.AudioConfig
import com.example.model.BannerStripConfig
import com.example.model.ColorLutPreset
import com.example.model.FacecamShape
import com.example.model.MvpStationUiState
import com.example.model.OverlayConfig
import com.example.model.PointerConfig
import com.example.model.PointerStyle
import com.example.model.RecordingConfig
import com.example.model.RecordingState
import com.example.model.StorageConfig
import com.example.model.VideoAdjustmentConfig
import com.example.model.VideoCodec
import com.example.model.VideoFps
import com.example.model.VideoOrientation
import com.example.model.VideoResolution
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MvpStationViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MvpStationViewModel"

    private val _uiState = MutableStateFlow(MvpStationUiState())
    val uiState: StateFlow<MvpStationUiState> = _uiState.asStateFlow()

    private val screenCaptureManager = ScreenCaptureManager(application.applicationContext)
    val controlLayerManager = ControlLayerManager()

    // Real-time PCM Audio Mixer Engine
    val audioMixer = AudioMixerEngine(sampleRate = 44100, channelCount = 2)

    private var activePipeline: OutputCompositionPipeline? = null
    private var timerJob: Job? = null
    private var currentOutputFile: File? = null

    init {
        screenCaptureManager.setCallback(object : ScreenCaptureManager.CaptureCallback {
            override fun onCaptureStarted() {
                Log.i(TAG, "Screen capture successfully started.")
            }

            override fun onCaptureStopped() {
                Log.w(TAG, "Screen capture was stopped externally.")
                if (_uiState.value.recordingState == RecordingState.RECORDING) {
                    stopRecording()
                }
            }

            override fun onCaptureError(message: String) {
                Log.e(TAG, "Capture error: $message")
                _uiState.update { it.copy(recordingErrorMessage = message) }
                stopRecording()
            }
        })

        // Observe real-time AudioMixerEngine state & VU telemetry
        viewModelScope.launch {
            audioMixer.mixerState.collect { mixerState ->
                _uiState.update { current ->
                    current.copy(
                        audioConfig = current.audioConfig.copy(
                            masterVolume = mixerState.masterVolume,
                            isMasterMuted = mixerState.isMasterMuted,
                            internalAudioEnabled = mixerState.internalAudio.isEnabled,
                            internalAudioMuted = mixerState.internalAudio.isMuted,
                            internalAudioVolume = mixerState.internalAudio.volume,
                            internalPeakLevel = mixerState.internalAudio.peakLevel,
                            micEnabled = mixerState.microphone.isEnabled,
                            micMuted = mixerState.microphone.isMuted,
                            micVolume = mixerState.microphone.volume,
                            micPeakLevel = mixerState.microphone.peakLevel,
                            noiseSuppression = mixerState.microphone.noiseSuppression,
                            echoCancellation = mixerState.microphone.echoCancellation,
                            voiceClarityBoost = mixerState.microphone.voiceClarity,
                            isNoiseSuppressorActive = mixerState.microphone.isNoiseSuppressorActive,
                            isAcousticEchoCancelerActive = mixerState.microphone.isAcousticEchoCancelerActive,
                            isAutomaticGainControlActive = mixerState.microphone.isAutomaticGainControlActive,
                            musicEnabled = mixerState.music.isEnabled,
                            musicMuted = mixerState.music.isMuted,
                            musicVolume = mixerState.music.volume,
                            musicLooping = mixerState.music.isLooping,
                            musicPlaybackState = mixerState.music.playbackState,
                            musicTrackTitle = mixerState.music.trackTitle,
                            musicTrackArtist = mixerState.music.trackArtist,
                            musicTrackUri = mixerState.music.trackUri?.toString(),
                            musicDurationMs = mixerState.music.durationMs,
                            musicCurrentPositionMs = mixerState.music.currentPositionMs,
                            musicPeakLevel = mixerState.music.peakLevel,
                            masterPeakLevel = mixerState.masterPeakLevel
                        )
                    )
                }
            }
        }
    }

    /**
     * Initiates real hardware screen capture using Android MediaProjection.
     */
    fun startNativeCapture(
        resultCode: Int,
        intentData: Intent,
        screenWidth: Int = 1080,
        screenHeight: Int = 2400,
        densityDpi: Int = 420
    ) {
        if (_uiState.value.recordingState == RecordingState.RECORDING) return

        if (resultCode != Activity.RESULT_OK) {
            _uiState.update { it.copy(recordingErrorMessage = "Screen capture permission was declined.") }
            return
        }

        val context = getApplication<Application>().applicationContext
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager == null) {
            _uiState.update { it.copy(recordingErrorMessage = "MediaProjection service unavailable.") }
            return
        }

        val mediaProjection: MediaProjection? = try {
            ScreenCaptureService.startService(context)
            projectionManager.getMediaProjection(resultCode, intentData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MediaProjection: ${e.message}", e)
            _uiState.update { it.copy(recordingErrorMessage = "Failed to obtain MediaProjection: ${e.message}") }
            return
        }

        if (mediaProjection == null) {
            _uiState.update { it.copy(recordingErrorMessage = "Unable to create MediaProjection token.") }
            return
        }

        _uiState.update { it.copy(recordingState = RecordingState.PREPARING, recordingErrorMessage = null) }

        viewModelScope.launch {
            try {
                // Setup output MP4 file
                val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
                val recDir = File(moviesDir, _uiState.value.storageConfig.targetDirectory).apply { mkdirs() }
                val outputFile = File(recDir, "MVP_Rec_${System.currentTimeMillis()}.mp4")
                currentOutputFile = outputFile

                val currentState = _uiState.value
                val pipeline = OutputCompositionPipeline(
                    recordingConfig = currentState.recordingConfig,
                    overlayConfig = currentState.overlayConfig,
                    bannerConfig = currentState.bannerStripConfig,
                    videoAdjustmentConfig = currentState.videoAdjustmentConfig,
                    deviceScreenWidth = screenWidth,
                    deviceScreenHeight = screenHeight,
                    context = context,
                    initialCompositionConfig = currentState.compositionConfig
                )

                pipeline.setListener(object : OutputCompositionPipeline.PipelineListener {
                    override fun onPipelineStarted(width: Int, height: Int, codecName: String, isHardware: Boolean) {
                        _uiState.update {
                            it.copy(
                                isHardwareEncoderActive = true,
                                codecHardwareName = codecName,
                                configuredWidth = width,
                                configuredHeight = height,
                                activeKeyframeIntervalSeconds = 2,
                                controlLayerIsolated = true
                            )
                        }
                    }

                    override fun onFrameEncoded(frameIndex: Long, isKeyFrame: Boolean) {
                        if (frameIndex % 30 == 0L) {
                            _uiState.update { it.copy(encodedFramesCount = frameIndex) }
                        }
                    }

                    override fun onPipelineStopped(outputFilePath: String?, totalFrames: Long) {
                        _uiState.update {
                            it.copy(
                                encodedFramesCount = totalFrames,
                                lastRecordedFilePath = outputFilePath
                            )
                        }
                    }

                    override fun onPipelineError(error: String) {
                        _uiState.update { it.copy(recordingErrorMessage = error) }
                    }
                })

                val encoderSurface = pipeline.startPipeline(
                    outputFile = outputFile,
                    audioMixer = audioMixer,
                    mediaProjection = mediaProjection
                )
                activePipeline = pipeline

                val captureSuccess = screenCaptureManager.startCapture(
                    projection = mediaProjection,
                    targetSurface = encoderSurface,
                    width = pipeline.outputDimensions.width,
                    height = pipeline.outputDimensions.height,
                    densityDpi = densityDpi
                )

                if (!captureSuccess) {
                    throw IllegalStateException("Failed to attach VirtualDisplay to hardware encoder surface.")
                }

                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.RECORDING,
                        recordingSeconds = 0
                    )
                }
                startTimer()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start native capture pipeline: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.IDLE,
                        recordingErrorMessage = "Capture Error: ${e.message}"
                    )
                }
                stopRecording()
            }
        }
    }

    /**
     * Cleanly stops the hardware video encoder, releases VirtualDisplay and MediaProjection,
     * and finalizes the MP4 recording with Android MediaStore gallery export.
     */
    fun stopRecording() {
        val currentState = _uiState.value
        if (currentState.recordingState != RecordingState.RECORDING && currentState.recordingState != RecordingState.PAUSED) return

        timerJob?.cancel()
        _uiState.update { it.copy(recordingState = RecordingState.SAVING) }

        viewModelScope.launch {
            try {
                screenCaptureManager.stopCapture()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping capture: ${e.message}")
            }

            val recordedFile = currentOutputFile
            val pipeline = activePipeline
            val currentWidth = pipeline?.outputDimensions?.width ?: currentState.configuredWidth
            val currentHeight = pipeline?.outputDimensions?.height ?: currentState.configuredHeight
            val durationSeconds = currentState.recordingSeconds

            try {
                activePipeline?.stopPipeline()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping pipeline: ${e.message}")
            } finally {
                activePipeline = null
            }

            val context = getApplication<Application>().applicationContext
            try {
                ScreenCaptureService.stopService(context)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping service: ${e.message}")
            }

            var finalDisplayPath = recordedFile?.absolutePath
            var finalUriString: String? = null
            var finalSizeMb = 0f

            // Export to MediaStore Gallery if enabled and file exists
            if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                if (currentState.storageConfig.autoSaveToGallery) {
                    val exportResult = MediaStoreExporter.saveToGallery(
                        context = context,
                        sourceFile = recordedFile,
                        width = currentWidth,
                        height = currentHeight,
                        durationSeconds = durationSeconds,
                        targetDirectory = currentState.storageConfig.targetDirectory
                    )
                    if (exportResult.success) {
                        finalDisplayPath = exportResult.publicPath
                        finalUriString = exportResult.uri?.toString()
                        finalSizeMb = exportResult.sizeMb
                    } else {
                        finalSizeMb = recordedFile.length() / (1024f * 1024f)
                        Log.w(TAG, "Fallback to app storage path: ${exportResult.errorMessage}")
                    }
                } else {
                    finalSizeMb = recordedFile.length() / (1024f * 1024f)
                }
            }

            _uiState.update {
                it.copy(
                    recordingState = RecordingState.IDLE,
                    recordingSeconds = 0,
                    isHardwareEncoderActive = false,
                    lastRecordedFilePath = finalDisplayPath ?: it.lastRecordedFilePath,
                    lastRecordedUri = finalUriString ?: it.lastRecordedUri,
                    lastRecordedFileSizeMb = finalSizeMb
                )
            }
        }
    }

    fun pauseRecording() {
        if (_uiState.value.recordingState != RecordingState.RECORDING) return
        timerJob?.cancel()
        activePipeline?.pausePipeline()
        _uiState.update { it.copy(recordingState = RecordingState.PAUSED) }
    }

    fun resumeRecording() {
        if (_uiState.value.recordingState != RecordingState.PAUSED) return
        activePipeline?.resumePipeline()
        _uiState.update { it.copy(recordingState = RecordingState.RECORDING) }
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
            }
        }
    }

    fun clearRecordingError() {
        _uiState.update { it.copy(recordingErrorMessage = null) }
    }

    // Video Configuration
    fun setResolution(resolution: VideoResolution) {
        _uiState.update {
            it.copy(
                recordingConfig = it.recordingConfig.copy(
                    resolution = resolution,
                    bitrateMbps = resolution.defaultBitrateMbps
                )
            )
        }
    }

    fun setFps(fps: VideoFps) {
        _uiState.update { it.copy(recordingConfig = it.recordingConfig.copy(fps = fps)) }
    }

    fun setBitrate(bitrateMbps: Int) {
        _uiState.update { it.copy(recordingConfig = it.recordingConfig.copy(bitrateMbps = bitrateMbps)) }
    }

    fun setCodec(codec: VideoCodec) {
        _uiState.update { it.copy(recordingConfig = it.recordingConfig.copy(codec = codec)) }
    }

    fun setOrientation(orientation: VideoOrientation) {
        _uiState.update { it.copy(recordingConfig = it.recordingConfig.copy(orientation = orientation)) }
    }

    // --- Audio Mixer Controls ---

    // Master Controls
    fun setMasterVolume(volume: Float) {
        audioMixer.setMasterVol(volume)
    }

    fun toggleMasterMute() {
        audioMixer.toggleMasterMute()
    }

    // Internal Audio Controls
    fun toggleInternalAudio() {
        audioMixer.toggleInternalAudio()
    }

    fun toggleInternalAudioMute() {
        audioMixer.toggleInternalAudioMute()
    }

    fun setInternalAudioVolume(volume: Float) {
        audioMixer.setInternalAudioVolume(volume)
    }

    // Microphone Controls
    fun toggleMic() {
        audioMixer.toggleMicrophone()
    }

    fun toggleMicMute() {
        audioMixer.toggleMicrophoneMute()
    }

    fun setMicVolume(volume: Float) {
        audioMixer.setMicrophoneVolume(volume)
    }

    fun toggleNoiseSuppression() {
        audioMixer.toggleNoiseSuppression()
    }

    fun toggleEchoCancellation() {
        audioMixer.toggleEchoCancellation()
    }

    fun toggleVoiceClarity() {
        audioMixer.toggleVoiceClarity()
    }

    // Music Player Controls
    fun selectMusicTrack(context: Context, uri: Uri) {
        audioMixer.loadMusicTrack(context, uri)
    }

    fun playMusic() {
        audioMixer.playMusic()
    }

    fun pauseMusic() {
        audioMixer.pauseMusic()
    }

    fun stopMusic() {
        audioMixer.stopMusic()
    }

    fun toggleMusicPlayPause() {
        audioMixer.toggleMusicPlayPause()
    }

    fun toggleMusicLoop() {
        audioMixer.toggleMusicLoop()
    }

    fun toggleMusicMute() {
        audioMixer.toggleMusicMute()
    }

    fun toggleMusic() {
        audioMixer.toggleMusicEnabled()
    }

    fun setMusicVolume(volume: Float) {
        audioMixer.setMusicVolume(volume)
    }

    fun toggleAudioDucking() {
        audioMixer.enableDucking = !audioMixer.enableDucking
        _uiState.update { it.copy(audioConfig = it.audioConfig.copy(audioDucking = audioMixer.enableDucking)) }
    }

    fun setDuckingStrength(strength: Float) {
        audioMixer.duckingStrength = strength
        _uiState.update { it.copy(audioConfig = it.audioConfig.copy(duckingStrength = strength)) }
    }

    // Overlay Configuration
    fun toggleFacecam() {
        _uiState.update {
            it.copy(overlayConfig = it.overlayConfig.copy(facecamEnabled = !it.overlayConfig.facecamEnabled))
        }
    }

    fun setFacecamShape(shape: FacecamShape) {
        _uiState.update { it.copy(overlayConfig = it.overlayConfig.copy(facecamShape = shape)) }
    }

    fun toggleFacecamBorderNeon() {
        _uiState.update {
            it.copy(overlayConfig = it.overlayConfig.copy(facecamBorderNeon = !it.overlayConfig.facecamBorderNeon))
        }
    }

    fun setFacecamOpacity(opacity: Float) {
        _uiState.update { it.copy(overlayConfig = it.overlayConfig.copy(facecamOpacity = opacity)) }
    }

    fun toggleWatermark() {
        _uiState.update {
            it.copy(overlayConfig = it.overlayConfig.copy(watermarkEnabled = !it.overlayConfig.watermarkEnabled))
        }
    }

    fun setWatermarkText(text: String) {
        _uiState.update { it.copy(overlayConfig = it.overlayConfig.copy(watermarkText = text)) }
    }

    fun toggleTeamLogo() {
        _uiState.update {
            it.copy(overlayConfig = it.overlayConfig.copy(teamLogoEnabled = !it.overlayConfig.teamLogoEnabled))
        }
    }

    fun toggleMemeStingers() {
        _uiState.update {
            it.copy(overlayConfig = it.overlayConfig.copy(memeStingersEnabled = !it.overlayConfig.memeStingersEnabled))
        }
    }

    // Banner Strip Configuration
    fun toggleBannerStrip() {
        _uiState.update {
            it.copy(bannerStripConfig = it.bannerStripConfig.copy(stripEnabled = !it.bannerStripConfig.stripEnabled))
        }
    }

    fun setTickerText(text: String) {
        _uiState.update { it.copy(bannerStripConfig = it.bannerStripConfig.copy(tickerText = text)) }
    }

    fun setSocialHandle(handle: String) {
        _uiState.update { it.copy(bannerStripConfig = it.bannerStripConfig.copy(socialHandle = handle)) }
    }

    fun setSponsorName(name: String) {
        _uiState.update { it.copy(bannerStripConfig = it.bannerStripConfig.copy(sponsorName = name)) }
    }

    fun toggleAnimateTicker() {
        _uiState.update {
            it.copy(bannerStripConfig = it.bannerStripConfig.copy(animateTicker = !it.bannerStripConfig.animateTicker))
        }
    }

    // Video Adjustment & GPU Color Enhancements
    fun setBrightness(value: Float) {
        updateVideoAdjustment { it.copy(brightness = value.coerceIn(VideoAdjustmentConfig.MIN_BRIGHTNESS, VideoAdjustmentConfig.MAX_BRIGHTNESS)) }
    }

    fun setContrast(value: Float) {
        updateVideoAdjustment { it.copy(contrast = value.coerceIn(VideoAdjustmentConfig.MIN_CONTRAST, VideoAdjustmentConfig.MAX_CONTRAST)) }
    }

    fun setSaturation(value: Float) {
        updateVideoAdjustment { it.copy(saturation = value.coerceIn(VideoAdjustmentConfig.MIN_SATURATION, VideoAdjustmentConfig.MAX_SATURATION)) }
    }

    fun toggleBrightness() {
        updateVideoAdjustment { it.copy(brightnessEnabled = !it.brightnessEnabled) }
    }

    fun toggleContrast() {
        updateVideoAdjustment { it.copy(contrastEnabled = !it.contrastEnabled) }
    }

    fun toggleSaturation() {
        updateVideoAdjustment { it.copy(saturationEnabled = !it.saturationEnabled) }
    }

    fun toggleColorEnhancementMaster() {
        updateVideoAdjustment { it.copy(isEnabled = !it.isEnabled) }
    }

    fun resetBrightness() {
        updateVideoAdjustment { it.copy(brightness = VideoAdjustmentConfig.DEFAULT_BRIGHTNESS, brightnessEnabled = true) }
    }

    fun resetContrast() {
        updateVideoAdjustment { it.copy(contrast = VideoAdjustmentConfig.DEFAULT_CONTRAST, contrastEnabled = true) }
    }

    fun resetSaturation() {
        updateVideoAdjustment { it.copy(saturation = VideoAdjustmentConfig.DEFAULT_SATURATION, saturationEnabled = true) }
    }

    fun resetAllColorEnhancements() {
        updateVideoAdjustment {
            it.copy(
                isEnabled = true,
                brightnessEnabled = true,
                brightness = VideoAdjustmentConfig.DEFAULT_BRIGHTNESS,
                contrastEnabled = true,
                contrast = VideoAdjustmentConfig.DEFAULT_CONTRAST,
                saturationEnabled = true,
                saturation = VideoAdjustmentConfig.DEFAULT_SATURATION,
                sharpness = 0.0f,
                colorLutPreset = ColorLutPreset.NATURAL
            )
        }
    }

    fun setSharpness(value: Float) {
        updateVideoAdjustment { it.copy(sharpness = value) }
    }

    fun setColorLutPreset(preset: ColorLutPreset) {
        updateVideoAdjustment { it.copy(colorLutPreset = preset) }
    }

    private fun updateVideoAdjustment(transform: (VideoAdjustmentConfig) -> VideoAdjustmentConfig) {
        _uiState.update { current ->
            val updatedAdjust = transform(current.videoAdjustmentConfig)
            val updatedComp = current.compositionConfig.copy(videoAdjustmentConfig = updatedAdjust)
            activePipeline?.updateVideoAdjustmentConfig(updatedAdjust)
            activePipeline?.updateCompositionConfig(updatedComp)
            current.copy(
                videoAdjustmentConfig = updatedAdjust,
                compositionConfig = updatedComp
            )
        }
    }

    // Pointer Controls
    fun togglePointer() {
        _uiState.update {
            it.copy(pointerConfig = it.pointerConfig.copy(showTouches = !it.pointerConfig.showTouches))
        }
    }

    fun setPointerStyle(style: PointerStyle) {
        _uiState.update { it.copy(pointerConfig = it.pointerConfig.copy(pointerStyle = style)) }
    }

    fun setPointerSize(sizeDp: Int) {
        _uiState.update { it.copy(pointerConfig = it.pointerConfig.copy(pointerSizeDp = sizeDp)) }
    }

    // Storage & Floating Controls
    fun toggleAutoSave() {
        _uiState.update {
            it.copy(storageConfig = it.storageConfig.copy(autoSaveToGallery = !it.storageConfig.autoSaveToGallery))
        }
    }

    fun setTargetDirectory(dir: String) {
        _uiState.update { it.copy(storageConfig = it.storageConfig.copy(targetDirectory = dir)) }
    }

    fun toggleFloatingControl() {
        val newState = !_uiState.value.floatingControlEnabled
        _uiState.update { it.copy(floatingControlEnabled = newState) }
        val context = getApplication<Application>().applicationContext
        if (newState) {
            try {
                com.example.engine.service.FloatingControlService.startService(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start FloatingControlService overlay: ${e.message}")
            }
        } else {
            try {
                com.example.engine.service.FloatingControlService.stopService(context)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop FloatingControlService overlay: ${e.message}")
            }
        }
    }

    // ==========================================
    // REAL OUTPUT COMPOSITION ENGINE CONTROLS
    // ==========================================

    fun selectCompositionLayer(layerId: String) {
        _uiState.update { current ->
            val updatedElementId = if (layerId == "GAME_VIDEO") null else layerId
            current.copy(
                compositionConfig = current.compositionConfig.copy(
                    selectedLayerId = layerId,
                    selectedElementId = updatedElementId
                )
            )
        }
    }

    fun selectCompositionElement(elementId: String?) {
        _uiState.update { current ->
            val layerId = elementId ?: "GAME_VIDEO"
            current.copy(
                compositionConfig = current.compositionConfig.copy(
                    selectedLayerId = layerId,
                    selectedElementId = elementId
                )
            )
        }
    }

    // --- Game Video Positioning, Sizing, Zoom, Crop & Scale Methods ---

    fun setGameScaleMode(mode: com.example.model.GameScaleMode) {
        updateGameVideo { it.copy(scaleMode = mode) }
    }

    fun updateGamePosition(xPercent: Float, yPercent: Float) {
        updateGameVideo {
            it.copy(
                xPercent = xPercent.coerceIn(0.0f, 1.0f),
                yPercent = yPercent.coerceIn(0.0f, 1.0f)
            )
        }
    }

    fun moveGame(deltaX: Float, deltaY: Float) {
        updateGameVideo {
            it.copy(
                xPercent = (it.xPercent + deltaX).coerceIn(0.0f, 1.0f),
                yPercent = (it.yPercent + deltaY).coerceIn(0.0f, 1.0f)
            )
        }
    }

    fun updateGameSize(widthPercent: Float, heightPercent: Float) {
        updateGameVideo {
            it.copy(
                widthPercent = widthPercent.coerceIn(0.1f, 2.5f),
                heightPercent = heightPercent.coerceIn(0.1f, 2.5f),
                scaleMode = com.example.model.GameScaleMode.CUSTOM
            )
        }
    }

    fun updateGameScale(scale: Float) {
        updateGameVideo {
            it.copy(scale = scale.coerceIn(0.2f, 3.0f))
        }
    }

    fun updateGameCrop(cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float) {
        updateGameVideo {
            it.copy(
                cropLeft = cropLeft.coerceIn(0.0f, 0.45f),
                cropTop = cropTop.coerceIn(0.0f, 0.45f),
                cropRight = cropRight.coerceIn(0.0f, 0.45f),
                cropBottom = cropBottom.coerceIn(0.0f, 0.45f)
            )
        }
    }

    fun updateGameRotation(rotationDeg: Float) {
        updateGameVideo {
            it.copy(rotationDeg = rotationDeg.coerceIn(-180f, 180f))
        }
    }

    fun updateGameOpacity(opacity: Float) {
        updateGameVideo {
            it.copy(opacity = opacity.coerceIn(0.05f, 1.0f))
        }
    }

    fun toggleGameVisibility() {
        updateGameVideo { it.copy(isVisible = !it.isVisible) }
    }

    fun resetGameTransform() {
        updateGameVideo {
            com.example.model.GameVideoConfig(
                scaleMode = com.example.model.GameScaleMode.FIT,
                xPercent = 0.5f,
                yPercent = 0.5f,
                widthPercent = 1.0f,
                heightPercent = 1.0f,
                scale = 1.0f,
                cropLeft = 0.0f,
                cropTop = 0.0f,
                cropRight = 0.0f,
                cropBottom = 0.0f,
                rotationDeg = 0.0f,
                opacity = 1.0f,
                isVisible = true,
                backgroundColorHex = it.backgroundColorHex
            )
        }
    }

    fun setGameFitPreset() {
        updateGameVideo {
            it.copy(
                scaleMode = com.example.model.GameScaleMode.FIT,
                xPercent = 0.5f,
                yPercent = 0.5f,
                scale = 1.0f,
                cropLeft = 0.0f,
                cropTop = 0.0f,
                cropRight = 0.0f,
                cropBottom = 0.0f,
                rotationDeg = 0.0f
            )
        }
    }

    fun setGameFillPreset() {
        updateGameVideo {
            it.copy(
                scaleMode = com.example.model.GameScaleMode.FILL,
                xPercent = 0.5f,
                yPercent = 0.5f,
                scale = 1.0f,
                cropLeft = 0.0f,
                cropTop = 0.0f,
                cropRight = 0.0f,
                cropBottom = 0.0f,
                rotationDeg = 0.0f
            )
        }
    }

    fun setGameFullscreenPreset() {
        updateGameVideo {
            it.copy(
                scaleMode = com.example.model.GameScaleMode.FULLSCREEN,
                xPercent = 0.5f,
                yPercent = 0.5f,
                widthPercent = 1.0f,
                heightPercent = 1.0f,
                scale = 1.0f,
                cropLeft = 0.0f,
                cropTop = 0.0f,
                cropRight = 0.0f,
                cropBottom = 0.0f,
                rotationDeg = 0.0f
            )
        }
    }

    fun setGameBackgroundColor(hex: String) {
        updateGameVideo { it.copy(backgroundColorHex = hex) }
    }

    fun toggleGridOverlay() {
        _uiState.update { current ->
            current.copy(
                compositionConfig = current.compositionConfig.copy(
                    showGridOverlay = !current.compositionConfig.showGridOverlay
                )
            )
        }
    }

    private fun updateGameVideo(transform: (com.example.model.GameVideoConfig) -> com.example.model.GameVideoConfig) {
        _uiState.update { current ->
            val updatedGame = transform(current.compositionConfig.gameVideoConfig)
            val updatedConfig = current.compositionConfig.copy(gameVideoConfig = updatedGame)
            activePipeline?.updateCompositionConfig(updatedConfig)
            current.copy(compositionConfig = updatedConfig)
        }
    }

    fun addCompositionElement(
        type: com.example.model.CompositionElementType,
        name: String? = null,
        uri: Uri? = null,
        title: String? = null,
        subtitle: String? = null
    ): String {
        val elementId = java.util.UUID.randomUUID().toString()
        val defaultName = name ?: when (type) {
            com.example.model.CompositionElementType.PHOTO -> "Custom Photo"
            com.example.model.CompositionElementType.PNG -> "PNG Graphic"
            com.example.model.CompositionElementType.MEME -> "Meme Stinger"
            com.example.model.CompositionElementType.VIDEO -> "Video Overlay"
            com.example.model.CompositionElementType.BANNER -> "Top Sponsor Banner"
            com.example.model.CompositionElementType.BOTTOM_STRIP -> "Bottom Strip Marquee"
            com.example.model.CompositionElementType.CUSTOM_GRAPHICS -> "Custom Frame"
        }

        val maxZ = _uiState.value.compositionConfig.elements.maxOfOrNull { it.zIndex } ?: 0

        val newElement = when (type) {
            com.example.model.CompositionElementType.BOTTOM_STRIP -> com.example.model.CompositionElement(
                id = elementId,
                name = defaultName,
                type = type,
                isVisible = true,
                xPercent = 0.5f,
                yPercent = 0.94f,
                widthPercent = 0.96f,
                heightPercent = 0.08f,
                titleText = title ?: "🔴 LIVE ESPORTS • OFFICIAL MATCH STREAM",
                subtitleText = subtitle ?: "FOLLOW & SUBSCRIBE @MVP_ESPORTS",
                accentColorHex = "#FF00F0FF",
                bannerBgColorHex = "#E60B0F19",
                contentUri = uri?.toString(),
                zIndex = maxZ + 1
            )
            com.example.model.CompositionElementType.BANNER -> com.example.model.CompositionElement(
                id = elementId,
                name = defaultName,
                type = type,
                isVisible = true,
                xPercent = 0.5f,
                yPercent = 0.06f,
                widthPercent = 0.92f,
                heightPercent = 0.07f,
                titleText = title ?: "TITAN RIGS • APEX PRO FUEL",
                subtitleText = subtitle ?: "OFFICIAL TOURNAMENT PARTNER",
                accentColorHex = "#FFFFB700",
                bannerBgColorHex = "#E60A101C",
                contentUri = uri?.toString(),
                zIndex = maxZ + 1
            )
            com.example.model.CompositionElementType.MEME -> com.example.model.CompositionElement(
                id = elementId,
                name = defaultName,
                type = type,
                isVisible = true,
                xPercent = 0.5f,
                yPercent = 0.5f,
                widthPercent = 0.35f,
                heightPercent = 0.25f,
                titleText = title ?: "GG WELL PLAYED!",
                subtitleText = subtitle ?: "VICTORY ROYALE",
                accentColorHex = "#FF39FF14",
                contentUri = uri?.toString(),
                zIndex = maxZ + 1
            )
            com.example.model.CompositionElementType.VIDEO -> com.example.model.CompositionElement(
                id = elementId,
                name = defaultName,
                type = type,
                isVisible = true,
                xPercent = 0.82f,
                yPercent = 0.22f,
                widthPercent = 0.28f,
                heightPercent = 0.22f,
                contentUri = uri?.toString(),
                loopVideo = true,
                zIndex = maxZ + 1
            )
            com.example.model.CompositionElementType.PHOTO,
            com.example.model.CompositionElementType.PNG,
            com.example.model.CompositionElementType.CUSTOM_GRAPHICS -> com.example.model.CompositionElement(
                id = elementId,
                name = defaultName,
                type = type,
                isVisible = true,
                xPercent = 0.85f,
                yPercent = 0.15f,
                widthPercent = 0.18f,
                heightPercent = 0.18f,
                contentUri = uri?.toString(),
                zIndex = maxZ + 1
            )
        }

        _uiState.update { current ->
            val updatedElements = current.compositionConfig.elements + newElement
            val updatedConfig = current.compositionConfig.copy(
                elements = updatedElements,
                selectedElementId = elementId
            )
            activePipeline?.updateCompositionConfig(updatedConfig)
            current.copy(compositionConfig = updatedConfig)
        }

        return elementId
    }

    fun removeCompositionElement(elementId: String) {
        _uiState.update { current ->
            val updatedElements = current.compositionConfig.elements.filter { it.id != elementId }
            val nextSelected = if (current.compositionConfig.selectedElementId == elementId) {
                updatedElements.firstOrNull()?.id
            } else {
                current.compositionConfig.selectedElementId
            }
            val updatedConfig = current.compositionConfig.copy(
                elements = updatedElements,
                selectedElementId = nextSelected
            )
            activePipeline?.updateCompositionConfig(updatedConfig)
            current.copy(compositionConfig = updatedConfig)
        }
    }

    fun toggleElementVisibility(elementId: String) {
        updateElement(elementId) { it.copy(isVisible = !it.isVisible) }
    }

    fun updateElementPosition(elementId: String, xPercent: Float, yPercent: Float) {
        updateElement(elementId) {
            it.copy(
                xPercent = xPercent.coerceIn(0.02f, 0.98f),
                yPercent = yPercent.coerceIn(0.02f, 0.98f)
            )
        }
    }

    fun updateElementSize(elementId: String, widthPercent: Float, heightPercent: Float) {
        updateElement(elementId) {
            it.copy(
                widthPercent = widthPercent.coerceIn(0.05f, 1.0f),
                heightPercent = heightPercent.coerceIn(0.03f, 1.0f)
            )
        }
    }

    fun updateElementCrop(
        elementId: String,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float
    ) {
        updateElement(elementId) {
            it.copy(
                cropLeft = cropLeft.coerceIn(0.0f, 0.45f),
                cropTop = cropTop.coerceIn(0.0f, 0.45f),
                cropRight = cropRight.coerceIn(0.0f, 0.45f),
                cropBottom = cropBottom.coerceIn(0.0f, 0.45f)
            )
        }
    }

    fun updateElementScale(elementId: String, scale: Float) {
        updateElement(elementId) {
            it.copy(scale = scale.coerceIn(0.2f, 3.0f))
        }
    }

    fun updateElementRotation(elementId: String, rotationDeg: Float) {
        updateElement(elementId) {
            it.copy(rotationDeg = rotationDeg.coerceIn(-180f, 180f))
        }
    }

    fun updateElementOpacity(elementId: String, opacity: Float) {
        updateElement(elementId) {
            it.copy(opacity = opacity.coerceIn(0.05f, 1.0f))
        }
    }

    fun moveElementLayerUp(elementId: String) {
        _uiState.update { current ->
            val list = current.compositionConfig.elements.toMutableList()
            val index = list.indexOfFirst { it.id == elementId }
            if (index >= 0 && index < list.size - 1) {
                val temp = list[index]
                list[index] = list[index + 1].copy(zIndex = temp.zIndex)
                list[index + 1] = temp.copy(zIndex = list[index + 1].zIndex + 1)
            }
            val updatedConfig = current.compositionConfig.copy(elements = list)
            activePipeline?.updateCompositionConfig(updatedConfig)
            current.copy(compositionConfig = updatedConfig)
        }
    }

    fun moveElementLayerDown(elementId: String) {
        _uiState.update { current ->
            val list = current.compositionConfig.elements.toMutableList()
            val index = list.indexOfFirst { it.id == elementId }
            if (index > 0) {
                val temp = list[index]
                list[index] = list[index - 1].copy(zIndex = temp.zIndex)
                list[index - 1] = temp.copy(zIndex = list[index - 1].zIndex - 1)
            }
            val updatedConfig = current.compositionConfig.copy(elements = list)
            activePipeline?.updateCompositionConfig(updatedConfig)
            current.copy(compositionConfig = updatedConfig)
        }
    }

    fun updateElementText(elementId: String, title: String?, subtitle: String?) {
        updateElement(elementId) {
            it.copy(titleText = title, subtitleText = subtitle)
        }
    }

    fun updateElementColors(elementId: String, accentHex: String, bgHex: String) {
        updateElement(elementId) {
            it.copy(accentColorHex = accentHex, bannerBgColorHex = bgHex)
        }
    }

    fun updateElementVideoLoop(elementId: String, loop: Boolean) {
        updateElement(elementId) {
            it.copy(loopVideo = loop)
        }
    }

    fun setElementContentUri(elementId: String, uri: Uri) {
        updateElement(elementId) {
            it.copy(contentUri = uri.toString())
        }
    }

    private fun updateElement(elementId: String, transform: (com.example.model.CompositionElement) -> com.example.model.CompositionElement) {
        _uiState.update { current ->
            val updatedElements = current.compositionConfig.elements.map {
                if (it.id == elementId) transform(it) else it
            }
            val updatedConfig = current.compositionConfig.copy(elements = updatedElements)
            activePipeline?.updateCompositionConfig(updatedConfig)
            current.copy(compositionConfig = updatedConfig)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording()
        audioMixer.stop()
    }
}
