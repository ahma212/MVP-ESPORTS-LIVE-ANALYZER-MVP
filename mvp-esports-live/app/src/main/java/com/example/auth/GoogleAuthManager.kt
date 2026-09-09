package com.example.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.youtube.service.YouTubeClient
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class AuthResult {
    data class Success(val session: AuthSession) : AuthResult()
    data class NeedsUserConsent(val intent: Intent, val accountEmail: String) : AuthResult()
    data class Error(val message: String, val throwable: Throwable? = null) : AuthResult()
    object Cancelled : AuthResult()
}

class GoogleAuthManager(
    private val context: Context,
    private val authStore: SecureAuthStore
) {
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    companion object {
        const val YOUTUBE_SCOPE_FULL = "https://www.googleapis.com/auth/youtube"
        const val YOUTUBE_SCOPE_FORCE_SSL = "https://www.googleapis.com/auth/youtube.force-ssl"
        const val YOUTUBE_OAUTH_SCOPE_STRING = "oauth2:$YOUTUBE_SCOPE_FULL $YOUTUBE_SCOPE_FORCE_SSL"
    }

    /**
     * Signs in using Android Credential Manager with Google ID, then obtains
     * the real OAuth 2.0 access token required for YouTube Data API v3.
     */
    suspend fun signInWithGoogle(
        activityContext: Context,
        serverClientId: String? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        val targetClientId = serverClientId?.takeIf { it.isNotBlank() }
            ?: authStore.getCustomOAuthClientId()?.takeIf { it.isNotBlank() }
            ?: "1028741355476-cjhkt7d29h6ksvj893e4g83b7o2a1ln8.apps.googleusercontent.com" // Default OAuth 2.0 Web Client ID

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(targetClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id

                // Step 2: Now obtain OAuth 2.0 Access Token with YouTube scopes for this Google account
                return@withContext obtainOAuth2AccessTokenAndConnect(
                    activityContext = activityContext,
                    accountEmail = email
                )
            } else {
                return@withContext AuthResult.Error("Unsupported credential type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            return@withContext AuthResult.Cancelled
        } catch (e: GetCredentialException) {
            return@withContext AuthResult.Error(
                message = "Credential Manager Sign-In failed: ${e.message ?: "Authentication error"}. Verify your Google Web Client ID or enter an OAuth Access Token directly.",
                throwable = e
            )
        } catch (e: Exception) {
            return@withContext AuthResult.Error(
                message = "Sign-in error: ${e.localizedMessage ?: "Unexpected error"}",
                throwable = e
            )
        }
    }

    /**
     * Obtains the OAuth 2.0 Access Token with YouTube scopes using Google Play Services AuthorizationClient
     * or GoogleAuthUtil, and connects the user's YouTube Channel.
     */
    suspend fun obtainOAuth2AccessTokenAndConnect(
        activityContext: Context,
        accountEmail: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            // First attempt: Try Google Play Services Identity AuthorizationClient
            val authClient = Identity.getAuthorizationClient(activityContext)
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(
                    listOf(
                        Scope(YOUTUBE_SCOPE_FULL),
                        Scope(YOUTUBE_SCOPE_FORCE_SSL)
                    )
                )
                .setAccount(Account(accountEmail, "com.google"))
                .build()

            try {
                val authResult = authClient.authorize(authRequest).awaitTask()
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent != null) {
                        return@withContext AuthResult.NeedsUserConsent(
                            intent = pendingIntent.intentSender.let { Intent().putExtra("intentSender", it) },
                            accountEmail = accountEmail
                        )
                    }
                }

                val token = authResult.accessToken
                if (!token.isNullOrBlank()) {
                    return@withContext connectYouTubeChannelWithToken(
                        accountEmail = accountEmail,
                        accessToken = token
                    )
                }
            } catch (_: Exception) {
                // Fall back to GoogleAuthUtil if AuthorizationClient is unavailable or fails
            }

            // Second attempt: GoogleAuthUtil.getToken
            try {
                val account = Account(accountEmail, "com.google")
                val token = GoogleAuthUtil.getToken(
                    activityContext,
                    account,
                    YOUTUBE_OAUTH_SCOPE_STRING
                )

                if (!token.isNullOrBlank()) {
                    return@withContext connectYouTubeChannelWithToken(
                        accountEmail = accountEmail,
                        accessToken = token
                    )
                } else {
                    return@withContext AuthResult.Error("Failed to obtain OAuth 2.0 access token for $accountEmail.")
                }
            } catch (userRecoverable: UserRecoverableAuthException) {
                val consentIntent = userRecoverable.intent
                return@withContext if (consentIntent != null) {
                    AuthResult.NeedsUserConsent(
                        intent = consentIntent,
                        accountEmail = accountEmail
                    )
                } else {
                    AuthResult.Error("Google authorization consent required for $accountEmail. Please review permissions.")
                }
            } catch (authEx: GoogleAuthException) {
                return@withContext AuthResult.Error(
                    message = "Google Play Services OAuth error: ${authEx.localizedMessage}. You may paste an OAuth Access Token directly.",
                    throwable = authEx
                )
            }
        } catch (e: Exception) {
            return@withContext AuthResult.Error(
                message = "Failed to obtain OAuth 2.0 token: ${e.localizedMessage ?: "Unknown error"}",
                throwable = e
            )
        }
    }

    /**
     * Connects user's real YouTube channel using an OAuth access token
     * and queries the YouTube Data API v3 'channels' endpoint.
     */
    suspend fun connectYouTubeChannelWithToken(
        accountEmail: String,
        accessToken: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val response = YouTubeClient.apiService.getMyChannel(
                authHeader = "Bearer $accessToken"
            )

            if (response.isSuccessful) {
                val body = response.body()
                val items = body?.items
                if (items.isNullOrEmpty()) {
                    return@withContext AuthResult.Error(
                        "No YouTube channel associated with this Google account ($accountEmail). Please create a channel on YouTube first."
                    )
                }

                val channel = items.first()
                val snippet = channel.snippet
                val stats = channel.statistics
                val status = channel.status

                val avatarUrl = snippet?.thumbnails?.high?.url
                    ?: snippet?.thumbnails?.medium?.url
                    ?: snippet?.thumbnails?.defaultThumb?.url

                val session = AuthSession(
                    accountEmail = accountEmail,
                    accessToken = accessToken,
                    tokenExpiryEpochMs = System.currentTimeMillis() + (3600 * 1000), // 1 hour typical
                    channelId = channel.id,
                    channelTitle = snippet?.title ?: "YouTube Creator",
                    channelHandle = snippet?.customUrl ?: "@${snippet?.title?.replace(" ", "")?.lowercase()}",
                    channelAvatarUrl = avatarUrl,
                    subscriberCount = formatSubscribers(stats?.subscriberCount),
                    videoCount = stats?.videoCount ?: "0",
                    isLiveStreamingEnabled = status?.isLinked ?: true
                )

                authStore.saveSession(session)
                return@withContext AuthResult.Success(session)
            } else {
                val errorMsg = when (response.code()) {
                    401 -> "Authentication error (401): The OAuth 2.0 access token is expired or unauthorized."
                    403 -> "Permissions error (403): YouTube Data API access is restricted. Ensure YouTube Live Streaming is enabled on your channel."
                    404 -> "Channel not found (404)."
                    else -> "YouTube API error HTTP ${response.code()}: ${response.message()}"
                }
                return@withContext AuthResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            return@withContext AuthResult.Error(
                message = "Network error communicating with YouTube API: ${e.localizedMessage}",
                throwable = e
            )
        }
    }

    /**
     * Disconnects current session and clears Credential Manager cache.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        authStore.clearSession()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {}
    }

    private fun formatSubscribers(countStr: String?): String {
        if (countStr == null) return "0 Subscribers"
        val count = countStr.toLongOrNull() ?: return "$countStr Subs"
        return when {
            count >= 1_000_000 -> String.format("%.1fM Subscribers", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK Subscribers", count / 1_000.0)
            else -> "$count Subscribers"
        }
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
