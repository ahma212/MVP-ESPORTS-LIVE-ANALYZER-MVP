package com.example.youtube.service

import com.example.youtube.model.CreateBroadcastRequest
import com.example.youtube.model.CreateStreamRequest
import com.example.youtube.model.LiveBroadcastItem
import com.example.youtube.model.LiveBroadcastResponse
import com.example.youtube.model.LiveChatMessageApiItem
import com.example.youtube.model.LiveChatMessageListResponse
import com.example.youtube.model.LiveStreamItem
import com.example.youtube.model.LiveStreamResponse
import com.example.youtube.model.SendLiveChatMessageRequest
import com.example.youtube.model.ThumbnailSetResponse
import com.example.youtube.model.UpdateBroadcastRequest
import com.example.youtube.model.YouTubeChannelListResponse
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface YouTubeApiService {

    /**
     * Retrieve the authenticated user's real YouTube channel.
     */
    @GET("channels")
    suspend fun getMyChannel(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet,statistics,status",
        @Query("mine") mine: Boolean = true
    ): Response<YouTubeChannelListResponse>

    /**
     * Create a real YouTube Live Broadcast.
     */
    @POST("liveBroadcasts")
    suspend fun createBroadcast(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet,status,contentDetails",
        @Body request: CreateBroadcastRequest
    ): Response<LiveBroadcastItem>

    /**
     * Update an existing YouTube Live Broadcast (e.g., title, description, privacy).
     */
    @PUT("liveBroadcasts")
    suspend fun updateBroadcast(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet,status,contentDetails",
        @Body request: UpdateBroadcastRequest
    ): Response<LiveBroadcastItem>

    /**
     * Delete a YouTube Live Broadcast by ID.
     */
    @DELETE("liveBroadcasts")
    suspend fun deleteBroadcast(
        @Header("Authorization") authHeader: String,
        @Query("id") id: String
    ): Response<ResponseBody>

    /**
     * Create a real YouTube Live Stream (Ingestion destination).
     */
    @POST("liveStreams")
    suspend fun createStream(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet,cdn,status",
        @Body request: CreateStreamRequest
    ): Response<LiveStreamItem>

    /**
     * Bind a live broadcast to a live stream ingestion endpoint.
     */
    @POST("liveBroadcasts/bind")
    suspend fun bindBroadcast(
        @Header("Authorization") authHeader: String,
        @Query("id") id: String,
        @Query("part") part: String = "id,snippet,contentDetails,status",
        @Query("streamId") streamId: String
    ): Response<LiveBroadcastItem>

    /**
     * Upload and set a custom thumbnail for the YouTube live broadcast video.
     */
    @POST("https://www.googleapis.com/upload/youtube/v3/thumbnails/set")
    suspend fun setThumbnail(
        @Header("Authorization") authHeader: String,
        @Query("videoId") videoId: String,
        @Body imageBody: RequestBody
    ): Response<ThumbnailSetResponse>

    /**
     * Get real-time status of a live broadcast.
     */
    @GET("liveBroadcasts")
    suspend fun getBroadcast(
        @Header("Authorization") authHeader: String,
        @Query("id") id: String,
        @Query("part") part: String = "snippet,status,contentDetails"
    ): Response<LiveBroadcastResponse>

    /**
     * Get real-time ingestion status and stream health of a live stream.
     */
    @GET("liveStreams")
    suspend fun getStream(
        @Header("Authorization") authHeader: String,
        @Query("id") id: String,
        @Query("part") part: String = "snippet,cdn,status"
    ): Response<LiveStreamResponse>

    /**
     * List user's active/upcoming live broadcasts.
     */
    @GET("liveBroadcasts")
    suspend fun listMyBroadcasts(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet,status,contentDetails",
        @Query("broadcastStatus") broadcastStatus: String = "upcoming",
        @Query("maxResults") maxResults: Int = 10
    ): Response<LiveBroadcastResponse>

    /**
     * List live streams (RTMP ingestion destinations).
     */
    @GET("liveStreams")
    suspend fun listMyStreams(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet,cdn,status",
        @Query("mine") mine: Boolean = true
    ): Response<LiveStreamResponse>

    /**
     * List live chat messages for an active broadcast liveChatId.
     */
    @GET("liveChat/messages")
    suspend fun listLiveChatMessages(
        @Header("Authorization") authHeader: String,
        @Query("liveChatId") liveChatId: String,
        @Query("part") part: String = "snippet,authorDetails",
        @Query("pageToken") pageToken: String? = null
    ): Response<LiveChatMessageListResponse>

    /**
     * Post a new message or tournament announcement into live chat.
     */
    @POST("liveChat/messages")
    suspend fun insertLiveChatMessage(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet",
        @Body request: SendLiveChatMessageRequest
    ): Response<LiveChatMessageApiItem>

    /**
     * Transition broadcast lifecycle (e.g. testing, live, complete).
     */
    @POST("liveBroadcasts/transition")
    suspend fun transitionBroadcast(
        @Header("Authorization") authHeader: String,
        @Query("broadcastStatus") broadcastStatus: String,
        @Query("id") id: String,
        @Query("part") part: String = "status"
    ): Response<LiveBroadcastItem>
}

object YouTubeClient {
    private const val BASE_URL = "https://www.googleapis.com/youtube/v3/"

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val apiService: YouTubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(YouTubeApiService::class.java)
    }
}
