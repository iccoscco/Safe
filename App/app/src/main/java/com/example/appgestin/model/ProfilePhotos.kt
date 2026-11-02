package com.example.appgestin.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID

/** Imports only the selected photo into app-private storage; no broad gallery permission. */
fun importProfilePhoto(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "No se pudo leer la imagen seleccionada." }
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("No se pudo abrir la foto.")
    val orientation = runCatching {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull()
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
    val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    val scale = minOf(1f, 512f / maxOf(oriented.width, oriented.height))
    val final = Bitmap.createScaledBitmap(oriented, (oriented.width * scale).toInt().coerceAtLeast(1),
        (oriented.height * scale).toInt().coerceAtLeast(1), true)
    val file = File(context.filesDir, "safe_avatar_${UUID.randomUUID()}.jpg")
    try {
        file.outputStream().use { check(final.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        return file.absolutePath
    } catch (e: Exception) {
        file.delete()
        throw e
    } finally {
        if (final !== oriented && final !== decoded) final.recycle()
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
    }
}

fun deleteProfilePhoto(context: Context, path: String?) {
    if (path == null) return
    val file = File(path)
    if (file.parentFile?.canonicalFile == context.filesDir.canonicalFile && file.name.startsWith("safe_avatar_")) file.delete()
}
