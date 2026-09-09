package com.example.youtube.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.auth.SecureAuthStore
import com.example.model.LatencyMode
import com.example.model.StreamPrivacy
import com.example.model.VideoFps
import com.example.model.VideoResolution
import com.example.youtube.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CreatedBroadcastInfo(
    val broadcastId: String,
    val streamId: String,
    val rtmpIngestUrl: String,
    val streamKey: String,
    val watchUrl: String,
    val liveChatId: String?,
    val title: String,
    val status: String?
)

sealed class YouTubeLiveResult<out T> {
    data class Success<T>(val data: T) : YouTubeLiveResult<T>()
    data class Error(val message: String, val statusCode: Int? = null) : YouTubeLiveResult<Nothing>()
}

class YouTubeLiveManager(
    private val context: Context,
    private val authStore: SecureAuthStore,
    private val apiService: YouTubeApiService = YouTubeClient.apiService
) {
    private val TAG = "YouTubeLiveManager"

    private fun getAuthHeader(): String? {
        val session = authStore.getSession() ?: return null
        return "Bearer ${session.accessToken}"
    }

    /**
     * Creates a real YouTube live broadcast, creates an RTMP ingest stream,
     * binds them together, and optionally uploads a custom thumbnail.
     */
    suspend fun createLiveBroadcast(
        title: String,
        description: String,
        privacy: StreamPrivacy,
        latencyMode: LatencyMode,
        resolution: VideoResolution,
        fps: VideoFps,
        customThumbnailUri: Uri? = null
    ): YouTubeLiveResult<CreatedBroadcastInfo> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext YouTubeLiveResult.Error(
            "Authentication required: Please connect your YouTube channel before creating a broadcast."
        )

        try {
            // 1. Format scheduled start time (UTC ISO 8601)
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val scheduledStartTime = isoFormat.format(Date(System.currentTimeMillis() + 60_000)) // 1 min from now

            val privacyStatus = when (privacy) {
                StreamPrivacy.PUBLIC -> "public"
                StreamPrivacy.UNLISTED -> "unlisted"
                StreamPrivacy.PRIVATE -> "private"
            }

            val latencyPreference = when (latencyMode) {
                LatencyMode.ULTRA_LOW -> "ultraLow"
                LatencyMode.LOW -> "low"
                LatencyMode.NORMAL -> "normal"
            }

            // 2. Create YouTube Broadcast
            val broadcastRequest = CreateBroadcastRequest(
                snippet = CreateBroadcastSnippet(
                    title = title.ifBlank { "MVP ESPORTS LIVE STREAM" },
                    description = description.ifBlank { "Live esports tournament broadcast powered by MVP ESPORTS LIVE." },
                    scheduledStartTime = scheduledStartTime
                ),
                status = CreateBroadcastStatus(
                    privacyStatus = privacyStatus,
                    selfDeclaredMadeForKids = false
                ),
                contentDetails = CreateBroadcastContentDetails(
                    enableAutoStart = true,
                    enableAutoStop = true,
                    latencyPreference = latencyPreference
                )
            )

            Log.i(TAG, "Creating YouTube broadcast: $title, privacy: $privacyStatus, latency: $latencyPreference")
            val broadcastResponse = apiService.createBroadcast(authHeader, request = broadcastRequest)
            if (!broadcastResponse.isSuccessful || broadcastResponse.body() == null) {
                val errorBody = broadcastResponse.errorBody()?.string() ?: ""
                Log.e(TAG, "Failed to create broadcast: HTTP ${broadcastResponse.code()} - $errorBody")
                return@withContext YouTubeLiveResult.Error(
                    "YouTube API Error (${broadcastResponse.code()}): ${parseApiError(errorBody)}",
                    broadcastResponse.code()
                )
            }

            val broadcast = broadcastResponse.body()!!
            val broadcastId = broadcast.id
            val liveChatId = broadcast.snippet?.liveChatId

            // 3. Create YouTube RTMP Stream
            val resString = when (resolution) {
                VideoResolution.RES_720P -> "720p"
                VideoResolution.RES_1080P -> "1080p"
                VideoResolution.RES_1440P -> "1440p"
                VideoResolution.RES_4K -> "2160p"
                else -> "1080p"
            }

            val fpsString = when (fps) {
                VideoFps.FPS_30 -> "30fps"
                VideoFps.FPS_60 -> "60fps"
                else -> "variable"
            }

            val streamRequest = CreateStreamRequest(
                snippet = CreateStreamSnippet(
                    title = "MVP Ingest - ${title.take(30)} - $resString@$fpsString"
                ),
                cdn = CreateStreamCdn(
                    frameRate = fpsString,
                    ingestionType = "rtmp",
                    resolution = resString
                )
            )

            Log.i(TAG, "Creating YouTube RTMP Stream ingest ($resString, $fpsString)")
            val streamResponse = apiService.createStream(authHeader, request = streamRequest)
            if (!streamResponse.isSuccessful || streamResponse.body() == null) {
                val errorBody = streamResponse.errorBody()?.string() ?: ""
                Log.e(TAG, "Failed to create stream: HTTP ${streamResponse.code()} - $errorBody")
                return@withContext YouTubeLiveResult.Error(
                    "Failed to create RTMP ingestion endpoint: ${parseApiError(errorBody)}",
                    streamResponse.code()
                )
            }

            val stream = streamResponse.body()!!
            val streamId = stream.id
            val ingestionInfo = stream.cdn?.ingestionInfo
            val rtmpAddress = ingestionInfo?.ingestionAddress ?: "rtmp://a.rtmp.youtube.com/live2"
            val streamKey = ingestionInfo?.streamName ?: ""

            // 4. Bind Broadcast to Stream
            Log.i(TAG, "Binding broadcast $broadcastId to stream $streamId")
            val bindResponse = apiService.bindBroadcast(
                authHeader = authHeader,
                id = broadcastId,
                streamId = streamId
            )
            if (!bindResponse.isSuccessful) {
                val errorBody = bindResponse.errorBody()?.string() ?: ""
                Log.w(TAG, "Warning: Broadcast bind returned HTTP ${bindResponse.code()}: $errorBody")
            }

            // 5. Upload Custom Thumbnail if provided
            if (customThumbnailUri != null) {
                try {
                    val bytes = context.contentResolver.openInputStream(customThumbnailUri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        Log.i(TAG, "Uploading custom thumbnail (${bytes.size} bytes) for broadcast $broadcastId")
                        val mediaType = (context.contentResolver.getType(customThumbnailUri) ?: "image/jpeg").toMediaTypeOrNull()
                        val requestBody = bytes.toRequestBody(mediaType)
                        val thumbResponse = apiService.setThumbnail(
                            authHeader = authHeader,
                            videoId = broadcastId,
                            imageBody = requestBody
                        )
                        if (thumbResponse.isSuccessful) {
                            Log.i(TAG, "Custom thumbnail applied successfully to YouTube video!")
                        } else {
                            Log.w(TAG, "Thumbnail upload failed HTTP ${thumbResponse.code()}: ${thumbResponse.errorBody()?.string()}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Non-fatal thumbnail upload error: ${e.message}")
                }
            }

            val watchUrl = "https://youtu.be/$broadcastId"
            Log.i(TAG, "YouTube Live Broadcast Created Successfully! Watch URL: $watchUrl, RTMP: $rtmpAddress, Key: [SECRET]")

            YouTubeLiveResult.Success(
                CreatedBroadcastInfo(
                    broadcastId = broadcastId,
                    streamId = streamId,
                    rtmpIngestUrl = rtmpAddress,
                    streamKey = streamKey,
                    watchUrl = watchUrl,
                    liveChatId = liveChatId,
                    title = title,
                    status = broadcast.status?.lifeCycleStatus ?: "ready"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating YouTube broadcast: ${e.message}", e)
            YouTubeLiveResult.Error("Network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Transitions broadcast lifecycle to 'live'.
     */
    suspend fun startLiveBroadcast(broadcastId: String): YouTubeLiveResult<LiveBroadcastItem> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext YouTubeLiveResult.Error("Not authenticated")
        try {
            Log.i(TAG, "Transitioning broadcast $broadcastId to LIVE...")
            val response = apiService.transitionBroadcast(
                authHeader = authHeader,
                broadcastStatus = "live",
                id = broadcastId
            )
            if (response.isSuccessful && response.body() != null) {
                YouTubeLiveResult.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Log.w(TAG, "Transition to live returned HTTP ${response.code()}: $errorBody")
                YouTubeLiveResult.Error("Transition error (${response.code()}): ${parseApiError(errorBody)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transitioning broadcast to live: ${e.message}", e)
            YouTubeLiveResult.Error("Error starting live: ${e.localizedMessage}")
        }
    }

    /**
     * Transitions broadcast lifecycle to 'complete' (ends stream).
     */
    suspend fun endLiveBroadcast(broadcastId: String): YouTubeLiveResult<LiveBroadcastItem> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext YouTubeLiveResult.Error("Not authenticated")
        try {
            Log.i(TAG, "Transitioning broadcast $broadcastId to COMPLETE...")
            val response = apiService.transitionBroadcast(
                authHeader = authHeader,
                broadcastStatus = "complete",
                id = broadcastId
            )
            if (response.isSuccessful && response.body() != null) {
                YouTubeLiveResult.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Log.w(TAG, "Transition to complete returned HTTP ${response.code()}: $errorBody")
                YouTubeLiveResult.Error("Error ending broadcast: ${parseApiError(errorBody)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ending broadcast: ${e.message}", e)
            YouTubeLiveResult.Error("Error ending broadcast: ${e.localizedMessage}")
        }
    }

    /**
     * Fetch incoming live chat messages for an active YouTube Live broadcast.
     */
    suspend fun fetchLiveChatMessages(
        liveChatId: String,
        pageToken: String? = null
    ): YouTubeLiveResult<LiveChatMessageListResponse> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext YouTubeLiveResult.Error(
            "Authentication required to read YouTube live chat.",
            401
        )
        try {
            val response = apiService.listLiveChatMessages(
                authHeader = authHeader,
                liveChatId = liveChatId,
                part = "snippet,authorDetails",
                pageToken = pageToken
            )
            if (response.isSuccessful && response.body() != null) {
                YouTubeLiveResult.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Log.w(TAG, "fetchLiveChatMessages returned HTTP ${response.code()}: $errorBody")
                YouTubeLiveResult.Error(
                    parseApiError(errorBody).ifBlank { "Failed to load live chat (HTTP ${response.code()})" },
                    response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching live chat messages: ${e.message}", e)
            YouTubeLiveResult.Error("Network error reading live chat: ${e.localizedMessage}")
        }
    }

    /**
     * Post a new chat message into YouTube Live Chat.
     */
    suspend fun sendLiveChatMessage(
        liveChatId: String,
        messageText: String
    ): YouTubeLiveResult<LiveChatMessageApiItem> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext YouTubeLiveResult.Error(
            "Authentication required to send chat messages.",
            401
        )
        if (messageText.isBlank()) {
            return@withContext YouTubeLiveResult.Error("Cannot send an empty chat message.")
        }
        try {
            val request = SendLiveChatMessageRequest(
                snippet = SendLiveChatMessageSnippet(
                    liveChatId = liveChatId,
                    type = "textMessageEvent",
                    textMessageDetails = TextMessageDetails(messageText = messageText.trim())
                )
            )
            val response = apiService.insertLiveChatMessage(
                authHeader = authHeader,
                part = "snippet",
                request = request
            )
            if (response.isSuccessful && response.body() != null) {
                YouTubeLiveResult.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Log.w(TAG, "sendLiveChatMessage returned HTTP ${response.code()}: $errorBody")
                YouTubeLiveResult.Error(
                    parseApiError(errorBody).ifBlank { "Failed to post message (HTTP ${response.code()})" },
                    response.code()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending live chat message: ${e.message}", e)
            YouTubeLiveResult.Error("Network error posting chat: ${e.localizedMessage}")
        }
    }

    /**
     * Updates an existing live broadcast's title, description, and privacy on YouTube API.
     */
    suspend fun updateLiveBroadcast(
        broadcastId: String,
        title: String,
        description: String,
        privacy: StreamPrivacy? = null
    ): YouTubeLiveResult<LiveBroadcastItem> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext YouTubeLiveResult.Error("Not authenticated", 401)
        try {
            val privacyStatus = privacy?.let {
                when (it) {
                    StreamPrivacy.PUBLIC -> "public"
                    StreamPrivacy.UNLISTED -> "unlisted"
                    StreamPrivacy.PRIVATE -> "private"
                }
            }

            val request = UpdateBroadcastRequest(
                id = broadcastId,
                snippet = CreateBroadcastSnippet(
                    title = title.ifBlank { "MVP ESPORTS LIVE STREAM" },
                    description = description
                ),
                status = privacyStatus?.let { CreateBroadcastStatus(privacyStatus = it) }
            )

            val response = apiService.updateBroadcast(
                authHeader = authHeader,
                part = "snippet,status,contentDetails",
                request = request
            )
            if (response.isSuccessful && response.body() != null) {
                YouTubeLiveResult.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Log.w(TAG, "updateLiveBroadcast returned HTTP ${response.code()}: $errorBody")
                YouTubeLiveResult.Error(parseApiError(errorBody), response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating broadcast: ${e.message}", e)
            YouTubeLiveResult.Error("Error updating broadcast: ${e.localizedMessage}")
        }
    }

    /**
     * Lists upcoming or active broadcasts for the authenticated channel.
     */
    suspend fun listUpcomingBroadcasts(): YouTubeLiveResult<List<LiveBroadcastItem>> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext YouTubeLiveResult.Error("Not authenticated", 401)
        try {
            val response = apiService.listMyBroadcasts(
                authHeader = authHeader,
                part = "snippet,status,contentDetails",
                broadcastStatus = "all",
                maxResults = 15
            )
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.items ?: emptyList()
                YouTubeLiveResult.Success(items)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                YouTubeLiveResult.Error(parseApiError(errorBody), response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing broadcasts: ${e.message}", e)
            YouTubeLiveResult.Error("Error loading broadcasts: ${e.localizedMessage}")
        }
    }

    /**
     * Uploads and sets custom thumbnail for a broadcast video ID.
     */
    suspend fun uploadThumbnail(broadcastId: String, imageUri: Uri): YouTubeLiveResult<Boolean> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext YouTubeLiveResult.Error("Not authenticated", 401)
        try {
            val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                return@withContext YouTubeLiveResult.Error("Could not read thumbnail image data.")
            }
            val mediaType = (context.contentResolver.getType(imageUri) ?: "image/jpeg").toMediaTypeOrNull()
            val requestBody = bytes.toRequestBody(mediaType)
            val response = apiService.setThumbnail(
                authHeader = authHeader,
                videoId = broadcastId,
                imageBody = requestBody
            )
            if (response.isSuccessful) {
                YouTubeLiveResult.Success(true)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                YouTubeLiveResult.Error(parseApiError(errorBody), response.code())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading thumbnail: ${e.message}", e)
            YouTubeLiveResult.Error("Error uploading thumbnail: ${e.localizedMessage}")
        }
    }

    /**
     * Fetches detailed broadcast information including liveChatId.
     */
    suspend fun getBroadcastDetails(broadcastId: String): YouTubeLiveResult<LiveBroadcastItem> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext YouTubeLiveResult.Error("Not authenticated", 401)
        try {
            val response = apiService.getBroadcast(authHeader, id = broadcastId)
            if (response.isSuccessful) {
                val item = response.body()?.items?.firstOrNull()
                if (item != null) {
                    YouTubeLiveResult.Success(item)
                } else {
                    YouTubeLiveResult.Error("Broadcast $broadcastId not found on YouTube.")
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                YouTubeLiveResult.Error(parseApiError(errorBody), response.code())
            }
        } catch (e: Exception) {
            YouTubeLiveResult.Error("Error fetching broadcast details: ${e.localizedMessage}")
        }
    }

    /**
     * Checks real broadcast status from YouTube API.
     */
    suspend fun pollBroadcastStatus(broadcastId: String): String? = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext null
        try {
            val response = apiService.getBroadcast(authHeader, id = broadcastId)
            if (response.isSuccessful) {
                response.body()?.items?.firstOrNull()?.status?.lifeCycleStatus
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks RTMP ingestion health from YouTube API.
     */
    suspend fun pollStreamHealth(streamId: String): String? = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader() ?: return@withContext null
        try {
            val response = apiService.getStream(authHeader, id = streamId)
            if (response.isSuccessful) {
                response.body()?.items?.firstOrNull()?.status?.streamStatus
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseApiError(errorJson: String): String {
        return try {
            if (errorJson.contains("\"message\":")) {
                val msgStart = errorJson.indexOf("\"message\":") + 10
                val openQuote = errorJson.indexOf("\"", msgStart)
                val closeQuote = errorJson.indexOf("\"", openQuote + 1)
                if (openQuote != -1 && closeQuote != -1) {
                    errorJson.substring(openQuote + 1, closeQuote)
                } else errorJson.take(150)
            } else errorJson.take(150)
        } catch (_: Exception) {
            errorJson.take(150)
        }
    }
}
