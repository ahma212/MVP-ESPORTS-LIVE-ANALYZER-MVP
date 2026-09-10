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

    data class NeedsUserConsent(
        val intent: Intent,
        val accountEmail: String
    ) : AuthResult()

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : AuthResult()

    object Cancelled : AuthResult()
}

class GoogleAuthManager(
    private val context: Context,
    private val authStore: SecureAuthStore
) {

    private val credentialManager: CredentialManager =
        CredentialManager.create(context)

    companion object {
        const val YOUTUBE_SCOPE_FULL =
            "https://www.googleapis.com/auth/youtube"

        const val YOUTUBE_SCOPE_FORCE_SSL =
            "https://www.googleapis.com/auth/youtube.force-ssl"

        /*
         * This is the OAuth Web Client ID used by Google Credential Manager
         * to obtain the Google ID token.
         *
         * IMPORTANT:
         * This must match the Web application OAuth client configured
         * in your Google Cloud project.
         */
        private const val GOOGLE_WEB_CLIENT_ID =
            "1028741355476-cjhkt7d29h6ksvj893e4g83b7o2a1ln8.apps.googleusercontent.com"

        private const val EXTRA_CONSENT_INTENT_SENDER =
            "mvp_esports_youtube_consent_intent_sender"
    }

    /**
     * Step 1:
     * Sign in to the user's Google account using Credential Manager.
     *
     * Step 2:
     * Use the signed-in Google account to request YouTube OAuth scopes.
     */
    suspend fun signInWithGoogle(
        activityContext: Context,
        serverClientId: String? = null
    ): AuthResult = withContext(Dispatchers.IO) {

        val clientId = serverClientId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: authStore.getCustomOAuthClientId()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: GOOGLE_WEB_CLIENT_ID

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
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

            if (
                credential is CustomCredential &&
                credential.type ==
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {

                val googleCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)

                val accountEmail = googleCredential.id.trim()

                if (accountEmail.isBlank()) {
                    return@withContext AuthResult.Error(
                        "Google Sign-In completed, but the Google account email could not be read."
                    )
                }

                return@withContext obtainOAuth2AccessTokenAndConnect(
                    activityContext = activityContext,
                    accountEmail = accountEmail
                )
            }

            return@withContext AuthResult.Error(
                "Google Sign-In returned an unsupported credential type."
            )

        } catch (e: GetCredentialCancellationException) {
            return@withContext AuthResult.Cancelled

        } catch (e: GetCredentialException) {
            return@withContext AuthResult.Error(
                message = buildCredentialErrorMessage(e),
                throwable = e
            )

        } catch (e: Exception) {
            return@withContext AuthResult.Error(
                message = "Google Sign-In failed: ${
                    e.localizedMessage ?: "Unknown error"
                }",
                throwable = e
            )
        }
    }

    /**
     * Request the real YouTube OAuth permissions for the selected
     * Google account.
     *
     * On first use Google may show a consent screen. In that case
     * NeedsUserConsent is returned and the calling UI must launch the
     * returned PendingIntent/IntentSender.
     */
    suspend fun obtainOAuth2AccessTokenAndConnect(
        activityContext: Context,
        accountEmail: String
    ): AuthResult = withContext(Dispatchers.IO) {

        if (accountEmail.isBlank()) {
            return@withContext AuthResult.Error(
                "Google account email is missing."
            )
        }

        try {
            val authorizationClient =
                Identity.getAuthorizationClient(activityContext)

            val requestedScopes = listOf(
                Scope(YOUTUBE_SCOPE_FULL),
                Scope(YOUTUBE_SCOPE_FORCE_SSL)
            )

            val authorizationRequest = AuthorizationRequest.builder()
                .setRequestedScopes(requestedScopes)
                .setAccount(
                    Account(
                        accountEmail,
                        "com.google"
                    )
                )
                .build()

            val authorizationResult =
                try {
                    authorizationClient
                        .authorize(authorizationRequest)
                        .awaitTask()
                } catch (e: Exception) {
                    return@withContext AuthResult.Error(
                        message = "Google YouTube authorization failed: ${
                            e.localizedMessage
                                ?: "Unable to request YouTube permissions"
                        }",
                        throwable = e
                    )
                }

            /*
             * First-time authorization:
             * Google requires the user to approve YouTube permissions.
             */
            if (authorizationResult.hasResolution()) {
                val pendingIntent = authorizationResult.pendingIntent

                if (pendingIntent != null) {
                    val intent = Intent().apply {
                        putExtra(
                            EXTRA_CONSENT_INTENT_SENDER,
                            pendingIntent.intentSender
                        )
                    }

                    return@withContext AuthResult.NeedsUserConsent(
                        intent = intent,
                        accountEmail = accountEmail
                    )
                }

                return@withContext AuthResult.Error(
                    "Google requires YouTube permission approval, but no consent screen could be opened."
                )
            }

            /*
             * Already authorized:
             * Google can return the OAuth access token directly.
             */
            val accessToken = authorizationResult.accessToken
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            if (accessToken != null) {
                return@withContext connectYouTubeChannelWithToken(
                    accountEmail = accountEmail,
                    accessToken = accessToken
                )
            }

            return@withContext AuthResult.Error(
                "Google authorization completed, but no YouTube access token was returned."
            )

        } catch (e: Exception) {
            return@withContext AuthResult.Error(
                message = "YouTube authorization failed: ${
                    e.localizedMessage ?: "Unknown error"
                }",
                throwable = e
            )
        }
    }

    /**
     * Uses a real Google OAuth access token to load the authenticated
     * YouTube channel through YouTube Data API v3.
     */
    suspend fun connectYouTubeChannelWithToken(
        accountEmail: String,
        accessToken: String
    ): AuthResult = withContext(Dispatchers.IO) {

        val cleanedToken = accessToken.trim()
        val cleanedEmail = accountEmail.trim()

        if (cleanedEmail.isBlank()) {
            return@withContext AuthResult.Error(
                "Google account email is missing."
            )
        }

        if (cleanedToken.isBlank()) {
            return@withContext AuthResult.Error(
                "YouTube OAuth access token is missing."
            )
        }

        try {
            val response = YouTubeClient.apiService.getMyChannel(
                authHeader = "Bearer $cleanedToken"
            )

            if (!response.isSuccessful) {
                val message = when (response.code()) {
                    401 ->
                        "YouTube authorization expired or was rejected. Please connect Google again."

                    403 ->
                        "YouTube permission was denied or the required YouTube API access is unavailable."

                    404 ->
                        "The YouTube channel could not be found."

                    else ->
                        "YouTube API error ${response.code()}: ${response.message()}"
                }

                return@withContext AuthResult.Error(message)
            }

            val body = response.body()
            val channel = body?.items?.firstOrNull()

            if (channel == null) {
                return@withContext AuthResult.Error(
                    "No YouTube channel is associated with this Google account. Please create or activate a YouTube channel first."
                )
            }

            val snippet = channel.snippet
            val statistics = channel.statistics

            val avatarUrl =
                snippet?.thumbnails?.high?.url
                    ?: snippet?.thumbnails?.medium?.url
                    ?: snippet?.thumbnails?.defaultThumb?.url

            val channelTitle =
                snippet?.title
                    ?.takeIf { it.isNotBlank() }
                    ?: "YouTube Creator"

            val channelHandle =
                snippet?.customUrl
                    ?.takeIf { it.isNotBlank() }
                    ?: "@${channelTitle
                        .replace(" ", "")
                        .lowercase()}"

            /*
             * IMPORTANT:
             * The current AuthSession model requires an expiry time.
             * This is only a temporary local value because the current
             * model does not store the provider-reported expiry.
             */
            val session = AuthSession(
                accountEmail = cleanedEmail,
                accessToken = cleanedToken,
                tokenExpiryEpochMs =
                    System.currentTimeMillis() + 55 * 60 * 1000L,
                channelId = channel.id,
                channelTitle = channelTitle,
                channelHandle = channelHandle,
                channelAvatarUrl = avatarUrl,
                subscriberCount =
                    formatSubscribers(statistics?.subscriberCount),
                videoCount = statistics?.videoCount ?: "0",
                isLiveStreamingEnabled = true
            )

            authStore.saveSession(session)

            return@withContext AuthResult.Success(session)

        } catch (e: Exception) {
            return@withContext AuthResult.Error(
                message = "Could not connect to YouTube: ${
                    e.localizedMessage ?: "Network or API error"
                }",
                throwable = e
            )
        }
    }

    /**
     * Clears the local YouTube session and Credential Manager state.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        authStore.clearSession()

        try {
            credentialManager.clearCredentialState(
                ClearCredentialStateRequest()
            )
        } catch (_: Exception) {
            // Credential Manager cleanup failure should not prevent logout.
        }
    }

    private fun buildCredentialErrorMessage(
        throwable: Throwable
    ): String {
        val raw = throwable.localizedMessage
            ?.takeIf { it.isNotBlank() }
            ?: "Unable to complete Google Sign-In."

        return "Google Sign-In failed: $raw"
    }

    private fun formatSubscribers(
        countStr: String?
    ): String {

        if (countStr.isNullOrBlank()) {
            return "0 Subscribers"
        }

        val count = countStr.toLongOrNull()
            ?: return "$countStr Subs"

        return when {
            count >= 1_000_000 ->
                String.format(
                    "%.1fM Subscribers",
                    count / 1_000_000.0
                )

            count >= 1_000 ->
                String.format(
                    "%.1fK Subscribers",
                    count / 1_000.0
                )

            else ->
                "$count Subscribers"
        }
    }
}

/**
 * Small coroutine bridge for Google Task APIs.
 */
private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->

        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }

        addOnFailureListener { error ->
            if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
        }

        addOnCanceledListener {
            continuation.cancel()
        }

        continuation.invokeOnCancellation {
            // Google Task does not expose a universal cancellation handle.
        }
    }