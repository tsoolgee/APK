package com.overlaymanager.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View

/**
 * Renders one overlay layer's visual content: a static image OR an animated
 * GIF, without any external image library (matches the original app relying
 * only on built-in GDI+ on Windows).
 *
 * - Android 9 (API 28) and above: uses the platform's ImageDecoder +
 *   AnimatedImageDrawable, which handles GIF looping automatically.
 * - Everything below that, all the way down to Android 4.4 (API 19): falls
 *   back to the legacy android.graphics.Movie API (present since API 1) and
 *   drives the animation loop manually - the direct equivalent of the
 *   frame-timer loop in the original Windows app.
 *
 * Known limitation: color tint is only applied to static images and to
 * animated GIFs on API 28+. On API 19-27, an animated GIF is shown without
 * tint (Movie has no clean tint hook) - tint still works for the width/height/
 * alpha/position of that layer, only the color overlay itself is skipped.
 */
class OverlayMediaView(context: Context) : View(context) {

    private var staticBitmap: Bitmap? = null
    private var movie: Movie? = null
    private var movieStartTime: Long = 0
    private var animatedDrawable: Drawable? = null
    private var isAnimated = false

    private var alphaValue: Int = 255
    private var tintColor: Int? = null
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destRect = RectF()

    fun setOverlayAlpha(a: Int) {
        alphaValue = a.coerceIn(0, 255)
        bitmapPaint.alpha = alphaValue
        animatedDrawable?.alpha = alphaValue
        invalidate()
    }

    fun setOverlayTint(color: Int?) {
        tintColor = color
        bitmapPaint.colorFilter = color?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_ATOP) }
        animatedDrawable?.colorFilter = color?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_ATOP) }
        invalidate()
    }

    /** Loads image content from a content:// Uri. Returns true on success. */
    fun loadFrom(context: Context, uri: Uri): Boolean {
        staticBitmap = null
        movie = null
        animatedDrawable = null
        isAnimated = false

        return try {
            val gif = looksLikeGif(context, uri)

            if (gif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                val drawable = android.graphics.ImageDecoder.decodeDrawable(source)
                animatedDrawable = drawable
                if (drawable is AnimatedImageDrawable) {
                    drawable.start()
                    isAnimated = true
                }
            } else if (gif) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    movie = Movie.decodeStream(stream)
                }
                movieStartTime = SystemClock.uptimeMillis()
                isAnimated = movie != null
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    staticBitmap = BitmapFactory.decodeStream(stream)
                }
            }

            bitmapPaint.alpha = alphaValue
            bitmapPaint.colorFilter = tintColor?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_ATOP) }
            animatedDrawable?.alpha = alphaValue
            animatedDrawable?.colorFilter = tintColor?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_ATOP) }

            invalidate()
            staticBitmap != null || movie != null || animatedDrawable != null
        } catch (e: Exception) {
            false
        }
    }

    private fun looksLikeGif(context: Context, uri: Uri): Boolean {
        val type = context.contentResolver.getType(uri)
        if (type != null) return type == "image/gif"
        // Fallback: sniff the GIF magic header ("GIF87a" / "GIF89a")
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(6)
                val read = stream.read(header)
                read == 6 && header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val drawable = animatedDrawable
        val m = movie
        val bmp = staticBitmap

        when {
            drawable != null -> {
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
            }
            m != null -> {
                val duration = m.duration().takeIf { it > 0 } ?: 1000
                val elapsed = ((SystemClock.uptimeMillis() - movieStartTime) % duration).toInt()
                m.setTime(elapsed)

                val scaleX = w / m.width().toFloat()
                val scaleY = h / m.height().toFloat()
                val save = canvas.save()
                canvas.scale(scaleX, scaleY)
                if (alphaValue < 255) {
                    // Movie has no built-in alpha param on this overload, so
                    // approximate transparency with a layer alpha instead.
                    canvas.saveLayerAlpha(0f, 0f, m.width().toFloat(), m.height().toFloat(), alphaValue)
                }
                m.draw(canvas, 0f, 0f)
                canvas.restoreToCount(save)
            }
            bmp != null -> {
                destRect.set(0f, 0f, w.toFloat(), h.toFloat())
                canvas.drawBitmap(bmp, null, destRect, bitmapPaint)
            }
        }

        if (isAnimated) {
            postInvalidateOnAnimation()
        }
    }
}
