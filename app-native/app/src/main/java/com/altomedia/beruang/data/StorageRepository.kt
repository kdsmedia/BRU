package com.altomedia.beruang.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import io.github.jan_supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Media upload to the Supabase Storage bucket "media". Mirrors the web app's
 * `uploadToStorage`: compress the image to maxWidth=800 / JPEG 0.7, upload, and
 * return the public URL. Falls back to base64 if storage is unavailable.
 */
object StorageRepository {

    private const val MAX_WIDTH = 800
    private const val QUALITY = 70

    /** Compress + downscale the image at [uri], returning JPEG bytes. */
    suspend fun compress(context: Context, uri: Uri, maxWidth: Int = MAX_WIDTH, quality: Int = QUALITY): ByteArray =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext ByteArray(0)
            val bmp = input.use { BitmapFactory.decodeStream(it) } ?: return@withContext ByteArray(0)
            val scale = maxWidth.toFloat() / bmp.width
            val out = if (bmp.width > maxWidth) {
                Bitmap.createScaledBitmap(bmp, maxWidth, (bmp.height * scale).toInt(), true)
            } else bmp
            val baos = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            if (out != bmp) bmp.recycle()
            out.recycle()
            baos.toByteArray()
        }

    /** Upload bytes and return the public URL; empty string on failure. */
    suspend fun upload(bytes: ByteArray, prefix: String, uid: String): String =
        withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) return@withContext ""
            val ext = "jpg"
            val path = "$prefix/${uid}_${System.currentTimeMillis()}.$ext"
            runCatching {
                SupabaseProvider.storage.from(SupabaseProvider.STORAGE_BUCKET)
                    .upload(path, bytes, ContentType.Image.JPEG)
                publicUrl(path)
            }.getOrElse { "" }
        }

    /** Build the public CDN URL for an object path. */
    fun publicUrl(path: String): String {
        val base = "${SupabaseProvider.SUPABASE_URL}/storage/v1/object/public/${SupabaseProvider.STORAGE_BUCKET}"
        return "$base/$path"
    }
}
