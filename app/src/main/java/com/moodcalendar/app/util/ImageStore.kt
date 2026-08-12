package com.moodcalendar.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object ImageStore {
    fun persistUris(
        context: Context,
        uris: List<String>,
        subDir: String = "mood_images"
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
                val uri = Uri.parse(uriString)
                val dest = File(dir, "${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                dest.absolutePath
            }.getOrNull()
        }
    }

    fun persistSingle(context: Context, uri: String?, subDir: String): String? {
        if (uri.isNullOrBlank()) return null
        return persistUris(context, listOf(uri), subDir).firstOrNull()
    }
}
