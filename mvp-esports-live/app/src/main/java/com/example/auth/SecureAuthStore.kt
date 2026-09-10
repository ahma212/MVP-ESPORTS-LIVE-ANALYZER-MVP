package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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

    private val appContext = context.applicationContext

    /*
     * Metadata/session information may remain in SharedPreferences.
     * The OAuth access token itself is ALWAYS encrypted with an Android
     * Keystore AES-256 key before it is persisted.
     */
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

    private val secretKey: SecretKey
        get() {
            val existingKey = keyStore.getKey(KEYSTORE_ALIAS, null)
            if (existingKey is SecretKey) {
                return existingKey
            }

            val keyGenerator =
                KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )

            val keySpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(keySpec)
            return keyGenerator.generateKey()
        }

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

fun updateAccessToken(
    accessToken: String,
    tokenExpiryEpochMs: Long
) {
    prefs.edit()
        .putString(KEY_ACCESS_TOKEN, accessToken)
        .putLong(KEY_EXPIRY_EPOCH_MS, tokenExpiryEpochMs)
        .apply()
}
    fun getSession(): AuthSession? {
        val email =
            prefs.getString(
                KEY_ACCOUNT_EMAIL,
                null
            ) ?: return null

        val encryptedToken =
            prefs.getString(
                KEY_ENCRYPTED_ACCESS_TOKEN,
                null
            ) ?: return null

        val token =
            try {
                decrypt(encryptedToken)
            } catch (_: Exception) {
                /*
                 * If the encrypted token can no longer be decrypted,
                 * invalidate the saved authentication state instead
                 * of returning corrupt credentials.
                 */
                clearSession()
                return null
            }

        if (token.isBlank()) {
            clearSession()
            return null
        }

        val expiry =
            prefs.getLong(
                KEY_EXPIRY_EPOCH_MS,
                0L
            )

        return AuthSession(
            accountEmail = email,
            accessToken = token,
            tokenExpiryEpochMs = expiry,
            channelId =
                prefs.getString(
                    KEY_CHANNEL_ID,
                    null
                ),
            channelTitle =
                prefs.getString(
                    KEY_CHANNEL_TITLE,
                    null
                ),
            channelHandle =
                prefs.getString(
                    KEY_CHANNEL_HANDLE,
                    null
                ),
            channelAvatarUrl =
                prefs.getString(
                    KEY_CHANNEL_AVATAR,
                    null
                ),
            subscriberCount =
                prefs.getString(
                    KEY_SUB_COUNT,
                    null
                ),
            videoCount =
                prefs.getString(
                    KEY_VIDEO_COUNT,
                    null
                ),
            isLiveStreamingEnabled =
                prefs.getBoolean(
                    KEY_LIVE_STREAM_ENABLED,
                    false
                )
        )
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCOUNT_EMAIL)
            .remove(KEY_ENCRYPTED_ACCESS_TOKEN)
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
        return prefs
            .getString(
                KEY_CUSTOM_CLIENT_ID,
                null
            )
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun saveCustomOAuthClientId(clientId: String) {
        prefs.edit()
            .putString(
                KEY_CUSTOM_CLIENT_ID,
                clientId.trim()
            )
            .apply()
    }

    private fun encrypt(plainText: String): String {
        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey
        )

        val iv = cipher.iv

        val cipherText =
            cipher.doFinal(
                plainText.toByteArray(
                    StandardCharsets.UTF_8
                )
            )

        val combined = ByteArray(
            iv.size + cipherText.size
        )

        System.arraycopy(
            iv,
            0,
            combined,
            0,
            iv.size
        )

        System.arraycopy(
            cipherText,
            0,
            combined,
            iv.size,
            cipherText.size
        )

        return Base64.encodeToString(
            combined,
            Base64.NO_WRAP
        )
    }

    private fun decrypt(encryptedText: String): String {
        val combined =
            Base64.decode(
                encryptedText,
                Base64.NO_WRAP
            )

        require(
            combined.size > GCM_IV_LENGTH_BYTES
        ) {
            "Invalid encrypted token payload."
        }

        val iv =
            combined.copyOfRange(
                0,
                GCM_IV_LENGTH_BYTES
            )

        val cipherText =
            combined.copyOfRange(
                GCM_IV_LENGTH_BYTES,
                combined.size
            )

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        val gcmSpec =
            GCMParameterSpec(
                GCM_TAG_LENGTH_BITS,
                iv
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            gcmSpec
        )

        val plainBytes =
            cipher.doFinal(cipherText)

        return String(
            plainBytes,
            StandardCharsets.UTF_8
        )
    }

    companion object {
        private const val PREFS_NAME =
            "mvp_esports_auth_secure_store"

        private const val ANDROID_KEYSTORE =
            "AndroidKeyStore"

        private const val KEYSTORE_ALIAS =
            "mvp_esports_youtube_auth_aes"

        private const val TRANSFORMATION =
            "AES/GCM/NoPadding"

        private const val GCM_IV_LENGTH_BYTES =
            12

        private const val GCM_TAG_LENGTH_BITS =
            128

        private const val KEY_ACCOUNT_EMAIL =
            "auth_account_email"

        private const val KEY_ENCRYPTED_ACCESS_TOKEN =
            "auth_encrypted_access_token"

        private const val KEY_EXPIRY_EPOCH_MS =
            "auth_token_expiry"

        private const val KEY_CHANNEL_ID =
            "yt_channel_id"

        private const val KEY_CHANNEL_TITLE =
            "yt_channel_title"

        private const val KEY_CHANNEL_HANDLE =
            "yt_channel_handle"

        private const val KEY_CHANNEL_AVATAR =
            "yt_channel_avatar"

        private const val KEY_SUB_COUNT =
            "yt_sub_count"

        private const val KEY_VIDEO_COUNT =
            "yt_video_count"

        private const val KEY_LIVE_STREAM_ENABLED =
            "yt_live_stream_enabled"

        private const val KEY_CUSTOM_CLIENT_ID =
            "oauth_custom_client_id"
    }
}