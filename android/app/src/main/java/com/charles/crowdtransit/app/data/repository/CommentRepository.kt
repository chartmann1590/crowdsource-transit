package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.charles.crowdtransit.model.Comment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {

    fun observeComments(targetType: String, targetId: String): Flow<List<Comment>> =
        db.reference.child("comments/$targetType/$targetId")
            .observeAsFlow()
            .map { snapshot ->
                snapshot?.children?.mapNotNull { it.getValue<Comment>() }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
            }

    suspend fun addComment(
        targetType: String,
        targetId: String,
        text: String,
        rating: Int,
        transitType: String,
        isAnonymous: Boolean,
    ): Comment {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val profile = db.reference.child("users/${user.uid}").get().await()
        val displayName = if (isAnonymous) "Anonymous Rider" else (profile.child("displayName").value as? String ?: "Rider")
        val initials = if (isAnonymous) "?" else displayName.split(" ").take(2).map { it.firstOrNull()?.uppercaseChar() ?: 'R' }.joinToString("")

        val ref = db.reference.child("comments/$targetType/$targetId").push()
        val commentId = ref.key ?: throw IllegalStateException("No key generated")
        val now = System.currentTimeMillis()

        val comment = Comment(
            commentId = commentId,
            userId = user.uid,
            displayName = displayName,
            isAnonymous = isAnonymous,
            avatarInitials = initials,
            targetType = targetType,
            targetId = targetId,
            text = text,
            transitType = transitType,
            rating = rating,
            helpfulCount = 0,
            flagged = false,
            createdAt = now,
            updatedAt = now,
        )

        ref.setValue(comment).await()

        if (targetType == "stop") {
            db.reference.child("stops/$targetId/commentCount")
                .runTransaction(object : com.google.firebase.database.Transaction.Handler {
                    override fun doTransaction(mutableData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                        val count = mutableData.getValue(Long::class.java) ?: 0L
                        mutableData.value = count + 1
                        return com.google.firebase.database.Transaction.success(mutableData)
                    }
                    override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: com.google.firebase.database.DataSnapshot?) {}
                })
        }

        db.reference.child("users/${user.uid}/stats/reviewCount")
            .runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(mutableData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    val count = mutableData.getValue(Long::class.java) ?: 0L
                    mutableData.value = count + 1
                    return com.google.firebase.database.Transaction.success(mutableData)
                }
                override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: com.google.firebase.database.DataSnapshot?) {}
            })

        return comment
    }

    suspend fun markHelpful(targetType: String, targetId: String, commentId: String) {
        val user = auth.currentUser ?: return
        val helpfulRef = db.reference.child("comments/$targetType/$targetId/$commentId/helpful/${user.uid}")
        val countRef = db.reference.child("comments/$targetType/$targetId/$commentId/helpfulCount")

        val alreadyMarked = helpfulRef.get().await().getValue(Boolean::class.java) ?: false

        if (alreadyMarked) {
            helpfulRef.removeValue().await()
            countRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(m: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    m.value = maxOf(0, (m.getValue(Long::class.java) ?: 1L) - 1)
                    return com.google.firebase.database.Transaction.success(m)
                }
                override fun onComplete(e: com.google.firebase.database.DatabaseError?, c: Boolean, s: com.google.firebase.database.DataSnapshot?) {}
            })
        } else {
            helpfulRef.setValue(true).await()
            countRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(m: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    m.value = (m.getValue(Long::class.java) ?: 0L) + 1
                    return com.google.firebase.database.Transaction.success(m)
                }
                override fun onComplete(e: com.google.firebase.database.DatabaseError?, c: Boolean, s: com.google.firebase.database.DataSnapshot?) {}
            })
        }
    }
}
