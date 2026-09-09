package com.example.engine.audio

import android.net.Uri

enum class MusicPlaybackState(val label: String) {
    STOPPED("Stopped"),
    PLAYING("Playing"),
    PAUSED("Paused")
}

data class InternalAudioState(
    val isEnabled: Boolean = true,
    val isMuted: Boolean = false,
    val volume: Float = 0.85f,
    val peakLevel: Float = 0f
)

data class MicrophoneState(
    val isEnabled: Boolean = true,
    val isMuted: Boolean = false,
    val volume: Float = 1.0f,
    val noiseSuppression: Boolean = true,
    val echoCancellation: Boolean = true,
    val voiceClarity: Boolean = true,
    val isNoiseSuppressorActive: Boolean = false,
    val isAcousticEchoCancelerActive: Boolean = false,
    val isAutomaticGainControlActive: Boolean = false,
    val peakLevel: Float = 0f
)

data class MusicPlayerState(
    val isEnabled: Boolean = true,
    val isMuted: Boolean = false,
    val volume: Float = 0.40f,
    val isLooping: Boolean = true,
    val playbackState: MusicPlaybackState = MusicPlaybackState.STOPPED,
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val trackUri: Uri? = null,
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val peakLevel: Float = 0f
)

data class AudioMixerState(
    val masterVolume: Float = 1.0f,
    val isMasterMuted: Boolean = false,
    val internalAudio: InternalAudioState = InternalAudioState(),
    val microphone: MicrophoneState = MicrophoneState(),
    val music: MusicPlayerState = MusicPlayerState(),
    val masterPeakLevel: Float = 0f,
    val isMixing: Boolean = false
)
