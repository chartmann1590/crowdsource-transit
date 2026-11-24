package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.model.UserProfile
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseDatabase,
) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInAnonymously(): FirebaseUser {
        val result = auth.signInAnonymously().await()
        val user = result.user!!
        ensureUserProfile(user)
        return user
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return if (auth.currentUser?.isAnonymous == true) {
            val result = auth.currentUser!!.linkWithCredential(credential).await()
            val user = result.user!!
            ensureUserProfile(user)
            user
        } else {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user!!
            ensureUserProfile(user)
            user
        }
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user!!
    }

    suspend fun createAccount(email: String, password: String, displayName: String): FirebaseUser {
        val result = if (auth.currentUser?.isAnonymous == true) {
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
            auth.currentUser!!.linkWithCredential(credential).await()
        } else {
            auth.createUserWithEmailAndPassword(email, password).await()
        }
        val user = result.user!!
        val profileUpdate = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
        user.updateProfile(profileUpdate).await()
        ensureUserProfile(user, displayName)
        return user
    }

    /**
     * Re-authenticates an email/password account ahead of a sensitive action
     * (account deletion). Google and anonymous accounts have no password; the
     * caller checks [canReauthenticateWithPassword] first.
     */
    suspend fun reauthenticateWithPassword(password: String) {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val email = user.email ?: throw IllegalStateException("This account has no email address")
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
    }

    /** True when the signed-in account can re-authenticate with a password. */
    fun canReauthenticateWithPassword(): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    /**
     * True for accounts whose only sign-in provider is Google. The app has
     * no Google sign-in flow of its own, so it cannot re-authenticate these
     * accounts in-app; account deletion routes them to the web deletion
     * page instead (see ProfileViewModel).
     */
    fun isGoogleOnlyAccount(): Boolean {
        val providers = auth.currentUser?.providerData?.map { it.providerId } ?: return false
        return GoogleAuthProvider.PROVIDER_ID in providers &&
            EmailAuthProvider.PROVIDER_ID !in providers
    }

    private companion object {
        /** user.delete() demands a recent sign-in (~5 min); keep a safety margin. */
        const val RECENT_LOGIN_WINDOW_MS = 4 * 60 * 1000L
    }

    /**
     * Deletes the signed-in account and all of its CrowdTransit data.
     *
     * The Realtime Database cleanup runs first because the database rules
     * require a signed-in user to write to their own nodes; the Firebase Auth
     * user is deleted last. Deleted: the profile/stats/badges node, the
     * leaderboard entry, saved trips, and every rating, comment, photo,
     * check-in and crowdsourced submission authored by this account.
     * Aggregate counters (e.g. a stop's ratingSum/ratingCount) are not
     * recomputed, the same trade-off the app already makes elsewhere.
     *
     * Throws FirebaseAuthRecentLoginRequiredException when the current
     * sign-in is too old; the caller should re-authenticate (password
     * accounts) or sign out/in and retry.
     */
    suspend fun deleteAccount() {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val uid = user.uid

        // Fail BEFORE any destructive write when the sign-in is too old for
        // user.delete(): otherwise the database cleanup below would succeed
        // and the Auth deletion would then throw, stranding a live account
        // whose data is already gone. Callers re-authenticate first
        // (password accounts) so this guard passes.
        val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
        if (System.currentTimeMillis() - lastSignIn > RECENT_LOGIN_WINDOW_MS) {
            throw FirebaseAuthRecentLoginRequiredException(
                "ERROR_REQUIRES_RECENT_LOGIN",
                "Sign-in is too old for account deletion; re-authenticate first.",
            )
        }

        db.reference.child("users/$uid").removeValue().await()
        db.reference.child("leaderboard/$uid").removeValue().await()
        db.reference.child("savedTrips/$uid").removeValue().await()
        db.reference.child("blockedUsers/$uid").removeValue().await()

        // The user's "helpful" votes live under other riders' comments.
        removeOwnHelpfulVotes(uid)

        // Ratings are stored under the author's uid key.
        removeOwnEntries("ratings", uid, matchUidKey = true, uidField = "userId")
        // Comments / photos / check-ins / crowdsourced submissions carry the
        // author's uid in a field on each entry.
        removeOwnEntries("comments", uid, matchUidKey = false, uidField = "userId")
        removeOwnEntries("photos", uid, matchUidKey = false, uidField = "uid")
        removeOwnEntries("activity", uid, matchUidKey = false, uidField = "uid")
        removeOwnEntries("crowdsourced", uid, matchUidKey = false, uidField = "submittedByUid")

        user.delete().await()
    }

    /**
     * Sweeps a top-level collection and removes every entry that belongs to
     * [uid]. Entries match when their key equals the uid ([matchUidKey], used
     * for ratings) or when their [uidField] child equals the uid (a plain
     * "uid" child is also accepted when [uidField] names something else).
     * Collections nest at most three levels deep (type / target / entry), so
     * the walk is bounded.
     *
     * Trade-off: the sweep reads whole collections because RTDB keeps no
     * per-user index of legacy content - an index maintained at write time
     * would miss everything posted before it existed and silently
     * under-delete, a worse failure for a privacy feature than a heavy
     * one-time download on a rare, user-initiated action. Revisit if the
     * dataset grows enough for the download to matter.
     */
    private suspend fun removeOwnEntries(
        root: String,
        uid: String,
        matchUidKey: Boolean,
        uidField: String,
    ) {
        val snapshot = db.reference.child(root).get().await()
        val removals = mutableListOf<String>()

        fun isOwn(node: DataSnapshot): Boolean {
            if (node.child(uidField).value as? String == uid) return true
            return uidField != "uid" && (node.child("uid").value as? String) == uid
        }

        fun walk(node: DataSnapshot, path: String, depth: Int) {
            for (child in node.children) {
                val key = child.key ?: continue
                val childPath = "$path/$key"
                when {
                    matchUidKey && depth == 2 && key == uid -> removals += childPath
                    isOwn(child) -> removals += childPath
                    depth < 3 -> walk(child, childPath, depth + 1)
                }
            }
        }

        walk(snapshot, root, 0)
        for (path in removals) {
            db.reference.child(path).removeValue().await()
        }
    }

    /**
     * Removes this user's "helpful" votes from other riders' comments
     * (comments/{type}/{target}/{commentId}/helpful/{uid}) and decrements
     * each comment's helpfulCount, so no identifier of the deleted account
     * is left behind in the comments tree.
     */
    private suspend fun removeOwnHelpfulVotes(uid: String) {
        val snapshot = db.reference.child("comments").get().await()
        for (typeNode in snapshot.children) {
            for (targetNode in typeNode.children) {
                for (commentNode in targetNode.children) {
                    if (!commentNode.child("helpful").hasChild(uid)) continue
                    val base = "comments/${typeNode.key}/${targetNode.key}/${commentNode.key}"
                    db.reference.child("$base/helpful/$uid").removeValue().await()
                    val count = commentNode.child("helpfulCount").getValue(Long::class.java)
                    if (count != null) {
                        db.reference.child("$base/helpfulCount")
                            .setValue(maxOf(0L, count - 1)).await()
                    }
                }
            }
        }
    }

    fun signOut() = auth.signOut()

    private suspend fun ensureUserProfile(user: FirebaseUser, displayName: String? = null) {
        val ref = db.reference.child("users/${user.uid}")
        val exists = ref.get().await().exists()
        if (!exists) {
            val name = displayName ?: user.displayName ?: "Rider"
            val initials = name.split(" ").take(2).map { it.firstOrNull()?.uppercaseChar() ?: 'R' }.joinToString("")
            val profile = UserProfile(
                userId = user.uid,
                displayName = name,
                isAnonymous = user.isAnonymous,
                avatarInitials = initials,
                joinedAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis(),
            )
            ref.setValue(profile).await()
        } else {
            ref.child("lastActiveAt").setValue(System.currentTimeMillis()).await()
        }
    }
}
