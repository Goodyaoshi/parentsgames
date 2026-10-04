package com.goodyaoshi.parentsgames.feature.games.jigsaw.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carrega uma imagem do disco/uri aplicando rotação EXIF e downscale
 * (limite de 4000px no maior lado para não estourar memória em fotos
 * gigantes do celular moderno).
 */
@Singleton
class PhotoLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val MAX_DIMENSION = 4000
    }

    fun loadFromUri(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return runCatching {
            // 1) Bounds-only decode para descobrir dimensão real
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val sample = computeSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)

            // 2) Decode com inSampleSize
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching null

            // 3) EXIF rotation
            val orientation = readExifOrientation(uri)
            applyExifRotation(raw, orientation)
        }.getOrNull()
    }

    private fun readExifOrientation(uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun applyExifRotation(bmp: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bmp
        }
        return runCatching {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }.getOrDefault(bmp)
    }

    private fun computeSampleSize(srcW: Int, srcH: Int, max: Int): Int {
        var sample = 1
        var w = srcW; var h = srcH
        while (w > max || h > max) {
            sample *= 2; w /= 2; h /= 2
        }
        return sample
    }

    /**
     * Imagem padrão para o caso "primeira vez, ainda sem foto" —
     * uma paisagem abstrata gerada com a paleta do app, suficientemente rica
     * para que o quebra-cabeça funcione mesmo sem foto da galeria.
     */
    fun generateDefaultLandscape(w: Int = 1200, h: Int = 900): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Céu (gradiente vertical sage → cream)
        for (y in 0 until h * 5 / 8) {
            val t = y.toFloat() / (h * 5 / 8)
            val r = (0x9D + t * (0xFA - 0x9D)).toInt()
            val g = (0xBF + t * (0xF6 - 0xBF)).toInt()
            val b = (0xA1 + t * (0xF0 - 0xA1)).toInt()
            val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            val paint = android.graphics.Paint().apply { this.color = color }
            canvas.drawRect(0f, y.toFloat(), w.toFloat(), (y + 1).toFloat(), paint)
        }
        // Sol
        canvas.drawCircle(w * 0.78f, h * 0.22f, h * 0.07f, android.graphics.Paint().apply { color = 0xFFE5B970.toInt() })
        // Montanhas (terracotta)
        val mtnPath = android.graphics.Path().apply {
            moveTo(0f, h * 0.55f)
            lineTo(w * 0.18f, h * 0.30f)
            lineTo(w * 0.32f, h * 0.55f)
            lineTo(w * 0.55f, h * 0.20f)
            lineTo(w * 0.78f, h * 0.55f)
            lineTo(w.toFloat(), h * 0.40f)
            lineTo(w.toFloat(), h * 0.62f)
            lineTo(0f, h * 0.62f)
            close()
        }
        canvas.drawPath(mtnPath, android.graphics.Paint().apply { color = 0xFFC97B5C.toInt() })
        // Campo (sage escuro)
        canvas.drawRect(0f, h * 0.62f, w.toFloat(), h.toFloat(), android.graphics.Paint().apply { color = 0xFF4F6A53.toInt() })
        // Casa pequena (terracotta)
        canvas.drawRect(w * 0.42f, h * 0.55f, w * 0.5f, h * 0.65f, android.graphics.Paint().apply { color = 0xFFC97B5C.toInt() })
        val roofPath = android.graphics.Path().apply {
            moveTo(w * 0.40f, h * 0.55f)
            lineTo(w * 0.46f, h * 0.50f)
            lineTo(w * 0.52f, h * 0.55f)
            close()
        }
        canvas.drawPath(roofPath, android.graphics.Paint().apply { color = 0xFF7B5E8C.toInt() })
        // Árvores
        listOf(0.10f, 0.22f, 0.85f, 0.92f).forEach { x ->
            canvas.drawCircle(w * x, h * 0.70f, h * 0.05f, android.graphics.Paint().apply { color = 0xFF7A9B7E.toInt() })
            canvas.drawRect(w * x - 4, h * 0.72f, w * x + 4, h * 0.80f, android.graphics.Paint().apply { color = 0xFF6B665E.toInt() })
        }
        // Flores (gold + plum) espalhadas
        val rng = kotlin.random.Random(42)
        repeat(24) {
            val fx = rng.nextFloat() * w
            val fy = h * (0.70f + rng.nextFloat() * 0.25f)
            val color = if (rng.nextBoolean()) 0xFFD4A24A.toInt() else 0xFF7B5E8C.toInt()
            canvas.drawCircle(fx, fy, 4f, android.graphics.Paint().apply { this.color = color })
        }
        return bmp
    }
}
