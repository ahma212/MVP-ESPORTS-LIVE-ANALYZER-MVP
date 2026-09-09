package com.example.engine.composition

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.example.model.CompositionElement

/**
 * Manages GPU video decoding for overlay video clips (loopable stingers, video badges, PiP).
 * Renders directly into an OpenGL OES SurfaceTexture.
 */
class VideoClipOverlayPlayer(private val context: Context) {

    private val TAG = "VideoClipOverlayPlayer"

    private var mediaPlayer: MediaPlayer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    var oesTexId: Int = 0
        private set

    private var currentUri: String? = null
    private var isPlaying: Boolean = false
    private val transformMatrix = FloatArray(16)

    fun initGl() {
        if (oesTexId == 0) {
            oesTexId = GlesUtils.generateOesTexture()
            surfaceTexture = SurfaceTexture(oesTexId).apply {
                setOnFrameAvailableListener {
                    // Frame ready for consumption
                }
            }
            surface = Surface(surfaceTexture)
        }
    }

    fun syncElement(element: CompositionElement) {
        val uriStr = element.contentUri
        if (uriStr.isNullOrBlank()) {
            stop()
            return
        }

        if (uriStr != currentUri) {
            currentUri = uriStr
            loadVideo(uriStr, element.loopVideo)
        } else {
            mediaPlayer?.isLooping = element.loopVideo
            if (element.isVisible && !isPlaying) {
                resume()
            } else if (!element.isVisible && isPlaying) {
                pause()
            }
        }
    }

    private fun loadVideo(uriStr: String, loop: Boolean) {
        stop()
        initGl()

        try {
            val uri = Uri.parse(uriStr)
            val mp = MediaPlayer().apply {
                setDataSource(context, uri)
                setSurface(surface)
                isLooping = loop
                setVolume(0.0f, 0.0f) // Audio is handled via mixer if desired, or silent video overlay
                setOnPreparedListener { player ->
                    player.start()
                    this@VideoClipOverlayPlayer.isPlaying = true
                    Log.i(TAG, "Video overlay clip playing: $uriStr")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error on overlay video: what=$what, extra=$extra")
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load overlay video: ${e.message}", e)
        }
    }

    fun updateTexImage(): FloatArray {
        surfaceTexture?.updateTexImage()
        surfaceTexture?.getTransformMatrix(transformMatrix)
        return transformMatrix
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                isPlaying = false
            }
        } catch (_: Exception) {}
    }

    fun resume() {
        try {
            mediaPlayer?.start()
            isPlaying = true
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isPlaying = false
        currentUri = null
    }

    fun release() {
        stop()
        surface?.release()
        surface = null
        surfaceTexture?.release()
        surfaceTexture = null
        if (oesTexId > 0) {
            GlesUtils.deleteTexture(oesTexId)
            oesTexId = 0
        }
    }
}
