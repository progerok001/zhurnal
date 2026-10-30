package ru.zhurnal.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/**
 * Аватарка может быть эмодзи или фото с устройства.
 * Фото хранится в базе как `data:image/jpeg;base64,...` и уменьшается до 256×256,
 * чтобы не раздувать базу и трафик.
 */
object AvatarImages {

    private const val PREFIX = "data:image/"
    private const val MAX_SIDE = 256
    private const val JPEG_QUALITY = 82

    fun isImage(value: String?): Boolean = value?.startsWith(PREFIX) == true

    fun decode(value: String?): ImageBitmap? {
        if (!isImage(value)) return null
        val raw = value!!.substringAfter(',', "")
        if (raw.isEmpty()) return null
        return try {
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Читает выбранное фото, обрезает по центру и возвращает data-URL для сервера. */
    fun fromUri(context: Context, uri: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_SIDE &&
                bounds.outHeight / (sample * 2) >= MAX_SIDE
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val source = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val square = scaleSquare(source)
            if (square !== source) source.recycle()
            val output = ByteArrayOutputStream()
            square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            square.recycle()

            val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$encoded"
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        val cropped = Bitmap.createBitmap(bitmap, x, y, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, MAX_SIDE, MAX_SIDE, true)
        if (cropped !== bitmap && cropped !== scaled) cropped.recycle()

        val result = Bitmap.createBitmap(MAX_SIDE, MAX_SIDE, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }
        if (scaled !== result) scaled.recycle()
        return result
    }
}
