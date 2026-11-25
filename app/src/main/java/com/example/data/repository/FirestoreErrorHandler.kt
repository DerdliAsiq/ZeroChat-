package com.example.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

/** Thrown for problems the user can act on; the message is shown in the UI (Turkish). */
class ZeroChatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Like [runCatching], but never swallows coroutine cancellation and logs Firestore failures
 * (without personal data) so permission errors are visible in logcat.
 */
suspend inline fun <T> firestoreCatching(
    operation: OperationType,
    path: String,
    crossinline block: suspend () -> T
): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    logFirestoreError(e, operation, path)
    Result.failure(e)
}

fun logFirestoreError(error: Throwable, operation: OperationType, path: String?) {
    val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    val code = firestoreError?.code?.name ?: error::class.java.simpleName
    Log.e("FirestoreError", "op=${operation.value} path=$path code=$code msg=${(firestoreError ?: error).message}")
}

/** Short Turkish description of a failure for banners. */
fun Throwable.userMessage(): String {
    if (this is ZeroChatException) return message.orEmpty()
    val firestoreError = generateSequence(this) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    return when (firestoreError?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Bu işlem için yetkiniz yok"
        FirebaseFirestoreException.Code.UNAVAILABLE -> "Sunucuya ulaşılamıyor, bağlantınızı kontrol edin"
        FirebaseFirestoreException.Code.NOT_FOUND -> "Kayıt bulunamadı"
        FirebaseFirestoreException.Code.ALREADY_EXISTS -> "Kayıt zaten mevcut"
        FirebaseFirestoreException.Code.FAILED_PRECONDITION -> "Sunucu yapılandırması eksik (dizin veya kural)"
        null -> localizedMessage ?: "Bilinmeyen hata"
        else -> "Sunucu hatası: ${firestoreError.code.name}"
    }
}
