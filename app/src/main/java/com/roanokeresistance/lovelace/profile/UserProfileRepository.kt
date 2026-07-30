package com.roanokeresistance.lovelace.profile

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private const val USERS_COLLECTION = "users"

/**
 * App-scoped display names (§4 of the architecture doc), stored separately
 * from Firebase Auth's Google-sourced profile so chat and viewport-sync
 * never need to touch the player's real Ingress/Google identity.
 */
class UserProfileRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun getDisplayName(uid: String): String? {
        val snapshot = firestore.collection(USERS_COLLECTION).document(uid).get().await()
        return snapshot.getString("displayName")?.takeIf { it.isNotBlank() }
    }

    // Membership gate (§4 of the architecture doc): true only once the
    // account has redeemed an invite code via InviteRepository — signing
    // in with Google alone isn't enough to reach chat or the map.
    suspend fun isApproved(uid: String): Boolean {
        val snapshot = firestore.collection(USERS_COLLECTION).document(uid).get().await()
        return snapshot.getBoolean("approved") == true
    }
}
