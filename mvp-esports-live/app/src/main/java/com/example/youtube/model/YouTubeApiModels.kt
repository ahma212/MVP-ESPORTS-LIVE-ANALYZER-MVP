package com.example.youtube.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YouTubeChannelListResponse(
    @Json(name = "kind") val kind: String?,
    @Json(name = "items") val items: List<YouTubeChannelItem>?
)

@JsonClass(generateAdapter = true)
data class YouTubeChannelItem(
    @Json(name = "id") val id: String,
    @Json(name = "snippet") val snippet: ChannelSnippet?,
    @Json(name = "statistics") val statistics: ChannelStatistics?,
    @Json(name = "status") val status: ChannelStatus?
)

@JsonClass(generateAdapter = true)
data class ChannelSnippet(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String?,
    @Json(name = "customUrl") val customUrl: String?,
    @Json(name = "thumbnails") val thumbnails: ChannelThumbnails?
)

@JsonClass(generateAdapter = true)
data class ChannelThumbnails(
    @Json(name = "default") val defaultThumb: ThumbnailItem?,
    @Json(name = "medium") val medium: ThumbnailItem?,
    @Json(name = "high") val high: ThumbnailItem?
)

@JsonClass(generateAdapter = true)
data class ThumbnailItem(
    @Json(name = "url") val url: String,
    @Json(name = "width") val width: Int?,
    @Json(name = "height") val height: Int?
)

@JsonClass(generateAdapter = true)
data class ChannelStatistics(
    @Json(name = "viewCount") val viewCount: String?,
    @Json(name = "subscriberCount") val subscriberCount: String?,
    @Json(name = "hiddenSubscriberCount") val hiddenSubscriberCount: Boolean?,
    @Json(name = "videoCount") val videoCount: String?
)

@JsonClass(generateAdapter = true)
data class ChannelStatus(
    @Json(name = "privacyStatus") val privacyStatus: String?,
    @Json(name = "isLinked") val isLinked: Boolean?,
    @Json(name = "longUploadsStatus") val longUploadsStatus: String?,
    @Json(name = "madeForKids") val madeForKids: Boolean?
)

// Live Broadcast Models
@JsonClass(generateAdapter = true)
data class LiveBroadcastResponse(
    @Json(name = "kind") val kind: String?,
    @Json(name = "items") val items: List<LiveBroadcastItem>?
)

@JsonClass(generateAdapter = true)
data class LiveBroadcastItem(
    @Json(name = "id") val id: String,
    @Json(name = "snippet") val snippet: BroadcastSnippet?,
    @Json(name = "status") val status: BroadcastStatus?,
    @Json(name = "contentDetails") val contentDetails: BroadcastContentDetails?
)

@JsonClass(generateAdapter = true)
data class BroadcastSnippet(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String?,
    @Json(name = "scheduledStartTime") val scheduledStartTime: String?,
    @Json(name = "liveChatId") val liveChatId: String?,
    @Json(name = "thumbnails") val thumbnails: ChannelThumbnails?
)

@JsonClass(generateAdapter = true)
data class BroadcastStatus(
    @Json(name = "lifeCycleStatus") val lifeCycleStatus: String?,
    @Json(name = "privacyStatus") val privacyStatus: String?,
    @Json(name = "recordingStatus") val recordingStatus: String?
)

@JsonClass(generateAdapter = true)
data class BroadcastContentDetails(
    @Json(name = "boundStreamId") val boundStreamId: String?,
    @Json(name = "monitorStream") val monitorStream: MonitorStreamDetails?,
    @Json(name = "enableLowLatency") val enableLowLatency: Boolean?
)

@JsonClass(generateAdapter = true)
data class MonitorStreamDetails(
    @Json(name = "enableMonitorStream") val enableMonitorStream: Boolean?,
    @Json(name = "embedHtml") val embedHtml: String?
)

// Live Stream Models (RTMP Ingestion)
@JsonClass(generateAdapter = true)
data class LiveStreamResponse(
    @Json(name = "kind") val kind: String?,
    @Json(name = "items") val items: List<LiveStreamItem>?
)

@JsonClass(generateAdapter = true)
data class LiveStreamItem(
    @Json(name = "id") val id: String,
    @Json(name = "snippet") val snippet: StreamSnippet?,
    @Json(name = "cdn") val cdn: CdnSettings?,
    @Json(name = "status") val status: StreamStatus?
)

@JsonClass(generateAdapter = true)
data class StreamSnippet(
    @Json(name = "title") val title: String
)

@JsonClass(generateAdapter = true)
data class CdnSettings(
    @Json(name = "ingestionType") val ingestionType: String?,
    @Json(name = "ingestionInfo") val ingestionInfo: IngestionInfo?,
    @Json(name = "resolution") val resolution: String?,
    @Json(name = "frameRate") val frameRate: String?
)

@JsonClass(generateAdapter = true)
data class IngestionInfo(
    @Json(name = "streamName") val streamName: String?,
    @Json(name = "ingestionAddress") val ingestionAddress: String?,
    @Json(name = "backupIngestionAddress") val backupIngestionAddress: String?,
    @Json(name = "rtmpsIngestionAddress") val rtmpsIngestionAddress: String?
)

@JsonClass(generateAdapter = true)
data class StreamStatus(
    @Json(name = "streamStatus") val streamStatus: String?
)

// Live Chat API Models
@JsonClass(generateAdapter = true)
data class LiveChatMessageListResponse(
    @Json(name = "kind") val kind: String?,
    @Json(name = "nextPageToken") val nextPageToken: String?,
    @Json(name = "pollingIntervalMillis") val pollingIntervalMillis: Long?,
    @Json(name = "items") val items: List<LiveChatMessageApiItem>?
)

@JsonClass(generateAdapter = true)
data class LiveChatMessageApiItem(
    @Json(name = "id") val id: String,
    @Json(name = "snippet") val snippet: ChatMessageSnippet?,
    @Json(name = "authorDetails") val authorDetails: ChatAuthorDetails?
)

@JsonClass(generateAdapter = true)
data class ChatMessageSnippet(
    @Json(name = "type") val type: String?,
    @Json(name = "liveChatId") val liveChatId: String?,
    @Json(name = "displayMessage") val displayMessage: String?,
    @Json(name = "publishedAt") val publishedAt: String?,
    @Json(name = "textMessageDetails") val textMessageDetails: TextMessageDetails? = null,
    @Json(name = "superChatDetails") val superChatDetails: SuperChatDetails? = null
)

@JsonClass(generateAdapter = true)
data class SuperChatDetails(
    @Json(name = "amountMicros") val amountMicros: String? = null,
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "amountDisplayString") val amountDisplayString: String? = null,
    @Json(name = "userComment") val userComment: String? = null,
    @Json(name = "tier") val tier: Int? = null
)

@JsonClass(generateAdapter = true)
data class ChatAuthorDetails(
    @Json(name = "channelId") val channelId: String?,
    @Json(name = "displayName") val displayName: String?,
    @Json(name = "profileImageUrl") val profileImageUrl: String?,
    @Json(name = "isChatOwner") val isChatOwner: Boolean?,
    @Json(name = "isChatSponsor") val isChatSponsor: Boolean?,
    @Json(name = "isChatModerator") val isChatModerator: Boolean?
)

// Broadcast Creation Models
@JsonClass(generateAdapter = true)
data class CreateBroadcastRequest(
    @Json(name = "snippet") val snippet: CreateBroadcastSnippet,
    @Json(name = "status") val status: CreateBroadcastStatus,
    @Json(name = "contentDetails") val contentDetails: CreateBroadcastContentDetails? = null
)

@JsonClass(generateAdapter = true)
data class CreateBroadcastSnippet(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "scheduledStartTime") val scheduledStartTime: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateBroadcastStatus(
    @Json(name = "privacyStatus") val privacyStatus: String = "public",
    @Json(name = "selfDeclaredMadeForKids") val selfDeclaredMadeForKids: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CreateBroadcastContentDetails(
    @Json(name = "enableAutoStart") val enableAutoStart: Boolean = true,
    @Json(name = "enableAutoStop") val enableAutoStop: Boolean = true,
    @Json(name = "latencyPreference") val latencyPreference: String = "ultraLow"
)

// Stream Creation Models
@JsonClass(generateAdapter = true)
data class CreateStreamRequest(
    @Json(name = "snippet") val snippet: CreateStreamSnippet,
    @Json(name = "cdn") val cdn: CreateStreamCdn
)

@JsonClass(generateAdapter = true)
data class CreateStreamSnippet(
    @Json(name = "title") val title: String
)

@JsonClass(generateAdapter = true)
data class CreateStreamCdn(
    @Json(name = "frameRate") val frameRate: String = "variable",
    @Json(name = "ingestionType") val ingestionType: String = "rtmp",
    @Json(name = "resolution") val resolution: String = "variable"
)

// Broadcast Update Models
@JsonClass(generateAdapter = true)
data class UpdateBroadcastRequest(
    @Json(name = "id") val id: String,
    @Json(name = "snippet") val snippet: CreateBroadcastSnippet,
    @Json(name = "status") val status: CreateBroadcastStatus? = null,
    @Json(name = "contentDetails") val contentDetails: CreateBroadcastContentDetails? = null
)

// Live Chat Message Posting Models
@JsonClass(generateAdapter = true)
data class SendLiveChatMessageRequest(
    @Json(name = "snippet") val snippet: SendLiveChatMessageSnippet
)

@JsonClass(generateAdapter = true)
data class SendLiveChatMessageSnippet(
    @Json(name = "liveChatId") val liveChatId: String,
    @Json(name = "type") val type: String = "textMessageEvent",
    @Json(name = "textMessageDetails") val textMessageDetails: TextMessageDetails
)

@JsonClass(generateAdapter = true)
data class TextMessageDetails(
    @Json(name = "messageText") val messageText: String
)

// Thumbnail Upload Models
@JsonClass(generateAdapter = true)
data class ThumbnailSetResponse(
    @Json(name = "kind") val kind: String?,
    @Json(name = "items") val items: List<ThumbnailSetItem>?
)

@JsonClass(generateAdapter = true)
data class ThumbnailSetItem(
    @Json(name = "default") val defaultThumb: ThumbnailItem?,
    @Json(name = "medium") val medium: ThumbnailItem?,
    @Json(name = "high") val high: ThumbnailItem?
)

