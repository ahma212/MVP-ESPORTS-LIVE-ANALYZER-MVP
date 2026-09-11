package com.example.engine.audio

object SharedBroadcastAudio {
    @Volatile
    private var instance: AudioMixerEngine? = null

    fun mixer(): AudioMixerEngine {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            return AudioMixerEngine(sampleRate = 44100, channelCount = 2).also {
                instance = it
            }
        }
    }
}