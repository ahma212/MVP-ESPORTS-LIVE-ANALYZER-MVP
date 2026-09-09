package com.example.auth

import android.content.Context
import android.content.SharedPreferences

data class AuthSession(
    val accountEmail: String,
    val accessToken: String,
    val tokenExpiryEpochMs: Long,
    val channelId: String?,
    val channelTitle: String?,
    val channelHandle: String?,
    val channelAvatarUrl: String?,
    val subscriberCount: String?,
    val videoCount: String?,
    val isLiveStreamingEnabled: Boolean
) {
    val isTokenExpired: Boolean
        get() = System.currentTimeMillis() >= tokenExpiryEpochMs
}

class SecureAuthStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "mvp_esports_auth_secure_store",
        Context.MODE_PRIVATE
    )

    fun saveSession(session: AuthSession) {
        prefs.edit()
            .putString(KEY_ACCOUNT_EMAIL, session.accountEmail)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putLong(KEY_EXPIRY_EPOCH_MS, session.tokenExpiryEpochMs)
            .putString(KEY_CHANNEL_ID, session.channelId)
            .putString(KEY_CHANNEL_TITLE, session.channelTitle)
            .putString(KEY_CHANNEL_HANDLE, session.channelHandle)
            .putString(KEY_CHANNEL_AVATAR, session.channelAvatarUrl)
            .putString(KEY_SUB_COUNT, session.subscriberCount)
            .putString(KEY_VIDEO_COUNT, session.videoCount)
            .putBoolean(KEY_LIVE_STREAM_ENABLED, session.isLiveStreamingEnabled)
            .apply()
    }

    fun getSession(): AuthSession? {
        val email = prefs.getString(KEY_ACCOUNT_EMAIL, null) ?: return null
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val expiry = prefs.getLong(KEY_EXPIRY_EPOCH_MS, 0L)

        return AuthSession(
            accountEmail = email,
            accessToken = token,
            tokenExpiryEpochMs = expiry,
            channelId = prefs.getString(KEY_CHANNEL_ID, null),
            channelTitle = prefs.getString(KEY_CHANNEL_TITLE, null),
            channelHandle = prefs.getString(KEY_CHANNEL_HANDLE, null),
            channelAvatarUrl = prefs.getString(KEY_CHANNEL_AVATAR, null),
            subscriberCount = prefs.getString(KEY_SUB_COUNT, null),
            videoCount = prefs.getString(KEY_VIDEO_COUNT, null),
            isLiveStreamingEnabled = prefs.getBoolean(KEY_LIVE_STREAM_ENABLED, false)
        )
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCOUNT_EMAIL)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_EXPIRY_EPOCH_MS)
            .remove(KEY_CHANNEL_ID)
            .remove(KEY_CHANNEL_TITLE)
            .remove(KEY_CHANNEL_HANDLE)
            .remove(KEY_CHANNEL_AVATAR)
            .remove(KEY_SUB_COUNT)
            .remove(KEY_VIDEO_COUNT)
            .remove(KEY_LIVE_STREAM_ENABLED)
            .apply()
    }

    fun getCustomOAuthClientId(): String? {
        return prefs.getString(KEY_CUSTOM_CLIENT_ID, null)
    }

    fun saveCustomOAuthClientId(clientId: String) {
        prefs.edit().putString(KEY_CUSTOM_CLIENT_ID, clientId.trim()).apply()
    }

    companion object {
        private const val KEY_ACCOUNT_EMAIL = "auth_account_email"
        private const val KEY_ACCESS_TOKEN = "auth_access_token"
        private const val KEY_EXPIRY_EPOCH_MS = "auth_token_expiry"
        private const val KEY_CHANNEL_ID = "yt_channel_id"
        private const val KEY_CHANNEL_TITLE = "yt_channel_title"
        private const val KEY_CHANNEL_HANDLE = "yt_channel_handle"
        private const val KEY_CHANNEL_AVATAR = "yt_channel_avatar"
        private const val KEY_SUB_COUNT = "yt_sub_count"
        private const val KEY_VIDEO_COUNT = "yt_video_count"
        private const val KEY_LIVE_STREAM_ENABLED = "yt_live_stream_enabled"
        private const val KEY_CUSTOM_CLIENT_ID = "oauth_custom_client_id"
    }
}
