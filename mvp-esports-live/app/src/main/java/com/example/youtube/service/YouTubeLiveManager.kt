package com.example.youtube.service

import android.content.Context
import android.net.Uri
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import com.example.auth.SecureAuthStore
import com.example.auth.GoogleAuthManager
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
    private val authManager: GoogleAuthManager,
    private val apiService: YouTubeApiService = YouTubeClient.apiService
) {
    private val TAG = "YouTubeLiveManager"

    private suspend fun getAuthHeader(): String? {
    val accessToken = authManager.getValidAccessToken()
        ?: return null

    return "Bearer $accessToken"
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
            val rtmpAddress = ingestionInfo?.ingestionAddress
            val streamKey = ingestionInfo?.streamName

            if (rtmpAddress.isNullOrBlank() || streamKey.isNullOrBlank()) {
                Log.e(TAG, "YouTube did not return valid RTMP ingestion info")
                return@withContext YouTubeLiveResult.Error(
                    "YouTube did not return a valid RTMP ingest URL or stream key."
                )
            }

            // 4. Bind Broadcast to Stream
            Log.i(TAG, "Binding broadcast $broadcastId to stream $streamId")
            val bindResponse = apiService.bindBroadcast(
                authHeader = authHeader,
                id = broadcastId,
                streamId = streamId
            )
            if (!bindResponse.isSuccessful) {
                val errorBody = bindResponse.errorBody()?.string() ?: ""
                Log.e(TAG, "Broadcast bind failed: HTTP ${bindResponse.code()} - $errorBody")
                return@withContext YouTubeLiveResult.Error(
                    "Failed to bind broadcast to stream: ${parseApiError(errorBody)}",
                    bindResponse.code()
                )
            }

            // 5. Upload Custom Thumbnail if provided (non-fatal)
            if (customThumbnailUri != null) {
                when (val thumbnailResult = uploadThumbnail(broadcastId, customThumbnailUri)) {
                    is YouTubeLiveResult.Success -> {
                        Log.i(TAG, "Custom thumbnail applied successfully")
                    }
                    is YouTubeLiveResult.Error -> {
                        Log.w(TAG, "Custom thumbnail upload failed: ${thumbnailResult.message}")
                    }
                }
            }

            val watchUrl = "https://youtu.be/$broadcastId"
            Log.i(TAG, "Broadcast created successfully. ID=$broadcastId StreamID=$streamId")

            return@withContext YouTubeLiveResult.Success(
                CreatedBroadcastInfo(
                    broadcastId = broadcastId,
                    streamId = streamId,
                    rtmpIngestUrl = rtmpAddress,
                    streamKey = streamKey,
                    watchUrl = watchUrl,
                    liveChatId = liveChatId,
                    title = title,
                    status = broadcast.status?.lifeCycleStatus
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating live broadcast: ${e.message}", e)
            return@withContext YouTubeLiveResult.Error(
                "Error creating live broadcast: ${e.localizedMessage}"
            )
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
 * Uploads and sets a custom thumbnail for a YouTube live broadcast.
 *
 * YouTube custom-thumbnail API accepts JPEG/PNG and has a 2 MB upload limit.
 * For images that are too large or use another image MIME type, the image is
 * decoded and safely re-encoded as JPEG while preserving its aspect ratio.
 */
suspend fun uploadThumbnail(
    broadcastId: String,
    imageUri: Uri
): YouTubeLiveResult<ThumbnailSetResponse> = withContext(Dispatchers.IO) {
    val authHeader = getAuthHeader()
        ?: return@withContext YouTubeLiveResult.Error(
            "Not authenticated. Please connect your YouTube channel first.",
            401
        )

    if (broadcastId.isBlank()) {
        return@withContext YouTubeLiveResult.Error(
            "Invalid YouTube broadcast ID."
        )
    }

    try {
        val prepared = prepareThumbnailForYouTube(imageUri)
            ?: return@withContext YouTubeLiveResult.Error(
                "Could not prepare the selected thumbnail. Please choose a valid JPG or PNG image."
            )

        val requestBody = prepared.bytes.toRequestBody(
            prepared.mimeType.toMediaTypeOrNull()
        )

        Log.i(
            TAG,
            "Uploading custom thumbnail: ${prepared.bytes.size} bytes, " +
                "type=${prepared.mimeType}, videoId=$broadcastId"
        )

        val response = apiService.setThumbnail(
            authHeader = authHeader,
            videoId = broadcastId,
            imageBody = requestBody
        )

        if (response.isSuccessful && response.body() != null) {
            YouTubeLiveResult.Success(response.body()!!)
        } else {
            val errorBody = response.errorBody()?.string().orEmpty()
            val apiError = parseApiError(errorBody)

            Log.w(
                TAG,
                "Thumbnail upload failed: HTTP ${response.code()} - $apiError"
            )

            YouTubeLiveResult.Error(
                apiError.ifBlank {
                    "YouTube rejected the thumbnail upload (HTTP ${response.code()})."
                },
                response.code()
            )
        }
    } catch (e: SecurityException) {
        Log.e(TAG, "Thumbnail permission error.", e)
        YouTubeLiveResult.Error(
            "The selected image cannot be accessed. Please select the thumbnail again."
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error uploading thumbnail.", e)
        YouTubeLiveResult.Error(
            "Error uploading thumbnail: ${e.localizedMessage ?: "Unknown error"}"
        )
    }
}

/**
 * Prepares a thumbnail that is compatible with YouTube's mobile upload limits.
 *
 * Strategy:
 * 1. Keep an already-valid JPEG/PNG untouched when it is within the 2 MB limit.
 * 2. Otherwise decode the image and encode it as JPEG.
 * 3. Reduce JPEG quality and, if required, resolution until it fits safely
 *    below YouTube's 2 MB API limit.
 *
 * The image is never intentionally cropped, so its original composition is
 * preserved. Non-16:9 images may be resized by YouTube according to its own
 * thumbnail processing rules.
 */
private fun prepareThumbnailForYouTube(
    imageUri: Uri
): PreparedThumbnail? {
    val resolver = context.contentResolver
    val reportedMimeType = resolver.getType(imageUri)?.lowercase(Locale.US)

    // YouTube's thumbnail upload API has a 2 MB maximum.
    val maxUploadBytes = (2 * 1024 * 1024) - 4096

    // Fast path: preserve a valid JPEG/PNG exactly when already small enough.
    if (reportedMimeType == "image/jpeg" || reportedMimeType == "image/png") {
        val originalBytes = resolver.openInputStream(imageUri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0

            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break

                total += read
                if (total > maxUploadBytes) {
                    return@use null
                }

                output.write(buffer, 0, read)
            }

            output.toByteArray()
        }

        if (originalBytes != null && originalBytes.isNotEmpty()) {
            return PreparedThumbnail(
                bytes = originalBytes,
                mimeType = reportedMimeType
            )
        }
    }

    // Decode bounds first so very large images are not decoded at full size.
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    resolver.openInputStream(imageUri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxDimension = 3840
        )
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val bitmap = resolver.openInputStream(imageUri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: return null

    if (bitmap.width <= 0 || bitmap.height <= 0) {
        bitmap.recycle()
        return null
    }

    val compressedBytes = compressBitmapToLimit(
        source = bitmap,
        maxBytes = maxUploadBytes
    )

    bitmap.recycle()

    return compressedBytes?.let {
        PreparedThumbnail(
            bytes = it,
            mimeType = "image/jpeg"
        )
    }
}

/**
 * Chooses a safe BitmapFactory sample size so large source images do not
 * unnecessarily consume excessive memory.
 */
private fun calculateInSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int
): Int {
    var sampleSize = 1

    while (
        width / (sampleSize * 2) >= maxDimension &&
        height / (sampleSize * 2) >= maxDimension
    ) {
        sampleSize *= 2
    }

    return sampleSize.coerceAtLeast(1)
}

/**
 * Compresses the source image while preserving its aspect ratio and avoiding
 * cropping. The function aims to stay below YouTube's 2 MB upload limit.
 */
private fun compressBitmapToLimit(
    source: Bitmap,
    maxBytes: Int
): ByteArray? {
    var workingBitmap = source
    var currentWidth = source.width
    var currentHeight = source.height

    repeat(5) {
        val qualities = intArrayOf(95, 90, 85, 80, 75, 70, 65, 60, 55, 50, 45)

        for (quality in qualities) {
            val output = ByteArrayOutputStream()

            if (
                !workingBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    quality,
                    output
                )
            ) {
                continue
            }

            val bytes = output.toByteArray()

            if (bytes.size <= maxBytes) {
                return bytes
            }
        }

        val nextWidth = (currentWidth * 0.82f).toInt().coerceAtLeast(640)
        val nextHeight = (currentHeight * 0.82f).toInt().coerceAtLeast(360)

        if (
            nextWidth == currentWidth &&
            nextHeight == currentHeight
        ) {
            return null
        }

        val resized = Bitmap.createScaledBitmap(
            workingBitmap,
            nextWidth,
            nextHeight,
            true
        )

        if (workingBitmap !== source) {
            workingBitmap.recycle()
        }

        workingBitmap = resized
        currentWidth = nextWidth
        currentHeight = nextHeight
    }

    if (workingBitmap !== source) {
        workingBitmap.recycle()
    }

    return null
}

private data class PreparedThumbnail(
    val bytes: ByteArray,
    val mimeType: String
)
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
