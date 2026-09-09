package com.example.engine.control

import android.view.WindowManager
import com.example.engine.core.CaptureOutputContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ControlLayerManager controls the user-facing interactive layer:
 *
 * - floating pointer
 * - floating button
 * - pointer menu
 * - chat UI
 * - music selection UI
 * - recording UI
 * - live controls
 * - settings
 * - app panels
 * - touch controls
 *
 * CRITICAL ISOLATION RULE:
 * By applying WindowManager.LayoutParams.FLAG_SECURE and separating View hierarchies,
 * Android's compositor ensures that every element managed here is strictly excluded
 * from the MediaProjection VirtualDisplay and the Hardware Video Encoder.
 */
class ControlLayerManager {

    data class ControlLayerState(
        val isFloatingButtonVisible: Boolean = true,
        val isFloatingPointerActive: Boolean = true,
        val isPointerMenuExpanded: Boolean = false,
        val isChatUiVisible: Boolean = false,
        val isMusicSelectorVisible: Boolean = false,
        val isRecordingUiVisible: Boolean = true,
        val isLiveControlsVisible: Boolean = true,
        val isSettingsPanelVisible: Boolean = false,
        val isAppPanelVisible: Boolean = true,
        val isTouchFeedbackVisible: Boolean = true,
        val isSecureExclusionActive: Boolean = true
    )

    private val _state = MutableStateFlow(ControlLayerState())
    val state: StateFlow<ControlLayerState> = _state.asStateFlow()

    fun getWindowLayoutParams(width: Int, height: Int, x: Int = 0, y: Int = 0): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            CaptureOutputContract.ISOLATED_CONTROL_WINDOW_FLAGS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            this.x = x
            this.y = y
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
    }

    fun toggleFloatingButton() {
        _state.update { it.copy(isFloatingButtonVisible = !it.isFloatingButtonVisible) }
    }

    fun toggleFloatingPointer() {
        _state.update { it.copy(isFloatingPointerActive = !it.isFloatingPointerActive) }
    }

    fun togglePointerMenu() {
        _state.update { it.copy(isPointerMenuExpanded = !it.isPointerMenuExpanded) }
    }

    fun toggleChatUi() {
        _state.update { it.copy(isChatUiVisible = !it.isChatUiVisible) }
    }

    fun toggleMusicSelector() {
        _state.update { it.copy(isMusicSelectorVisible = !it.isMusicSelectorVisible) }
    }

    fun toggleSettingsPanel() {
        _state.update { it.copy(isSettingsPanelVisible = !it.isSettingsPanelVisible) }
    }

    /**
     * Verifies that the isolation flags are strictly configured.
     */
    fun verifyIsolationStatus(): Boolean {
        val flags = CaptureOutputContract.ISOLATED_CONTROL_WINDOW_FLAGS
        val isSecure = (flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        return isSecure && _state.value.isSecureExclusionActive
    }
}
