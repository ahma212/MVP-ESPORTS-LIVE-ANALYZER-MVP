package com.example.engine.control

import com.example.model.MvpStationUiState
import com.example.model.VideoResolution
import com.example.model.YouTubeLiveUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level bridge between:
 * - MvpStationViewModel / YouTubeLiveViewModel (real logic)
 * - FloatingControlService (remote control HUD)
 *
 * Pointer only sends commands here. Heavy work stays in ViewModels/services.
 * Control layer stays isolated from encoder output.
 */
object FloatingControlBridge {

    private val _stationState = MutableStateFlow(MvpStationUiState())
    val stationState: StateFlow<MvpStationUiState> = _stationState.asStateFlow()

    private val _youtubeState = MutableStateFlow(YouTubeLiveUiState())
    val youtubeState: StateFlow<YouTubeLiveUiState> = _youtubeState.asStateFlow()

    @Volatile private var stationHandler: StationHandler? = null
    @Volatile private var youtubeHandler: YouTubeHandler? = null

    interface StationHandler {
        fun startRecording()
        fun pauseRecording()
        fun resumeRecording()
        fun stopRecording()
        fun toggleMic()
        fun setMicVolume(volume: Float)
        fun toggleInternalAudio()
        fun setInternalAudioVolume(volume: Float)
        fun toggleMusic()
        fun toggleMusicPlayPause()
        fun setMusicVolume(volume: Float)
        fun toggleOverlay()
        fun toggleBannerStrip()
        fun toggleFacecam()
        fun toggleWatermark()
        fun setResolution(resolution: VideoResolution)
    }

    interface YouTubeHandler {
        fun startLive()
        fun endLive()
        fun sendChat(message: String)
    }

    fun publishStationState(state: MvpStationUiState) {
        _stationState.value = state
    }

    fun publishYouTubeState(state: YouTubeLiveUiState) {
        _youtubeState.value = state
    }

    fun registerStationHandler(handler: StationHandler?) {
        stationHandler = handler
    }

    fun registerYouTubeHandler(handler: YouTubeHandler?) {
        youtubeHandler = handler
    }

    fun startRecording() = stationHandler?.startRecording()
    fun pauseRecording() = stationHandler?.pauseRecording()
    fun resumeRecording() = stationHandler?.resumeRecording()
    fun stopRecording() = stationHandler?.stopRecording()
    fun toggleMic() = stationHandler?.toggleMic()
    fun setMicVolume(volume: Float) = stationHandler?.setMicVolume(volume)
    fun toggleInternalAudio() = stationHandler?.toggleInternalAudio()
    fun setInternalAudioVolume(volume: Float) = stationHandler?.setInternalAudioVolume(volume)
    fun toggleMusic() = stationHandler?.toggleMusic()
    fun toggleMusicPlayPause() = stationHandler?.toggleMusicPlayPause()
    fun setMusicVolume(volume: Float) = stationHandler?.setMusicVolume(volume)
    fun toggleOverlay() = stationHandler?.toggleOverlay()
    fun toggleBannerStrip() = stationHandler?.toggleBannerStrip()
    fun toggleFacecam() = stationHandler?.toggleFacecam()
    fun toggleWatermark() = stationHandler?.toggleWatermark()
    fun setResolution(resolution: VideoResolution) = stationHandler?.setResolution(resolution)

    fun startLive() = youtubeHandler?.startLive()
    fun endLive() = youtubeHandler?.endLive()
    fun sendChat(message: String) = youtubeHandler?.sendChat(message)

    // --- Part 4B: UI must show MediaProjection dialog ---
    enum class PendingCaptureAction {
        NONE,
        START_RECORDING,
        START_LIVE
    }

    private val _pendingCaptureAction = MutableStateFlow(PendingCaptureAction.NONE)
    val pendingCaptureAction: StateFlow<PendingCaptureAction> = _pendingCaptureAction.asStateFlow()

    fun requestStartRecordingPermission() {
        _pendingCaptureAction.value = PendingCaptureAction.START_RECORDING
    }

    fun requestStartLivePermission() {
        _pendingCaptureAction.value = PendingCaptureAction.START_LIVE
    }

    fun clearPendingCaptureAction() {
        _pendingCaptureAction.value = PendingCaptureAction.NONE
    }
}