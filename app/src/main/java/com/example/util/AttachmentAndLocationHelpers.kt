package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/** Largest file that fits in one encrypted message (base64 + encryption overhead). */
const val MAX_ATTACHMENT_BYTES = 350 * 1024

/** Longest voice note that fits in one encrypted message at the recorder's bitrate. */
const val MAX_VOICE_NOTE_SECONDS = 100

data class PickedDocument(val name: String, val sizeBytes: Long, val mime: String, val base64: String?)

/**
 * Reads a picked document. Returns base64 = null when the file is larger than
 * [MAX_ATTACHMENT_BYTES]; files are never silently truncated.
 */
suspend fun readDocumentForSending(context: Context, uri: Uri): PickedDocument = withContext(Dispatchers.IO) {
    var name = "belge"
    var size = -1L
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
    }
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    if (size > MAX_ATTACHMENT_BYTES) return@withContext PickedDocument(name, size, mime, null)
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_ATTACHMENT_BYTES) return@use null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }.getOrNull()
    PickedDocument(
        name = name,
        sizeBytes = bytes?.size?.toLong() ?: size,
        mime = mime,
        base64 = bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
    )
}

/**
 * Decodes a picked image at reduced resolution, applies its EXIF rotation and re-encodes it as
 * a JPEG data URI no larger than [maxDimension] pixels. Returns null if it cannot be decoded.
 */
suspend fun encodeImageForSending(context: Context, uri: Uri, maxDimension: Int = 1280): String? =
    withContext(Dispatchers.Default) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) {
                sample *= 2
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val scale = minOf(1f, maxDimension.toFloat() / maxOf(decoded.width, decoded.height))
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotation != 0f) postRotate(rotation)
            }
            val finalBitmap = if (scale < 1f || rotation != 0f) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else {
                decoded
            }
            var quality = 80
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 15
            } while (bytes.size > MAX_ATTACHMENT_BYTES && quality >= 35)
            if (bytes.size > MAX_ATTACHMENT_BYTES) null
            else "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }.getOrNull()
    }

/** Same as [encodeImageForSending] for a bitmap captured by the in-app camera. */
suspend fun encodeBitmapForSending(bitmap: Bitmap, rotationDegrees: Int = 0, maxDimension: Int = 1280): String? =
    withContext(Dispatchers.Default) {
        runCatching {
            val scale = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
            }
            val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val out = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

/**
 * Gets a fresh location fix (GPS if fine location is granted, otherwise network), waiting up to
 * [timeoutMs]. Falls back to a last-known fix only if it is less than two minutes old.
 * Returns null when no real location is available; callers must not invent one.
 */
suspend fun fetchCurrentLocation(context: Context, timeoutMs: Long = 15_000L): Location? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return null
    val providers = buildList {
        if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
    }
    val recent = providers.mapNotNull { p ->
        try {
            manager.getLastKnownLocation(p)
        } catch (_: SecurityException) {
            null
        }
    }.filter { System.currentTimeMillis() - it.time < 120_000L }.maxByOrNull { it.time }

    for (provider in providers) {
        val fix = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (fix != null) return fix
    }
    return recent
}
