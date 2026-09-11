package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthResult
import com.example.auth.GoogleAuthManager
import com.example.auth.SecureAuthStore
import com.example.engine.audio.AudioMixerEngine
import com.example.engine.capture.ScreenCaptureManager
import com.example.engine.output.OutputCompositionPipeline
import com.example.engine.output.rtmp.RtmpConnectionState
import com.example.engine.output.rtmp.RtmpStreamSink
import com.example.engine.service.ScreenCaptureService
import com.example.model.AudioConfig
import com.example.engine.control.FloatingControlBridge
import com.example.model.BannerStripConfig
import com.example.model.ChatConnectionStatus
import com.example.model.ChatFilterMode
import com.example.model.ColorLutPreset
import com.example.model.LatencyMode
import com.example.model.LiveBroadcastSummary
import com.example.model.LiveChatMessage
import com.example.model.OverlayConfig
import com.example.model.RecordingConfig
import com.example.model.StreamHealth
import com.example.model.StreamPrivacy
import com.example.model.StreamTelemetry
import com.example.model.VideoAdjustmentConfig
import com.example.model.VideoFps
import com.example.model.VideoResolution
import com.example.model.YouTubeChannelInfo
import com.example.model.YouTubeLiveUiState
import com.example.youtube.model.*
import com.example.youtube.service.CreatedBroadcastInfo
import com.example.youtube.service.YouTubeLiveManager
import com.example.youtube.service.YouTubeLiveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class YouTubeLiveViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "YouTubeLiveViewModel"

    private val authStore = SecureAuthStore(application.applicationContext)
    private val authManager = GoogleAuthManager(application.applicationContext, authStore)
    val youTubeLiveManager = YouTubeLiveManager(
    application.applicationContext,
    authStore,
    authManager
)

    // Real-time Audio Mixer Engine for YouTube Live Output
    val audioMixer = AudioMixerEngine(sampleRate = 44100, channelCount = 2)

    private val _uiState = MutableStateFlow(
        YouTubeLiveUiState()
    )
    val uiState: StateFlow<YouTubeLiveUiState> = _uiState.asStateFlow()

    private var outputPipeline: OutputCompositionPipeline? = null
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var rtmpSink: RtmpStreamSink? = null

    private var liveTelemetryJob: Job? = null
    private var connectionStateJob: Job? = null
    private var statusPollJob: Job? = null
    private var chatPollJob: Job? = null
    private var nextChatPageToken: String? = null

    init { 
    viewModelScope.launch {
            uiState.collect { state ->
                FloatingControlBridge.publishYouTubeState(state)
            }
        }

        FloatingControlBridge.registerYouTubeHandler(
            object : FloatingControlBridge.YouTubeHandler {
                override fun startLive() {
                    if (_uiState.value.telemetry.isLive) return

                    val cfg = _uiState.value.streamConfig
                    if (cfg.streamKey.isBlank()) {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Pehle app se YouTube broadcast create / stream key set karein."
                            )
                        }
                        // Bring app to foreground so user can fix config
                        val context = getApplication<Application>().applicationContext
                        val intent = Intent(context, com.example.MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("mvp_action", "open_youtube")
                        }
                        context.startActivity(intent)
                        return
                    }

                    // Need MediaProjection for screen capture into live pipeline
                    FloatingControlBridge.requestStartLivePermission()
                    val context = getApplication<Application>().applicationContext
                    val intent = Intent(context, com.example.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("mvp_action", "start_live")
                    }
                    context.startActivity(intent)
                }

                override fun endLive() {
                    stopLiveStream()
                }

                override fun sendChat(message: String) {
                    sendChatMessage(message)
                }
            }
        )
        restorePersistedSession()

        // Sync Audio Mixer states to control flags and telemetry
        viewModelScope.launch {
            audioMixer.mixerState.collect { mixer ->
                _uiState.update { current ->
                    current.copy(
                        controlFlags = current.controlFlags.copy(
                            isMicMuted = mixer.microphone.isMuted
                        )
                    )
                }
            }
        }
    }
private fun restorePersistedSession() {
    val savedSession = authStore.getSession() ?: return

    _uiState.update {
        it.copy(
            channelInfo = YouTubeChannelInfo(
                isConnected = true,
                channelTitle = savedSession.channelTitle ?: "YouTube Creator",
                channelHandle = savedSession.channelHandle ?: "",
                subscriberCount = savedSession.subscriberCount ?: "Active Channel",
                isLiveStreamingEnabled = savedSession.isLiveStreamingEnabled,
                channelAvatarUrl = savedSession.channelAvatarUrl,
                accountEmail = savedSession.accountEmail,
                channelId = savedSession.channelId,
                videoCount = savedSession.videoCount,
                isTokenExpired = false
            )
        )
    }

    viewModelScope.launch {
        val refreshedToken = authManager.getValidAccessToken()

        if (refreshedToken == null) {
            _uiState.update {
                it.copy(
                    channelInfo = it.channelInfo.copy(
                        isConnected = true,
                        isTokenExpired = true
                    ),
                    errorMessage = "YouTube authorization needs to be refreshed. Please reconnect Google for YouTube access."
                )
            }
        }
    }
}
    fun openAuthDialog() {
        _uiState.update { it.copy(showAuthDialog = true, errorMessage = null, authSuccessMessage = null) }
    }

    fun closeAuthDialog() {
        _uiState.update { it.copy(showAuthDialog = false) }
    }
    /**
     * Connects with Android Credential Manager Google Sign-In.
     */
fun connectWithCredentialManager(activityContext: Context) {
    viewModelScope.launch {
        _uiState.update {
            it.copy(
                isConnectingAccount = true,
                errorMessage = null,
                authSuccessMessage = null
            )
        }

        val result = authManager.signInWithGoogle(activityContext)
        handleAuthResult(result)
    }
}

private fun handleAuthResult(result: AuthResult) {
    when (result) {
        is AuthResult.Success -> {
            val session = result.session

            _uiState.update {
                it.copy(
                    isConnectingAccount = false,
                    showAuthDialog = false,
                    authSuccessMessage =
                        "Connected as ${session.channelTitle} (${session.accountEmail})",
                    channelInfo = YouTubeChannelInfo(
                        isConnected = true,
                        channelTitle =
                            session.channelTitle ?: "YouTube Channel",
                        channelHandle = session.channelHandle ?: "",
                        subscriberCount =
                            session.subscriberCount ?: "0 Subs",
                        isLiveStreamingEnabled =
                            session.isLiveStreamingEnabled,
                        channelAvatarUrl =
                            session.channelAvatarUrl,
                        accountEmail =
                            session.accountEmail,
                        channelId =
                            session.channelId,
                        videoCount =
                            session.videoCount,
                        isTokenExpired = false
                    )
                )
            }
        }

        is AuthResult.Error -> {
            _uiState.update {
                it.copy(
                    isConnectingAccount = false,
                    errorMessage = result.message
                )
            }
        }

        is AuthResult.NeedsUserConsent -> {
            _uiState.update {
                it.copy(
                    isConnectingAccount = false,
                    pendingConsentIntent = result.intent,
                    pendingConsentEmail = result.accountEmail
                )
            }
        }

        is AuthResult.Cancelled -> {
            _uiState.update {
                it.copy(
                    isConnectingAccount = false
                )
            }
        }
    }
}

fun onConsentResult(activityContext: Context, isSuccess: Boolean) {
        val pendingEmail = _uiState.value.pendingConsentEmail
        _uiState.update { it.copy(pendingConsentIntent = null, pendingConsentEmail = null) }
        if (isSuccess && !pendingEmail.isNullOrBlank()) {
            viewModelScope.launch {
                _uiState.update { it.copy(isConnectingAccount = true, errorMessage = null) }
                val result = authManager.obtainOAuth2AccessTokenAndConnect(activityContext, pendingEmail)
                handleAuthResult(result)
            }
        } else {
            _uiState.update {
                it.copy(
                    isConnectingAccount = false,
                    errorMessage = "Google Authorization was cancelled or denied. An OAuth 2.0 access token with YouTube scopes is required."
                )
            }
        }
    }

    fun clearConsentIntent() {
        _uiState.update { it.copy(pendingConsentIntent = null, pendingConsentEmail = null) }
    }

    fun disconnectYouTubeAccount() {
        if (_uiState.value.telemetry.isLive) {
            stopLiveStream()
        }
        viewModelScope.launch {
            authManager.disconnect()
            _uiState.update {
                it.copy(
                    channelInfo = YouTubeChannelInfo(isConnected = false),
                    authSuccessMessage = "YouTube channel disconnected."
                )
            }
        }
    }

    fun switchAccount(activityContext: Context) {
        viewModelScope.launch {
            authManager.disconnect()
            _uiState.update {
                it.copy(
                    channelInfo = YouTubeChannelInfo(isConnected = false),
                    showAuthDialog = true
                )
            }
        }
    }

    fun updateStreamTitle(title: String) {
        _uiState.update { it.copy(streamConfig = it.streamConfig.copy(title = title)) }
    }

    fun updateStreamDescription(description: String) {
        _uiState.update { it.copy(streamConfig = it.streamConfig.copy(description = description)) }
    }

    fun updateGameTitle(game: String) {
        _uiState.update { it.copy(streamConfig = it.streamConfig.copy(gameTitle = game)) }
    }

    fun updatePrivacy(privacy: StreamPrivacy) {
        _uiState.update { it.copy(streamConfig = it.streamConfig.copy(privacy = privacy)) }
    }

    fun updateLatency(mode: LatencyMode) {
        _uiState.update { it.copy(streamConfig = it.streamConfig.copy(latencyMode = mode)) }
    }

    fun updateResolution(resolution: VideoResolution) {
        _uiState.update { it.copy(streamConfig = it.streamConfig.copy(resolution = resolution)) }
    }

    fun updateFps(fps: VideoFps) {
        _uiState.update { it.copy(streamConfig = it.streamConfig.copy(fps = fps)) }
    }

    fun updateBitrate(bitrateMbps: Int) {
        _uiState.update { it.copy(streamConfig = it.streamConfig.copy(bitrateMbps = bitrateMbps)) }
    }

    fun setThumbnailUri(uri: Uri?) {
        _uiState.update { it.copy(streamConfig = it.streamConfig.copy(customThumbnailUri = uri)) }
    }

    fun updateRtmpServerUrl(url: String) {
        _uiState.update { it.copy(streamConfig = it.streamConfig.copy(rtmpServerUrl = url)) }
    }

    fun updateStreamKey(key: String) {
        _uiState.update { it.copy(streamConfig = it.streamConfig.copy(streamKey = key)) }
    }

    /**
     * Calls real YouTube Live API to create a live broadcast, RTMP ingest stream,
     * and bind them together.
     */
    fun createRealYouTubeBroadcast() {
        val currentState = _uiState.value
        if (!currentState.channelInfo.isConnected) {
            _uiState.update { it.copy(errorMessage = "Please connect your YouTube channel first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isStartingStream = true, errorMessage = null, authSuccessMessage = null) }
            val cfg = _uiState.value.streamConfig

            val result = youTubeLiveManager.createLiveBroadcast(
                title = cfg.title,
                description = cfg.description,
                privacy = cfg.privacy,
                latencyMode = cfg.latencyMode,
                resolution = cfg.resolution,
                fps = cfg.fps,
                customThumbnailUri = cfg.customThumbnailUri
            )

            when (result) {
                is YouTubeLiveResult.Success -> {
                    val info = result.data
                    _uiState.update {
                        it.copy(
                            isStartingStream = false,
                            authSuccessMessage = "YouTube Live Broadcast Created! Stream: ${info.watchUrl}",
                            streamConfig = it.streamConfig.copy(
                                broadcastId = info.broadcastId,
                                streamId = info.streamId,
                                liveChatId = info.liveChatId,
                                rtmpServerUrl = info.rtmpIngestUrl,
                                streamKey = info.streamKey,
                                watchUrl = info.watchUrl,
                                liveStatus = "BOUND (READY TO STREAM)",
                                connectionState = "READY_TO_STREAM"
                            )
                        )
                    }
                    if (!info.liveChatId.isNullOrBlank()) {
                        startChatPolling(info.liveChatId)
                    }
                }
                is YouTubeLiveResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isStartingStream = false,
                            errorMessage = "YouTube API Error: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Loads upcoming and active broadcasts from user's connected YouTube channel.
     */
    fun loadUpcomingBroadcasts() {
        if (!_uiState.value.channelInfo.isConnected) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBroadcasts = true, errorMessage = null) }
            val result = youTubeLiveManager.listUpcomingBroadcasts()
            when (result) {
                is YouTubeLiveResult.Success -> {
                    val summaries = result.data.map { item ->
                        LiveBroadcastSummary(
                            id = item.id,
                            title = item.snippet?.title ?: "Untitled Broadcast",
                            description = item.snippet?.description,
                            scheduledStartTime = item.snippet?.scheduledStartTime,
                            lifeCycleStatus = item.status?.lifeCycleStatus ?: "ready",
                            privacyStatus = item.status?.privacyStatus,
                            liveChatId = item.snippet?.liveChatId,
                            boundStreamId = item.contentDetails?.boundStreamId,
                            thumbnailUrl = null
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingBroadcasts = false,
                            upcomingBroadcasts = summaries
                        )
                    }
                }
                is YouTubeLiveResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingBroadcasts = false,
                            errorMessage = "Failed to load broadcasts: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Selects an existing broadcast from YouTube to manage and stream to.
     */
    fun selectBroadcast(summary: LiveBroadcastSummary) {
        _uiState.update { current ->
            current.copy(
                streamConfig = current.streamConfig.copy(
                    title = summary.title,
                    description = summary.description ?: current.streamConfig.description,
                    broadcastId = summary.id,
                    streamId = summary.boundStreamId,
                    liveChatId = summary.liveChatId,
                    watchUrl = "https://youtu.be/${summary.id}",
                    liveStatus = summary.lifeCycleStatus.uppercase(),
                    connectionState = if (summary.lifeCycleStatus.equals("live", ignoreCase = true)) "LIVE" else "READY"
                ),
                authSuccessMessage = "Selected broadcast: ${summary.title}"
            )
        }
        if (!summary.liveChatId.isNullOrBlank()) {
            startChatPolling(summary.liveChatId)
        }
    }

    /**
     * Updates an existing live broadcast's title, description, and privacy settings on YouTube.
     */
    fun updateCurrentBroadcast(title: String, description: String, privacy: StreamPrivacy) {
        val broadcastId = _uiState.value.streamConfig.broadcastId
        if (broadcastId.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    streamConfig = it.streamConfig.copy(title = title, description = description, privacy = privacy),
                    authSuccessMessage = "Local stream configuration updated."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingBroadcast = true, errorMessage = null) }
            val result = youTubeLiveManager.updateLiveBroadcast(broadcastId, title, description, privacy)
            when (result) {
                is YouTubeLiveResult.Success -> {
                    val updated = result.data
                    _uiState.update {
                        it.copy(
                            isUpdatingBroadcast = false,
                            streamConfig = it.streamConfig.copy(
                                title = updated.snippet?.title ?: title,
                                description = updated.snippet?.description ?: description,
                                privacy = privacy
                            ),
                            authSuccessMessage = "YouTube Live Broadcast updated successfully!"
                        )
                    }
                }
                is YouTubeLiveResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isUpdatingBroadcast = false,
                            errorMessage = "Broadcast update failed: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Uploads and sets custom thumbnail for the current active broadcast video on YouTube.
     */
    fun uploadThumbnailForCurrentBroadcast(imageUri: Uri) {
        val broadcastId = _uiState.value.streamConfig.broadcastId
        _uiState.update {
            it.copy(streamConfig = it.streamConfig.copy(customThumbnailUri = imageUri))
        }

        if (broadcastId.isNullOrBlank()) {
            _uiState.update { it.copy(authSuccessMessage = "Thumbnail saved locally. It will upload when broadcast is created.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingThumbnail = true, errorMessage = null) }
            val result = youTubeLiveManager.uploadThumbnail(broadcastId, imageUri)
            when (result) {
                is YouTubeLiveResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isUploadingThumbnail = false,
                            authSuccessMessage = "Custom thumbnail published to YouTube!"
                        )
                    }
                }
                is YouTubeLiveResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isUploadingThumbnail = false,
                            errorMessage = "Thumbnail upload error: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Transitions YouTube broadcast lifecycle to LIVE.
     */
    fun startLiveBroadcastLifecycle() {
        val broadcastId = _uiState.value.streamConfig.broadcastId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingStream = true, errorMessage = null) }
            val result = youTubeLiveManager.startLiveBroadcast(broadcastId)
            when (result) {
                is YouTubeLiveResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isStartingStream = false,
                            streamConfig = it.streamConfig.copy(
                                liveStatus = "LIVE ON YOUTUBE",
                                connectionState = "STREAMING"
                            ),
                            authSuccessMessage = "Broadcast is now officially LIVE on YouTube!"
                        )
                    }
                }
                is YouTubeLiveResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isStartingStream = false,
                            errorMessage = "Failed to transition to LIVE: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Transitions YouTube broadcast lifecycle to COMPLETE (Ends stream).
     */
    fun endLiveBroadcastLifecycle() {
        val broadcastId = _uiState.value.streamConfig.broadcastId ?: return
        viewModelScope.launch {
            val result = youTubeLiveManager.endLiveBroadcast(broadcastId)
            when (result) {
                is YouTubeLiveResult.Success -> {
                    stopLiveStream()
                    _uiState.update {
                        it.copy(
                            streamConfig = it.streamConfig.copy(
                                liveStatus = "COMPLETE (ENDED)",
                                connectionState = "OFFLINE"
                            ),
                            authSuccessMessage = "YouTube Live broadcast completed."
                        )
                    }
                }
                is YouTubeLiveResult.Error -> {
                    stopLiveStream()
                    _uiState.update {
                        it.copy(errorMessage = "Broadcast end error: ${result.message}")
                    }
                }
            }
        }
    }

    // ==========================================
    // REAL YOUTUBE LIVE CHAT ENGINE
    // ==========================================

    /**
     * Starts continuous polling for incoming YouTube Live Chat messages.
     * Adheres strictly to the polling interval specified in the YouTube API response.
     */
    fun startChatPolling(explicitChatId: String? = null) {
        chatPollJob?.cancel()
        val chatId = explicitChatId ?: _uiState.value.streamConfig.liveChatId

        if (chatId.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    chatConnectionStatus = ChatConnectionStatus.NO_CHAT_ACTIVE,
                    chatErrorMessage = "No active broadcast liveChatId. Create or select a broadcast to enable chat."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                chatConnectionStatus = ChatConnectionStatus.CONNECTING,
                chatErrorMessage = null
            )
        }

        chatPollJob = viewModelScope.launch(Dispatchers.IO) {
            var delayIntervalMs = 5000L
            while (isActive) {
                val result = youTubeLiveManager.fetchLiveChatMessages(chatId, nextChatPageToken)
                when (result) {
                    is YouTubeLiveResult.Success -> {
                        val response = result.data
                        nextChatPageToken = response.nextPageToken
                        delayIntervalMs = (response.pollingIntervalMillis ?: 5000L).coerceIn(2000L, 30000L)

                        val apiItems = response.items ?: emptyList()
                        if (apiItems.isNotEmpty()) {
                            val parsed = apiItems.mapNotNull { item ->
                                val snippet = item.snippet ?: return@mapNotNull null
                                val author = item.authorDetails
                                val displayName = author?.displayName ?: "Viewer"
                                val messageText = snippet.displayMessage
                                    ?: snippet.textMessageDetails?.messageText
                                    ?: snippet.superChatDetails?.userComment
                                    ?: ""

                                val isSuperChat = snippet.type == "superChatEvent" || snippet.superChatDetails != null
                                val superAmount = snippet.superChatDetails?.amountDisplayString
                                val tier = snippet.superChatDetails?.tier ?: 1
                                val isOwner = author?.isChatOwner == true
                                val isMod = author?.isChatModerator == true
                                val isSponsor = author?.isChatSponsor == true
                                val formattedTime = formatChatTimestamp(snippet.publishedAt)

                                LiveChatMessage(
                                    id = item.id,
                                    author = displayName,
                                    message = messageText,
                                    authorAvatarUrl = author?.profileImageUrl,
                                    channelId = author?.channelId,
                                    publishedAt = snippet.publishedAt,
                                    timestampFormatted = formattedTime,
                                    isSuperChat = isSuperChat,
                                    superChatAmount = superAmount,
                                    superChatTier = tier,
                                    isOwner = isOwner,
                                    isModerator = isMod,
                                    isSponsor = isSponsor
                                )
                            }

                            withContext(Dispatchers.Main) {
                                _uiState.update { current ->
                                    val existingIds = current.liveChatMessages.map { it.id }.toSet()
                                    val newMessages = parsed.filter { it.id !in existingIds }
                                    val updatedList = (current.liveChatMessages + newMessages).takeLast(250)
                                    current.copy(
                                        liveChatMessages = updatedList,
                                        chatConnectionStatus = ChatConnectionStatus.LIVE_CONNECTED,
                                        chatErrorMessage = null
                                    )
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                _uiState.update { current ->
                                    current.copy(
                                        chatConnectionStatus = ChatConnectionStatus.LIVE_CONNECTED,
                                        chatErrorMessage = null
                                    )
                                }
                            }
                        }
                    }
                    is YouTubeLiveResult.Error -> {
                        withContext(Dispatchers.Main) {
                            _uiState.update { current ->
                                current.copy(
                                    chatConnectionStatus = if (result.statusCode == 401) {
                                        ChatConnectionStatus.AUTH_ERROR
                                    } else {
                                        ChatConnectionStatus.NETWORK_ERROR
                                    },
                                    chatErrorMessage = result.message
                                )
                            }
                        }
                        delayIntervalMs = if (result.statusCode == 401) 15000L else 10000L
                    }
                }
                delay(delayIntervalMs)
            }
        }
    }

    fun stopChatPolling() {
        chatPollJob?.cancel()
        chatPollJob = null
        _uiState.update { it.copy(chatConnectionStatus = ChatConnectionStatus.PAUSED) }
    }

    fun refreshChatManually() {
        val chatId = _uiState.value.streamConfig.liveChatId
        if (!chatId.isNullOrBlank()) {
            startChatPolling(chatId)
        }
    }

    fun onChatInputChanged(text: String) {
        _uiState.update { it.copy(chatInputText = text.take(200)) }
    }

    fun sendChatMessage(messageText: String? = null) {
        val text = (messageText ?: _uiState.value.chatInputText).trim()
        val chatId = _uiState.value.streamConfig.liveChatId

        if (text.isBlank()) return
        if (chatId.isNullOrBlank()) {
            _uiState.update {
                it.copy(chatErrorMessage = "Cannot send message: No active YouTube liveChatId.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingChatMessage = true, chatErrorMessage = null) }
            val result = youTubeLiveManager.sendLiveChatMessage(chatId, text)
            when (result) {
                is YouTubeLiveResult.Success -> {
                    val item = result.data
                    val author = item.authorDetails
                    val userMsg = LiveChatMessage(
                        id = item.id,
                        author = author?.displayName ?: _uiState.value.channelInfo.channelTitle,
                        message = text,
                        authorAvatarUrl = author?.profileImageUrl ?: _uiState.value.channelInfo.channelAvatarUrl,
                        channelId = author?.channelId ?: _uiState.value.channelInfo.channelId,
                        timestampFormatted = "Just now",
                        isOwner = true
                    )
                    _uiState.update { current ->
                        val updatedList = (current.liveChatMessages + userMsg).takeLast(250)
                        current.copy(
                            isSendingChatMessage = false,
                            chatInputText = "",
                            liveChatMessages = updatedList,
                            chatErrorMessage = null
                        )
                    }
                }
                is YouTubeLiveResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSendingChatMessage = false,
                            chatErrorMessage = "Failed to send message: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    fun sendQuickChatMacro(macroText: String) {
        sendChatMessage(macroText)
    }

    fun setChatFilterMode(mode: ChatFilterMode) {
        _uiState.update { it.copy(chatFilterMode = mode) }
    }

    fun toggleChatAutoScroll() {
        _uiState.update { it.copy(isChatAutoScroll = !it.isChatAutoScroll) }
    }

    fun clearChatMessages() {
        _uiState.update { it.copy(liveChatMessages = emptyList()) }
    }

    fun clearLocalChat() {
        clearChatMessages()
    }

    private fun formatChatTimestamp(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = inputFormat.parse(isoDate.take(19))
            if (date != null) {
                val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                outputFormat.format(date)
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Starts the full native streaming pipeline:
     * Screen capture -> Output Composition Layer -> Hardware Audio/Video Encoder -> YouTube RTMP Ingest.
     */
    fun startLiveStream(
    mediaProjection: MediaProjection? = null,
    screenWidth: Int = 1080,
    screenHeight: Int = 2400,
    densityDpi: Int = 420,
    resultCode: Int? = null,
    resultData: Intent? = null
) {
        val currentState = _uiState.value
        if (currentState.telemetry.isLive) return

        val cfg = currentState.streamConfig
        if (cfg.streamKey.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Stream Key is required. Tap 'Create Real Broadcast' or enter stream key.") }
            return
        }

        val context = getApplication<Application>().applicationContext
        _uiState.update { it.copy(isStartingStream = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
               // 1. Start foreground screen capture service
if (resultCode != null && resultData != null) {
    ScreenCaptureService.startService(
        context = context,
        resultCode = resultCode,
        resultData = Intent(resultData)
    )
}

                // 2. Setup RTMP sink
                val targetFps = cfg.fps.fpsValue.coerceIn(30, 60)

val (targetW, targetH, recommendedBitrateKbps) = when {
    cfg.resolution == VideoResolution.RES_720P && targetFps <= 30 ->
        Triple(1280, 720, 2500)
    cfg.resolution == VideoResolution.RES_720P ->
        Triple(1280, 720, 4000)
    cfg.resolution == VideoResolution.RES_1080P && targetFps <= 30 ->
        Triple(1920, 1080, 4500)
    else ->
        Triple(1920, 1080, 6000) // 1080p60 only if device supports
}

val targetBitrateKbps = if (cfg.bitrateMbps > 0) {
    (cfg.bitrateMbps * 1000).coerceIn(1500, 8000)
} else {
    recommendedBitrateKbps
}
                val sink = RtmpStreamSink(
                    rtmpUrl = cfg.rtmpServerUrl,
                    streamKey = cfg.streamKey,
                    width = targetW,
                    height = targetH,
                    fps = cfg.fps.fpsValue,
                    targetBitrateKbps = targetBitrateKbps
                )

                val connected = sink.start()
                if (!connected) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isStartingStream = false,
                                errorMessage = "Failed to connect to YouTube RTMP Ingestion server."
                            )
                        }
                    }
                    return@launch
                }

                rtmpSink = sink

                // 3. Setup Output Composition Pipeline (ISOLATED from UI controls)
                val recordingConfig = RecordingConfig(
                    resolution = cfg.resolution,
                    fps = cfg.fps,
                    bitrateMbps = cfg.bitrateMbps
                )

                val pipeline = OutputCompositionPipeline(
                    recordingConfig = recordingConfig,
                    overlayConfig = OverlayConfig(),
                    bannerConfig = BannerStripConfig(),
                    videoAdjustmentConfig = _uiState.value.videoAdjustmentConfig,
                    deviceScreenWidth = screenWidth,
                    deviceScreenHeight = screenHeight,
                    context = context
                )

                val encoderSurface = pipeline.startLiveStreamPipeline(
                    rtmpSink = sink,
                    outputFile = null,
                    audioMixer = audioMixer,
                    mediaProjection = mediaProjection
                )
                outputPipeline = pipeline

                // 4. Attach MediaProjection to Surface if available
                if (mediaProjection != null) {
                    val capture = ScreenCaptureManager(context)
                    capture.startCapture(mediaProjection, encoderSurface, targetW, targetH, densityDpi)
                    screenCaptureManager = capture
                }

               // 5. Wait until YouTube reports the RTMP ingest as active, then go LIVE
                val broadcastId = cfg.broadcastId
                val streamId = cfg.streamId
                if (!broadcastId.isNullOrBlank()) {
                    delay(3000) // brief ramp-up so first packets can reach YouTube
                    var attempts = 0
                    while (attempts < 8) {
                        val health = if (!streamId.isNullOrBlank()) {
                            youTubeLiveManager.pollStreamHealth(streamId)
                        } else {
                            null
                        }
                        if (health.equals("active", ignoreCase = true)) {
                            break
                        }
                        delay(1500)
                        attempts++
                    }
                    youTubeLiveManager.startLiveBroadcast(broadcastId)
                }

                // 6. Monitor telemetry & connection states
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isStartingStream = false,
                            streamConfig = it.streamConfig.copy(
                                liveStatus = "LIVE ON YOUTUBE",
                                connectionState = "STREAMING"
                            ),
                            telemetry = StreamTelemetry(
                                isLive = true,
                                elapsedSeconds = 0,
                                health = StreamHealth.GOOD,
                                currentBitrateKbps = 0,
                                currentFps = 0,
                                droppedFrames = 0,
                                viewerCount = 0,
                                likesCount = 0
                            )
                        )
                    }
                }

                observeSinkTelemetry(sink)
                startPollingStatus(broadcastId, cfg.streamId)

            } catch (e: Exception) {
                Log.e(TAG, "Error starting live stream pipeline: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isStartingStream = false,
                            errorMessage = "Streaming error: ${e.localizedMessage}"
                        )
                    }
                }
                stopLiveStream()
            }
        }
    }

    private fun observeSinkTelemetry(sink: RtmpStreamSink) {
        liveTelemetryJob?.cancel()
        liveTelemetryJob = viewModelScope.launch {
            sink.telemetry.collect { telemetry ->
                _uiState.update { current ->
                    if (!current.telemetry.isLive && !telemetry.isLive) return@update current
                    current.copy(
                        telemetry = current.telemetry.copy(
                            isLive = telemetry.isLive,
                            elapsedSeconds = telemetry.elapsedSeconds,
                            health = telemetry.health,
                            currentBitrateKbps = telemetry.currentBitrateKbps,
                            currentFps = telemetry.currentFps,
                            droppedFrames = telemetry.droppedFrames
                        )
                    )
                }
            }
        }

        connectionStateJob?.cancel()
        connectionStateJob = viewModelScope.launch {
            sink.connectionState.collect { state ->
                _uiState.update { current ->
                    current.copy(
                        streamConfig = current.streamConfig.copy(
                            connectionState = state.label
                        )
                    )
                }
            }
        }
    }

    private fun startPollingStatus(broadcastId: String?, streamId: String?) {
        statusPollJob?.cancel()
        if (broadcastId.isNullOrBlank()) return

        statusPollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _uiState.value.telemetry.isLive) {
                delay(10_000)
                val status = youTubeLiveManager.pollBroadcastStatus(broadcastId)
                val health = if (!streamId.isNullOrBlank()) youTubeLiveManager.pollStreamHealth(streamId) else null

                withContext(Dispatchers.Main) {
                    _uiState.update { current ->
                        current.copy(
                            streamConfig = current.streamConfig.copy(
                                liveStatus = status?.uppercase() ?: current.streamConfig.liveStatus
                            ),
                            telemetry = current.telemetry.copy(
                                health = when (health?.lowercase()) {
                                     "active" -> StreamHealth.EXCELLENT
                                    "good" -> StreamHealth.GOOD
                                    "ok" -> StreamHealth.GOOD
                                    "bad" -> StreamHealth.POOR
                                    "nodata" -> StreamHealth.WARNING
                                    else -> current.telemetry.health
                                }
                            )
                        )
                    }
                }
            }
        }
    }

    fun stopLiveStream() {
        val cfg = _uiState.value.streamConfig
        val broadcastId = cfg.broadcastId

        viewModelScope.launch(Dispatchers.IO) {
            if (!broadcastId.isNullOrBlank()) {
                youTubeLiveManager.endLiveBroadcast(broadcastId)
            }

            screenCaptureManager?.stopCapture()
            screenCaptureManager = null

            outputPipeline?.stopPipeline()
            outputPipeline = null

            rtmpSink?.stop()
            rtmpSink = null

            liveTelemetryJob?.cancel()
            connectionStateJob?.cancel()
            statusPollJob?.cancel()

            val context = getApplication<Application>().applicationContext
            ScreenCaptureService.stopService(context)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        telemetry = StreamTelemetry(
                            isLive = false,
                            elapsedSeconds = 0,
                            health = StreamHealth.OFFLINE,
                            currentBitrateKbps = 0,
                            currentFps = 0
                        ),
                        streamConfig = it.streamConfig.copy(
                            liveStatus = "STREAM CONCLUDED",
                            connectionState = "DISCONNECTED"
                        ),
                        authSuccessMessage = "YouTube Live stream ended cleanly."
                    )
                }
            }
        }
    }

    // --- Audio Mixer & Control Methods ---
    fun toggleMuteMic() {
        audioMixer.toggleMicrophoneMute()
    }

    fun setMicrophoneVolume(volume: Float) {
        audioMixer.setMicrophoneVolume(volume)
    }

    fun toggleInternalAudio() {
        audioMixer.toggleInternalAudio()
    }

    fun toggleInternalAudioMute() {
        audioMixer.toggleInternalAudioMute()
    }

    fun setInternalAudioVolume(volume: Float) {
        audioMixer.setInternalAudioVolume(volume)
    }

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

    fun setMusicVolume(volume: Float) {
        audioMixer.setMusicVolume(volume)
    }

    fun setMasterVolume(volume: Float) {
        audioMixer.setMasterVol(volume)
    }

    fun toggleMasterMute() {
        audioMixer.toggleMasterMute()
    }

    fun toggleEmergencySlate() {
        _uiState.update {
            it.copy(controlFlags = it.controlFlags.copy(isEmergencySlateActive = !it.controlFlags.isEmergencySlateActive))
        }
    }

    fun toggleChatSlowMode() {
        _uiState.update {
            it.copy(controlFlags = it.controlFlags.copy(isChatSlowMode = !it.controlFlags.isChatSlowMode))
        }
    }

    fun toggleSuperChatOnly() {
        _uiState.update {
            it.copy(controlFlags = it.controlFlags.copy(isSuperChatOnly = !it.controlFlags.isSuperChatOnly))
        }
    }

    fun toggleSubscriberOnly() {
        _uiState.update {
            it.copy(controlFlags = it.controlFlags.copy(isSubscriberOnly = !it.controlFlags.isSubscriberOnly))
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null, authSuccessMessage = null) }
    }

    // Video Adjustment & GPU Color Enhancements for Live Broadcast
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

    fun setColorLutPreset(preset: ColorLutPreset) {
        updateVideoAdjustment { it.copy(colorLutPreset = preset) }
    }

    private fun updateVideoAdjustment(transform: (VideoAdjustmentConfig) -> VideoAdjustmentConfig) {
        _uiState.update { current ->
            val updated = transform(current.videoAdjustmentConfig)
            outputPipeline?.updateVideoAdjustmentConfig(updated)
            current.copy(videoAdjustmentConfig = updated)
        }
    }

    override fun onCleared() {
        FloatingControlBridge.registerYouTubeHandler(null)
        stopChatPolling()
        // Do NOT stopLiveStream() or audioMixer.stop() here.
        // Active live session must survive Activity/ViewModel clear.
        // User ends live explicitly from pointer or YouTube Live screen.
        super.onCleared()
    }
}
