package com.moodcalendar.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

object ImageStore {
    fun persistUris(
        context: Context,
        uris: List<String>,
        subDir: String = "mood_images",
        maxSide: Int = 1600,
        jpegQuality: Int = 82
    ): List<String> {
        val dir = File(context.filesDir, subDir).apply { mkdirs() }
        return uris.mapNotNull { uriString ->
            if (uriString.startsWith(dir.absolutePath) ||
                uriString.startsWith(context.filesDir.absolutePath) ||
                uriString.startsWith("file:")
            ) {
                return@mapNotNull uriString.removePrefix("file://")
            }
            runCatching {
                compressToFile(context, Uri.parse(uriString), dir, maxSide, jpegQuality)
            }.getOrNull()
        }
    }

    fun persistSingle(
        context: Context,
        uri: String?,
        subDir: String,
        maxSide: Int = 1600,
        jpegQuality: Int = 82
    ): String? {
        if (uri.isNullOrBlank()) return null
        return persistUris(context, listOf(uri), subDir, maxSide, jpegQuality).firstOrNull()
    }

    /** Avatar: smaller JPEG for faster upload. */
    fun persistAvatar(context: Context, uri: String?): String? =
        persistSingle(context, uri, "avatars", maxSide = 512, jpegQuality = 85)

    private fun compressToFile(
        context: Context,
        uri: Uri,
        dir: File,
        maxSide: Int,
        jpegQuality: Int
    ): String? {
        val dest = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val original = BitmapFactory.decodeStream(input) ?: return null
            val scaled = scaleDown(original, maxSide)
            if (scaled !== original) original.recycle()
            FileOutputStream(dest).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            }
            scaled.recycle()
        } ?: return null
        return dest.absolutePath
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = max(w, h)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }
}
