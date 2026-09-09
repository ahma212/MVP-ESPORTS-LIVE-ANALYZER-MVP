package com.example.model

import android.content.Intent
import android.net.Uri

enum class StreamPrivacy(val label: String) {
    PUBLIC("Public (Everyone)"),
    UNLISTED("Unlisted (Anyone with link)"),
    PRIVATE("Private (Only you)")
}

enum class LatencyMode(val label: String, val latencyDescription: String) {
    ULTRA_LOW("Ultra-Low Latency", "~1-2 seconds lag, ideal for fast chat reaction"),
    LOW("Low Latency", "~5-7 seconds lag, balanced stability"),
    NORMAL("Normal Latency", "~15-30 seconds, best quality & buffering protection")
}

enum class StreamHealth(val label: String, val hexColor: String) {
    EXCELLENT("EXCELLENT", "#00E676"),
    GOOD("GOOD", "#00F0FF"),
    WARNING("UNSTABLE", "#FFB800"),
    POOR("POOR QUALITY", "#FF2A55"),
    OFFLINE("OFFLINE", "#64748B")
}

enum class ChatConnectionStatus(val label: String) {
    DISCONNECTED("Chat Disconnected"),
    CONNECTING("Connecting to Chat..."),
    LIVE_CONNECTED("Live Chat Active"),
    POLLING("Syncing Messages..."),
    PAUSED("Chat Paused"),
    NO_CHAT_ACTIVE("No Active Broadcast"),
    AUTH_ERROR("Auth Token Expired"),
    NETWORK_ERROR("Reconnecting Network...")
}

enum class ChatFilterMode(val label: String) {
    ALL("All Messages"),
    SUPER_CHATS("Super Chats"),
    MODERATORS("Moderators"),
    MEMBERS("Members Only")
}

data class LiveBroadcastSummary(
    val id: String,
    val title: String,
    val description: String?,
    val scheduledStartTime: String?,
    val lifeCycleStatus: String,
    val privacyStatus: String?,
    val liveChatId: String?,
    val boundStreamId: String?,
    val thumbnailUrl: String? = null
)

data class YouTubeChannelInfo(
    val isConnected: Boolean = false,
    val channelTitle: String = "No Channel Connected",
    val channelHandle: String = "@creator",
    val subscriberCount: String = "0 Subscribers",
    val isLiveStreamingEnabled: Boolean = false,
    val channelAvatarUrl: String? = null,
    val accountEmail: String? = null,
    val channelId: String? = null,
    val videoCount: String? = null,
    val isTokenExpired: Boolean = false
)

data class LiveStreamConfig(
    val title: String = "MVP ESPORTS GRAND CHAMPIONSHIP | ROAD TO PRO",
    val description: String = "Welcome to the official live stream of MVP Esports! Drop a like, subscribe, and follow the tournament journey.\n\n🎮 Game: Mobile Gaming Championship\n⚡ Powered by MVP ESPORTS LIVE",
    val gameTitle: String = "Valorant Mobile / Esports Pro",
    val privacy: StreamPrivacy = StreamPrivacy.PUBLIC,
    val latencyMode: LatencyMode = LatencyMode.ULTRA_LOW,
    val customThumbnailUri: Uri? = null,
    val rtmpServerUrl: String = "rtmp://a.rtmp.youtube.com/live2",
    val streamKey: String = "",
    val autoStartBroadcast: Boolean = true,
    val autoStopBroadcast: Boolean = true,
    val resolution: VideoResolution = VideoResolution.RES_1080P,
    val fps: VideoFps = VideoFps.FPS_60,
    val bitrateMbps: Int = 8,
    val broadcastId: String? = null,
    val streamId: String? = null,
    val liveChatId: String? = null,
    val watchUrl: String? = null,
    val liveStatus: String = "READY",
    val connectionState: String = "OFFLINE"
)

data class StreamTelemetry(
    val isLive: Boolean = false,
    val elapsedSeconds: Long = 0,
    val health: StreamHealth = StreamHealth.OFFLINE,
    val currentBitrateKbps: Int = 0,
    val currentFps: Int = 0,
    val droppedFrames: Int = 0,
    val viewerCount: Int = 0,
    val likesCount: Int = 0
)

data class StreamControlFlags(
    val isMicMuted: Boolean = false,
    val isEmergencySlateActive: Boolean = false,
    val isChatSlowMode: Boolean = false,
    val isSuperChatOnly: Boolean = false,
    val isSubscriberOnly: Boolean = false
)

data class LiveChatMessage(
    val id: String,
    val author: String,
    val message: String,
    val authorAvatarUrl: String? = null,
    val channelId: String? = null,
    val publishedAt: String? = null,
    val timestampFormatted: String = "",
    val isSuperChat: Boolean = false,
    val superChatAmount: String? = null,
    val superChatTier: Int = 1,
    val isOwner: Boolean = false,
    val isModerator: Boolean = false,
    val isSponsor: Boolean = false,
    val isSystemNotice: Boolean = false
)

data class YouTubeLiveUiState(
    val channelInfo: YouTubeChannelInfo = YouTubeChannelInfo(),
    val streamConfig: LiveStreamConfig = LiveStreamConfig(),
    val telemetry: StreamTelemetry = StreamTelemetry(),
    val controlFlags: StreamControlFlags = StreamControlFlags(),
    val liveChatMessages: List<LiveChatMessage> = emptyList(),
    val chatConnectionStatus: ChatConnectionStatus = ChatConnectionStatus.DISCONNECTED,
    val chatInputText: String = "",
    val isSendingChatMessage: Boolean = false,
    val chatErrorMessage: String? = null,
    val chatFilterMode: ChatFilterMode = ChatFilterMode.ALL,
    val isChatAutoScroll: Boolean = true,
    val isChatRefreshing: Boolean = false,
    val upcomingBroadcasts: List<LiveBroadcastSummary> = emptyList(),
    val isLoadingBroadcasts: Boolean = false,
    val isUpdatingBroadcast: Boolean = false,
    val isUploadingThumbnail: Boolean = false,
    val isConnectingAccount: Boolean = false,
    val isStartingStream: Boolean = false,
    val errorMessage: String? = null,
    val authSuccessMessage: String? = null,
    val showAuthDialog: Boolean = false,
    val customOAuthClientId: String = "",
    val pendingConsentIntent: Intent? = null,
    val pendingConsentEmail: String? = null,
    val videoAdjustmentConfig: VideoAdjustmentConfig = VideoAdjustmentConfig()
)
