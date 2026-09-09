package com.example.engine.composition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import com.example.model.CompositionElement
import com.example.model.CompositionElementType

/**
 * Generates and caches GPU-ready 2D Bitmaps for banners, bottom strips, memes, and loaded gallery photos.
 */
class GraphicOverlayTextureGenerator(private val context: Context) {

    private val TAG = "GraphicOverlayGenerator"

    // Texture Cache: elementId -> OpenGl Texture Id
    private val textureCache = mutableMapOf<String, Int>()
    // Hash cache to check if text or URI changed
    private val elementHashCache = mutableMapOf<String, Int>()

    /**
     * Obtains or generates an OpenGL 2D texture for the given CompositionElement.
     */
    fun getOrCreateTexture(element: CompositionElement): Int {
        val currentHash = computeElementHash(element)
        val cachedHash = elementHashCache[element.id]
        val existingTexId = textureCache[element.id] ?: 0

        if (existingTexId > 0 && cachedHash == currentHash) {
            return existingTexId
        }

        // Generate Bitmap based on element type
        val bitmap = try {
            when (element.type) {
                CompositionElementType.BOTTOM_STRIP -> generateBottomStripBitmap(element)
                CompositionElementType.BANNER -> generateTopBannerBitmap(element)
                CompositionElementType.MEME -> generateMemeBitmap(element)
                CompositionElementType.PNG,
                CompositionElementType.PHOTO,
                CompositionElementType.CUSTOM_GRAPHICS -> loadUriOrFallbackBitmap(element)
                CompositionElementType.VIDEO -> null // Handled via OES Video player
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate overlay bitmap for ${element.name}: ${e.message}")
            null
        }

        if (bitmap != null) {
            val texId = GlesUtils.loadBitmapToTexture(bitmap, existingTexId)
            bitmap.recycle()
            textureCache[element.id] = texId
            elementHashCache[element.id] = currentHash
            return texId
        }

        return existingTexId
    }

    private fun computeElementHash(e: CompositionElement): Int {
        var result = e.id.hashCode()
        result = 31 * result + e.type.hashCode()
        result = 31 * result + (e.contentUri?.hashCode() ?: 0)
        result = 31 * result + (e.titleText?.hashCode() ?: 0)
        result = 31 * result + (e.subtitleText?.hashCode() ?: 0)
        result = 31 * result + e.accentColorHex.hashCode()
        result = 31 * result + e.bannerBgColorHex.hashCode()
        return result
    }

    private fun loadUriOrFallbackBitmap(element: CompositionElement): Bitmap {
        if (!element.contentUri.isNullOrBlank()) {
            try {
                val uri = Uri.parse(element.contentUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val decoded = BitmapFactory.decodeStream(stream, null, options)
                    if (decoded != null) return decoded
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load image from URI ${element.contentUri}: ${e.message}")
            }
        }

        // Fallback custom stylized graphic bitmap
        return generateTeamCrestFallback(element)
    }

    private fun generateBottomStripBitmap(element: CompositionElement): Bitmap {
        val width = 1280
        val height = 120
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val accentColor = parseColorSafe(element.accentColorHex, Color.parseColor("#00F0FF"))
        val bgColor = parseColorSafe(element.bannerBgColorHex, Color.parseColor("#E60B0F19"))

        // Background Box with glowing border
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val rect = RectF(6f, 6f, width - 6f, height - 6f)
        canvas.drawRoundRect(rect, 16f, 16f, bgPaint)
        canvas.drawRoundRect(rect, 16f, 16f, borderPaint)

        // Accent Left Edge Tag
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
        }
        val tagRect = RectF(6f, 6f, 24f, height - 6f)
        canvas.drawRoundRect(tagRect, 16f, 16f, tagPaint)

        // Live Pulsing Dot
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF0033")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(50f, 40f, 10f, dotPaint)

        // "LIVE" Badge Text
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("LIVE", 70f, 48f, badgePaint)

        // Primary Title Marquee Text
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val title = element.titleText ?: "MVP ESPORTS GRAND CHAMPIONSHIP"
        canvas.drawText(title, 140f, 48f, titlePaint)

        // Subtitle / Sponsor Social Text
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val subtitle = element.subtitleText ?: "SUBSCRIBE • LIKE • FOLLOW @MVP_ESPORTS"
        canvas.drawText(subtitle, 50f, 92f, subPaint)

        return bitmap
    }

    private fun generateTopBannerBitmap(element: CompositionElement): Bitmap {
        val width = 1280
        val height = 100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val accentColor = parseColorSafe(element.accentColorHex, Color.parseColor("#FFB700"))
        val bgColor = parseColorSafe(element.bannerBgColorHex, Color.parseColor("#E60A101C"))

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val rect = RectF(6f, 6f, width - 6f, height - 6f)
        canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
        canvas.drawRoundRect(rect, 12f, 12f, borderPaint)

        // Header Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val title = element.titleText ?: "TITAN RIGS • APEX PRO FUEL • OFFICIAL SPONSOR"
        canvas.drawText(title, 40f, 44f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val sub = element.subtitleText ?: "USE PROMO CODE: MVP10 FOR 15% DISCOUNT"
        canvas.drawText(sub, 40f, 78f, subPaint)

        return bitmap
    }

    private fun generateMemeBitmap(element: CompositionElement): Bitmap {
        if (!element.contentUri.isNullOrBlank()) {
            return loadUriOrFallbackBitmap(element)
        }

        val width = 480
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val accentColor = parseColorSafe(element.accentColorHex, Color.parseColor("#39FF14"))

        // Semi-transparent dark container
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9050811")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val rect = RectF(8f, 8f, width - 8f, height - 8f)
        canvas.drawRoundRect(rect, 20f, 20f, bgPaint)
        canvas.drawRoundRect(rect, 20f, 20f, borderPaint)

        // Meme punchline top
        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        canvas.drawText(element.titleText ?: "GG WELL PLAYED!", width / 2f, 80f, topPaint)

        // Trophy / Icon art
        val trophyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 72f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("👑", width / 2f, 200f, trophyPaint)

        // Meme punchline bottom
        val botPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        canvas.drawText(element.subtitleText ?: "VICTORY ROYALE", width / 2f, 300f, botPaint)

        return bitmap
    }

    private fun generateTeamCrestFallback(element: CompositionElement): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val accentColor = parseColorSafe(element.accentColorHex, Color.parseColor("#00F0FF"))

        // Shield Hexagon Path
        val path = Path().apply {
            moveTo(size * 0.5f, size * 0.05f)
            lineTo(size * 0.92f, size * 0.25f)
            lineTo(size * 0.92f, size * 0.70f)
            lineTo(size * 0.5f, size * 0.95f)
            lineTo(size * 0.08f, size * 0.70f)
            lineTo(size * 0.08f, size * 0.25f)
            close()
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6090D16")
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)

        // MVP Crest Text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 44f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("MVP", size * 0.5f, size * 0.52f, textPaint)

        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("ESPORTS", size * 0.5f, size * 0.68f, subTextPaint)

        return bitmap
    }

    private fun parseColorSafe(colorHex: String?, fallback: Int): Int {
        if (colorHex.isNullOrBlank()) return fallback
        return try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            fallback
        }
    }

    fun release() {
        textureCache.values.forEach { GlesUtils.deleteTexture(it) }
        textureCache.clear()
        elementHashCache.clear()
    }
}
