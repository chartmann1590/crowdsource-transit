package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.model.Rating
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {

    suspend fun getUserRating(targetType: String, targetId: String): Rating? {
        val uid = auth.currentUser?.uid ?: return null
        return db.reference.child("ratings/$targetType/$targetId/$uid")
            .get().await()
            .getValue<Rating>()
    }

    suspend fun submitRating(
        targetType: String,
        targetId: String,
        overall: Int,
        subcategories: Map<String, Int> = emptyMap(),
        transitType: String = "",
        isAnonymous: Boolean = false,
    ) {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val profile = db.reference.child("users/${user.uid}").get().await()
        val displayName = if (isAnonymous) "Anonymous" else (profile.child("displayName").value as? String ?: "Rider")

        val prevRating = getUserRating(targetType, targetId)
        val prevOverall = prevRating?.overall ?: 0

        val now = System.currentTimeMillis()
        val rating = Rating(
            userId = user.uid,
            displayName = displayName,
            isAnonymous = isAnonymous,
            targetType = targetType,
            targetId = targetId,
            overall = overall,
            subcategories = subcategories,
            transitType = transitType,
            createdAt = prevRating?.createdAt ?: now,
            updatedAt = now,
        )

        db.reference.child("ratings/$targetType/$targetId/${user.uid}").setValue(rating).await()

        val targetRef = db.reference.child(if (targetType == "stop") "stopStats/$targetId" else "routes/$targetId")

        if (prevRating == null) {
            targetRef.child("ratingSum").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(m: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    m.value = (m.getValue(Long::class.java) ?: 0L) + overall
                    return com.google.firebase.database.Transaction.success(m)
                }
                override fun onComplete(e: com.google.firebase.database.DatabaseError?, c: Boolean, s: com.google.firebase.database.DataSnapshot?) {}
            })
            targetRef.child("ratingCount").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(m: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    m.value = (m.getValue(Long::class.java) ?: 0L) + 1
                    return com.google.firebase.database.Transaction.success(m)
                }
                override fun onComplete(e: com.google.firebase.database.DatabaseError?, c: Boolean, s: com.google.firebase.database.DataSnapshot?) {}
            })
        } else {
            val delta = overall - prevOverall
            if (delta != 0) {
                targetRef.child("ratingSum").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                    override fun doTransaction(m: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                        m.value = (m.getValue(Long::class.java) ?: 0L) + delta
                        return com.google.firebase.database.Transaction.success(m)
                    }
                    override fun onComplete(e: com.google.firebase.database.DatabaseError?, c: Boolean, s: com.google.firebase.database.DataSnapshot?) {}
                })
            }
        }
    }
}
