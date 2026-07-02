package com.charles.crowdtransit.app.data.repository

import android.graphics.Bitmap
import android.util.Base64
import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.charles.crowdtransit.model.StopPhoto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

private const val MAX_DIMENSION_PX = 800
private const val TARGET_BYTES = 100 * 1024 // ~100KB
private const val MAX_PHOTOS_PER_STOP = 10

/**
 * Photos on stops. Stored as base64 JPEG directly in RTDB under /photos/{stopId}/{pushId}
 * (Firebase Storage isn't configured on this project and new buckets need a paid plan; base64 in
 * RTDB is fine at hobby scale and is swappable for Storage later behind this same interface).
 */
@Singleton
class PhotoRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {

    fun observePhotos(stopId: String): Flow<List<StopPhoto>> =
        db.reference.child("photos/$stopId").observeAsFlow().map { snapshot ->
            snapshot?.children?.mapNotNull { child ->
                child.getValue<StopPhoto>()?.copy(photoId = child.key ?: "")
            }?.sortedByDescending { it.timestamp } ?: emptyList()
        }

    /** Downscales to max 800px on the long edge and compresses to roughly the target size. */
    fun compressToBase64(bitmap: Bitmap): String {
        val scale = MAX_DIMENSION_PX.toFloat() / max(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).toInt()),
                max(1, (bitmap.height * scale).toInt()),
                true,
            )
        } else {
            bitmap
        }

        var quality = 90
        var bytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            quality -= 15
        } while (bytes.size > TARGET_BYTES && quality > 20)

        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun uploadPhoto(stopId: String, bitmap: Bitmap) {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val base64 = compressToBase64(bitmap)

        val stopRef = db.reference.child("photos/$stopId")
        val existing = stopRef.get().await()
        if (existing.childrenCount >= MAX_PHOTOS_PER_STOP) {
            val oldest = existing.children.mapNotNull { it.getValue<StopPhoto>()?.copy(photoId = it.key ?: "") }
                .minByOrNull { it.timestamp }
            if (oldest != null && oldest.photoId.isNotEmpty()) {
                stopRef.child(oldest.photoId).removeValue().await()
            }
        }

        val ref = stopRef.push()
        val photo = StopPhoto(
            photoId = ref.key ?: "",
            uid = user.uid,
            data = base64,
            timestamp = System.currentTimeMillis(),
        )
        ref.setValue(photo).await()
    }
}
