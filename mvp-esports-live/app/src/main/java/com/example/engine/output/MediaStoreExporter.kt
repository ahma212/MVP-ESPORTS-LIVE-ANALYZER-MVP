package com.example.engine.output

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MediaStoreExporter handles saving recorded MP4 videos into Android's native MediaStore
 * under Movies/MVP_Recordings with proper metadata and IS_PENDING state (Android 10+).
 */
object MediaStoreExporter {
    private const val TAG = "MediaStoreExporter"

    data class ExportResult(
        val uri: Uri?,
        val publicPath: String,
        val sizeMb: Float,
        val success: Boolean,
        val errorMessage: String? = null
    )

    suspend fun saveToGallery(
        context: Context,
        sourceFile: File,
        width: Int,
        height: Int,
        targetDirectory: String = "Movies/MVP_Recordings"
    ): ExportResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            Log.e(
                TAG,
                "Source recording file does not exist or is empty: ${sourceFile.absolutePath}"
            )

            return@withContext ExportResult(
                uri = null,
                publicPath = sourceFile.absolutePath,
                sizeMb = 0f,
                success = false,
                errorMessage = "Source recording file is empty or missing."
            )
        }

        val sizeBytes = sourceFile.length()
        val sizeMb = sizeBytes / (1024f * 1024f)

        val actualDurationMs = try {
            val retriever = MediaMetadataRetriever()

            try {
                retriever.setDataSource(sourceFile.absolutePath)

                retriever
                    .extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )
                    ?.toLongOrNull()
                    ?.coerceAtLeast(1L)
                    ?: 1L
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Could not read final MP4 duration: ${e.message}"
            )
            1L
        }

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(Date())

        val displayName = "MVP_Rec_$timestamp.mp4"

        val relativePath = targetDirectory
            .trim('/')
            .let {
                if (it.endsWith('/')) {
                    it
                } else {
                    "$it/"
                }
            }

        val contentValues = ContentValues().apply {
            put(
                MediaStore.Video.Media.DISPLAY_NAME,
                displayName
            )

            put(
                MediaStore.Video.Media.TITLE,
                "MVP Esports Screen Recording"
            )

            put(
                MediaStore.Video.Media.MIME_TYPE,
                "video/mp4"
            )

            put(
                MediaStore.Video.Media.WIDTH,
                width
            )

            put(
                MediaStore.Video.Media.HEIGHT,
                height
            )

            put(
                MediaStore.Video.Media.DURATION,
                actualDurationMs
            )

            put(
                MediaStore.Video.Media.DATE_ADDED,
                System.currentTimeMillis() / 1000
            )

            put(
                MediaStore.Video.Media.DATE_TAKEN,
                System.currentTimeMillis()
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    relativePath
                )

                put(
                    MediaStore.Video.Media.IS_PENDING,
                    1
                )
            }
        }

        val resolver = context.contentResolver
        var mediaUri: Uri? = null

        try {
            mediaUri = resolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            if (mediaUri == null) {
                throw IllegalStateException(
                    "Failed to create MediaStore entry for video."
                )
            }

            resolver.openOutputStream(mediaUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int

                    while (
                        inputStream
                            .read(buffer)
                            .also { bytesRead = it } != -1
                    ) {
                        outputStream.write(
                            buffer,
                            0,
                            bytesRead
                        )
                    }

                    outputStream.flush()
                }
            } ?: throw IllegalStateException(
                "Failed to open output stream for MediaStore Uri."
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(
                        MediaStore.Video.Media.IS_PENDING,
                        0
                    )
                }

                resolver.update(
                    mediaUri,
                    publishValues,
                    null,
                    null
                )
            }

            val finalPublicPath =
                "$relativePath$displayName"

            Log.i(
                TAG,
                "Video successfully saved to MediaStore Gallery: " +
                    "$mediaUri " +
                    "($finalPublicPath, " +
                    "${"%.2f".format(sizeMb)} MB, " +
                    "duration=${actualDurationMs} ms)"
            )

            ExportResult(
                uri = mediaUri,
                publicPath = finalPublicPath,
                sizeMb = sizeMb,
                success = true
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error saving recording to MediaStore: ${e.message}",
                e
            )

            if (mediaUri != null) {
                try {
                    resolver.delete(
                        mediaUri,
                        null,
                        null
                    )
                } catch (_: Exception) {
                }
            }

            ExportResult(
                uri = null,
                publicPath = sourceFile.absolutePath,
                sizeMb = sizeMb,
                success = false,
                errorMessage = e.message
            )
        }
    }
}