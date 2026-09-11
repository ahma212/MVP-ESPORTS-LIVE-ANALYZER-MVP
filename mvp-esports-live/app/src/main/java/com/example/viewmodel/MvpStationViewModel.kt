package com.example.viewmodel

import android.app.Activity
import android.app.Application
import com.example.engine.control.FloatingControlBridge
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.audio.AudioMixerEngine
import com.example.engine.audio.MusicPlaybackState
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
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
       val controlLayerManager = ControlLayerManager()
val audioMixer = AudioMixerEngine(sampleRate = 44100, channelCount = 2)

private var activePipeline: OutputCompositionPipeline? = null
private var timerJob: Job? = null
private var currentOutputFile: File? = null

private data class PendingCaptureRequest(
    val resultCode: Int,
    val intentData: Intent,
    val screenWidth: Int,
    val screenHeight: Int,
    val densityDpi: Int
)

private var pendingCaptureRequest: PendingCaptureRequest? = null
private var captureService: ScreenCaptureService? = null
private var captureServiceBound = false

private val captureServiceConnection =
    object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?,
            serviceBinder: IBinder?
        ) {
            val binder =
                serviceBinder as? ScreenCaptureService.LocalBinder
                    ?: return

            val service = binder.getService()

            captureService = service
            captureServiceBound = true

            service.setCaptureListener(
                object : ScreenCaptureService.CaptureListener {

                    override fun onProjectionReady() {
                        beginNativeCapture(service)
                    }

                    override fun onCaptureStarted() {
                        Log.i(
                            TAG,
                            "Screen capture successfully started."
                        )
                    }

                    override fun onCaptureStopped() {
                        Log.w(
                            TAG,
                            "MediaProjection capture session stopped."
                        )

                        if (
                            _uiState.value.recordingState ==
                                RecordingState.RECORDING ||
                            _uiState.value.recordingState ==
                                RecordingState.PAUSED
                        ) {
                            _uiState.update {
                                it.copy(
                                    recordingErrorMessage =
                                        "Screen capture session was stopped."
                                )
                            }
                            stopRecording()
                        }
                    }

                   override fun onCaptureError(
    message: String
) {
    Log.e(
        TAG,
        "Capture error: $message"
    )

    val state =
        _uiState.value.recordingState

    when (state) {
        RecordingState.PREPARING -> {
            timerJob?.cancel()
            timerJob = null

            try {
                activePipeline?.stopPipeline()
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Error stopping pipeline after capture preparation failure: ${e.message}"
                )
            } finally {
                activePipeline = null
            }

            _uiState.update {
                it.copy(
                    recordingState =
                        RecordingState.IDLE,
                    recordingErrorMessage =
                        message
                )
            }
        }

        RecordingState.RECORDING,
        RecordingState.PAUSED -> {
            _uiState.update {
                it.copy(
                    recordingErrorMessage =
                        message
                )
            }

            stopRecording()
        }

        else -> {
            _uiState.update {
                it.copy(
                    recordingErrorMessage =
                        message
                )
            }
        }
    }
}
                }
            )

            if (service.isProjectionReady()) {
                beginNativeCapture(service)
            }
        }

        override fun onServiceDisconnected(
    name: ComponentName?
) {
    Log.w(
        TAG,
        "Screen capture service binder disconnected."
    )

    captureService = null
    captureServiceBound = false

    val state =
        _uiState.value.recordingState

    when (state) {
        RecordingState.RECORDING,
        RecordingState.PAUSED -> {
            _uiState.update {
                it.copy(
                    recordingErrorMessage =
                        "Screen capture service connection was lost."
                )
            }

            /*
             * Finalize the current recording instead of leaving
             * the encoders and pipeline running without capture.
             */
            stopRecording()
        }

        RecordingState.PREPARING -> {
            timerJob?.cancel()
            timerJob = null

            try {
                activePipeline?.stopPipeline()
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Error stopping pipeline after service disconnect: ${e.message}"
                )
            } finally {
                activePipeline = null
            }

            _uiState.update {
                it.copy(
                    recordingState =
                        RecordingState.IDLE,
                    recordingErrorMessage =
                        "Screen capture service connection was lost."
                )
            }
        }

        else -> {
            // No active capture session requires cleanup.
        }
    }
}
    }

init {
// Publish station state to floating pointer HUD
        viewModelScope.launch {
            uiState.collect { state ->
                FloatingControlBridge.publishStationState(state)
            }
        }

        // Register real command handlers for floating pointer
        FloatingControlBridge.registerStationHandler(
            object : FloatingControlBridge.StationHandler {
                override fun startRecording() {
                    when (_uiState.value.recordingState) {
                        RecordingState.RECORDING -> {
                            // already recording
                        }
                        RecordingState.PAUSED -> {
                            resumeRecording()
                        }
                        RecordingState.PREPARING,
                        RecordingState.SAVING -> {
                            // busy
                        }
                        RecordingState.IDLE -> {
                            // Need system MediaProjection dialog via Activity UI
                            FloatingControlBridge.requestStartRecordingPermission()
                            val context = getApplication<Application>().applicationContext
                            val intent = Intent(context, com.example.MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra("mvp_action", "start_recording")
                            }
                            context.startActivity(intent)
                        }
                    }
                }
                override fun pauseRecording() {
                    pauseRecording()
                }

                override fun resumeRecording() {
                    resumeRecording()
                }

                override fun stopRecording() {
                    stopRecording()
                }

                override fun toggleMic() {
                    toggleMic()
                }

                override fun setMicVolume(volume: Float) {
                    setMicVolume(volume)
                }

                override fun toggleInternalAudio() {
                    toggleInternalAudio()
                }

                override fun setInternalAudioVolume(volume: Float) {
                    setInternalAudioVolume(volume)
                }

                override fun toggleMusic() {
                    toggleMusic()
                }

                override fun toggleMusicPlayPause() {
                    toggleMusicPlayPause()
                }

                override fun setMusicVolume(volume: Float) {
    setMusicVolume(volume)
}

override fun toggleMusicLoop() {
    toggleMusicLoop()
}

override fun toggleOverlay() {
    toggleTeamLogo()
}

                override fun toggleBannerStrip() {
                    toggleBannerStrip()
                }

                override fun toggleFacecam() {
                    toggleFacecam()
                }

                override fun toggleWatermark() {
                    toggleWatermark()
                }

                override fun setResolution(resolution: VideoResolution) {
                    setResolution(resolution)
                    
                    override fun setFps(fps: com.example.model.VideoFps) {
                    setFps(fps)
                }

                override fun setBitrate(bitrateMbps: Int) {
                    setBitrate(bitrateMbps)
                }

                override fun setBrightness(value: Float) {
                    setBrightness(value)
                }

                override fun setContrast(value: Float) {
                    setContrast(value)
                }

                override fun setSaturation(value: Float) {
                    setSaturation(value)
                }
                    override fun clearBreakVideo() {
                    clearBreakVideo()
                }

                override fun removeSelectedOverlay() {
                    val id = _uiState.value.compositionConfig.selectedElementId
                    if (!id.isNullOrBlank()) {
                        removeCompositionElement(id)
                    }
                }
                }
            }
        )
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
fun requestMusicPickFromPointer() {
    FloatingControlBridge.requestSelectMusic()

    val context = getApplication<Application>().applicationContext

    val intent = Intent(context, com.example.MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra("mvp_action", "select_music")
    }

    context.startActivity(intent)
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
    val currentRecordingState = _uiState.value.recordingState

    if (
        currentRecordingState == RecordingState.RECORDING ||
        currentRecordingState == RecordingState.PREPARING ||
        currentRecordingState == RecordingState.SAVING
    ) {
        return
    }

    if (
        resultCode != Activity.RESULT_OK ||
        intentData.extras == null
    ) {
        _uiState.update {
            it.copy(
                recordingState = RecordingState.IDLE,
                recordingErrorMessage =
                    "Screen capture permission was declined."
            )
        }
        return
    }

    val context =
        getApplication<Application>().applicationContext

    pendingCaptureRequest =
        PendingCaptureRequest(
            resultCode = resultCode,
            intentData = Intent(intentData),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            densityDpi = densityDpi
        )

    _uiState.update {
        it.copy(
            recordingState = RecordingState.PREPARING,
            recordingErrorMessage = null
        )
    }

    try {
        ScreenCaptureService.startService(
            context = context,
            resultCode = resultCode,
            resultData = Intent(intentData)
        )

        val serviceIntent =
            Intent(
                context,
                ScreenCaptureService::class.java
            )

        val bound =
            context.bindService(
                serviceIntent,
                captureServiceConnection,
                Context.BIND_AUTO_CREATE
            )

        if (!bound) {
            pendingCaptureRequest = null

            _uiState.update {
                it.copy(
                    recordingState = RecordingState.IDLE,
                    recordingErrorMessage =
                        "Unable to connect to the screen capture service."
                )
            }

            ScreenCaptureService.stopService(context)
        }

    } catch (e: Exception) {
        pendingCaptureRequest = null

        Log.e(
            TAG,
            "Failed to start ScreenCaptureService: ${e.message}",
            e
        )

        _uiState.update {
            it.copy(
                recordingState = RecordingState.IDLE,
                recordingErrorMessage =
                    "Failed to start screen capture: ${
                        e.localizedMessage ?: "Unknown error"
                    }"
            )
        }

        ScreenCaptureService.stopService(context)
    }
}

private fun beginNativeCapture(
    service: ScreenCaptureService
) {
    val request = pendingCaptureRequest
        ?: return

    if (activePipeline != null) {
        return
    }

    pendingCaptureRequest = null

    val context =
        getApplication<Application>().applicationContext

    if (!service.isProjectionReady()) {
        _uiState.update {
            it.copy(
                recordingState = RecordingState.IDLE,
                recordingErrorMessage =
                    "MediaProjection is no longer available."
            )
        }

        try {
            service.setCaptureListener(null)
        } catch (_: Exception) {
        }

        if (captureServiceBound) {
            try {
                context.unbindService(
                    captureServiceConnection
                )
            } catch (_: Exception) {
            }

            captureServiceBound = false
        }

        captureService = null
        ScreenCaptureService.stopService(context)
        return
    }

    viewModelScope.launch {
        try {
            val mediaProjection =
                service.getMediaProjection()
                    ?: throw IllegalStateException(
                        "MediaProjection became unavailable."
                    )

            val moviesDir =
                context.getExternalFilesDir(
                    Environment.DIRECTORY_MOVIES
                ) ?: context.filesDir

            val recDir =
                File(
                    moviesDir,
                    _uiState.value.storageConfig.targetDirectory
                ).apply {
                    mkdirs()
                }

            val outputFile =
                File(
                    recDir,
                    "MVP_Rec_${System.currentTimeMillis()}.mp4"
                )

            currentOutputFile = outputFile

            val currentState = _uiState.value

            val pipeline =
                OutputCompositionPipeline(
                    recordingConfig =
                        currentState.recordingConfig,
                    overlayConfig =
                        currentState.overlayConfig,
                    bannerConfig =
                        currentState.bannerStripConfig,
                    videoAdjustmentConfig =
                        currentState.videoAdjustmentConfig,
                    deviceScreenWidth =
                        request.screenWidth,
                    deviceScreenHeight =
                        request.screenHeight,
                    context = context,
                    initialCompositionConfig =
                        currentState.compositionConfig
                )

            pipeline.setListener(
                object :
                    OutputCompositionPipeline.PipelineListener {

                    override fun onPipelineStarted(
                        width: Int,
                        height: Int,
                        codecName: String,
                        isHardware: Boolean
                    ) {
                        _uiState.update {
                            it.copy(
                                isHardwareEncoderActive =
                                    isHardware,
                                codecHardwareName =
                                    codecName,
                                configuredWidth =
                                    width,
                                configuredHeight =
                                    height,
                                activeKeyframeIntervalSeconds =
                                    2,
                                controlLayerIsolated =
                                    true
                            )
                        }
                    }

                    override fun onFrameEncoded(
                        frameIndex: Long,
                        isKeyFrame: Boolean
                    ) {
                        if (frameIndex % 30L == 0L) {
                            _uiState.update {
                                it.copy(
                                    encodedFramesCount =
                                        frameIndex
                                )
                            }
                        }
                    }

                    override fun onPipelineStopped(
                        outputFilePath: String?,
                        totalFrames: Long
                    ) {
                        _uiState.update {
                            it.copy(
                                encodedFramesCount =
                                    totalFrames,
                                lastRecordedFilePath =
                                    outputFilePath
                            )
                        }
                    }

                    override fun onPipelineError(
                        error: String
                    ) {
                        _uiState.update {
                            it.copy(
                                recordingErrorMessage =
                                    error
                            )
                        }
                    }
                }
            )

            val encoderSurface =
                pipeline.startPipeline(
                    outputFile = outputFile,
                    audioMixer = audioMixer,
                    mediaProjection = mediaProjection
                )

            activePipeline = pipeline

            val captureSuccess =
                service.startCapture(
                    targetSurface = encoderSurface,
                    width =
                        pipeline.outputDimensions.width,
                    height =
                        pipeline.outputDimensions.height,
                    densityDpi =
                        request.densityDpi
                )

            if (!captureSuccess) {
                throw IllegalStateException(
                    "Failed to create the MediaProjection VirtualDisplay."
                )
            }

            _uiState.update {
                it.copy(
                    recordingState =
                        RecordingState.RECORDING,
                    recordingSeconds = 0
                )
            }

            startTimer()

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to start native capture pipeline: ${e.message}",
                e
            )

            try {
                service.stopCapture()
            } catch (cleanupError: Exception) {
                Log.w(
                    TAG,
                    "Capture cleanup failed: ${cleanupError.message}"
                )
            }

            try {
                activePipeline?.stopPipeline()
            } catch (cleanupError: Exception) {
                Log.w(
                    TAG,
                    "Pipeline cleanup failed: ${cleanupError.message}"
                )
            }

            activePipeline = null
            pendingCaptureRequest = null

            try {
                service.setCaptureListener(null)
            } catch (_: Exception) {
            }

            if (captureServiceBound) {
                try {
                    context.unbindService(
                        captureServiceConnection
                    )
                } catch (_: Exception) {
                }

                captureServiceBound = false
            }

            captureService = null

            try {
                ScreenCaptureService.stopService(context)
            } catch (cleanupError: Exception) {
                Log.w(
                    TAG,
                    "Service cleanup failed: ${cleanupError.message}"
                )
            }

            _uiState.update {
                it.copy(
                    recordingState =
                        RecordingState.IDLE,
                    recordingErrorMessage =
                        "Capture Error: ${
                            e.localizedMessage
                                ?: "Unknown error"
                        }"
                )
            }
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
            val service = captureService

try {
    service?.stopCapture()
} catch (e: Exception) {
    Log.w(
        TAG,
        "Error stopping capture service: ${e.message}"
    )
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
    service?.setCaptureListener(null)
} catch (_: Exception) {
}

if (captureServiceBound) {
    try {
        context.unbindService(
            captureServiceConnection
        )
    } catch (_: Exception) {
    }

    captureServiceBound = false
}

captureService = null

try {
    ScreenCaptureService.stopService(context)
} catch (e: Exception) {
    Log.w(
        TAG,
        "Error stopping service: ${e.message}"
    )
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
     fun setRecordingError(message: String) {
    _uiState.update {
        it.copy(
            recordingErrorMessage = message
        )
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
/**
     * Full-screen break video for live (covers game). Gallery URI se.
     */
    fun setBreakVideo(uri: Uri) {
        // Remove previous break layer if any
        val existingBreakIds = _uiState.value.compositionConfig.elements
            .filter { it.name == "BREAK_VIDEO_FULL" }
            .map { it.id }
        existingBreakIds.forEach { removeCompositionElement(it) }

        val elementId = java.util.UUID.randomUUID().toString()
        val newElement = com.example.model.CompositionElement(
            id = elementId,
            name = "BREAK_VIDEO_FULL",
            type = com.example.model.CompositionElementType.VIDEO,
            isVisible = true,
            xPercent = 0.5f,
            yPercent = 0.5f,
            widthPercent = 1.0f,
            heightPercent = 1.0f,
            contentUri = uri.toString(),
            loopVideo = true,
            isVideoPlaying = true,
            zIndex = 999
        )

        _uiState.update { current ->
            val updatedElements = current.compositionConfig.elements + newElement
            val updatedGame = current.compositionConfig.gameVideoConfig.copy(isVisible = false)
            val updatedConfig = current.compositionConfig.copy(
                elements = updatedElements,
                selectedElementId = elementId,
                gameVideoConfig = updatedGame
            )
            activePipeline?.updateCompositionConfig(updatedConfig)
            current.copy(compositionConfig = updatedConfig)
        }
    }

    /**
     * Break khatam — game wapas, break video hatao.
     */
    fun clearBreakVideo() {
        val breakIds = _uiState.value.compositionConfig.elements
            .filter { it.name == "BREAK_VIDEO_FULL" }
            .map { it.id }
        breakIds.forEach { removeCompositionElement(it) }

        _uiState.update { current ->
            val updatedGame = current.compositionConfig.gameVideoConfig.copy(isVisible = true)
            val updatedConfig = current.compositionConfig.copy(gameVideoConfig = updatedGame)
            activePipeline?.updateCompositionConfig(updatedConfig)
            current.copy(compositionConfig = updatedConfig)
        }
    }

    /**
     * Gallery photo/PNG as overlay on stream.
     */
    fun addOverlayPhoto(uri: Uri) {
        addCompositionElement(
            type = com.example.model.CompositionElementType.PHOTO,
            name = "Pointer Photo",
            uri = uri
        )
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
    /*
     * IMPORTANT ARCHITECTURE RULE:
     *
     * MvpStationViewModel is a UI/lifecycle coordinator.
     * It must NOT forcibly terminate an active recording session
     * just because the ViewModel is being cleared.
     *
     * The active recording stack is expected to live independently
     * from the Activity/ViewModel lifecycle:
     *
     * Activity/UI
     *     ↓
     * MvpStationViewModel
     *
     * Started Foreground Service
     *     ↓
     * MediaProjection
     *     ↓
     * Capture / Output Pipeline
     *
     * Therefore onCleared() must NOT:
     * - stop MediaProjection
     * - stop ScreenCaptureService
     * - stop OutputCompositionPipeline
     * - stop AudioMixerEngine
     *
     * Those resources are stopped only by the explicit recording
     * shutdown path or by an actual capture/service failure.
     */

    timerJob?.cancel()
    timerJob = null

    /*
     * A pending permission request belongs to the UI flow.
     * It is safe to discard when the ViewModel is cleared.
     */
    pendingCaptureRequest = null

    /*
     * Remove the UI callback so a cleared ViewModel is not retained
     * by the long-running foreground capture service.
     */
    try {
        captureService?.setCaptureListener(null)
    } catch (_: Exception) {
    }

    /*
     * Unbind only the ViewModel's client connection.
     *
     * IMPORTANT:
     * unbindService() does NOT stop the started foreground service.
     * The started ScreenCaptureService continues to own the active
     * MediaProjection session.
     */
    if (captureServiceBound) {
        try {
            val context =
                getApplication<Application>().applicationContext

            context.unbindService(
                captureServiceConnection
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Error unbinding ScreenCaptureService during ViewModel cleanup: ${e.message}"
            )
        }

        captureServiceBound = false
    }

    /*
     * Drop only the ViewModel-side references.
     *
     * Do NOT call:
     * captureService?.stopCapture()
     * activePipeline?.stopPipeline()
     * ScreenCaptureService.stopService(context)
     * audioMixer.stop()
     *
     * because doing so would incorrectly terminate an active
     * background recording session.
     */
    captureService = null

captureService = null

    /*
     * Do not null/stop activePipeline or audioMixer here.
     * The running output/audio components must remain alive until
     * the real recording shutdown path explicitly finalizes them.
     */

    FloatingControlBridge.registerStationHandler(null)

    super.onCleared()
} me 
}
