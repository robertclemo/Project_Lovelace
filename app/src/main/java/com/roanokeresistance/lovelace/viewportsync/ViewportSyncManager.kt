package com.roanokeresistance.lovelace.viewportsync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class Viewport(val lat: Double, val lng: Double, val zoom: Float)

data class LeaderState(val leaderUid: String, val leaderName: String, val viewport: Viewport?)

private const val VIEWPORT_DOC_PATH = "viewport_sync/current"

/**
 * Leader-follow viewport sync (§6 of the architecture doc). A single shared
 * Firestore document holds the current leader's identity and their last
 * reported {lat, lng, zoom}; followers listen to it and pan/zoom their own
 * map to match. Coordinates only — not gameplay data, so this rides plain
 * authenticated Firestore rather than the E2E channel chat uses.
 */
class ViewportSyncManager(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val docRef = firestore.document(VIEWPORT_DOC_PATH)

    fun observeLeaderState(): Flow<LeaderState?> = callbackFlow {
        val registration: ListenerRegistration = docRef.addSnapshotListener { snapshot, _ ->
            val leaderUid = snapshot?.getString("leaderUid")
            if (leaderUid.isNullOrEmpty()) {
                trySend(null)
            } else {
                val lat = snapshot.getDouble("lat")
                val lng = snapshot.getDouble("lng")
                val zoom = snapshot.getDouble("zoom")
                val viewport = if (lat != null && lng != null && zoom != null) {
                    Viewport(lat, lng, zoom.toFloat())
                } else {
                    null
                }
                trySend(LeaderState(leaderUid, snapshot.getString("leaderName") ?: "Agent", viewport))
            }
        }
        awaitClose { registration.remove() }
    }

    fun becomeLeader(uid: String, name: String) {
        docRef.set(mapOf("leaderUid" to uid, "leaderName" to name))
    }

    fun stopLeading() {
        docRef.set(mapOf("leaderUid" to "", "leaderName" to ""))
    }

    fun publishViewport(viewport: Viewport) {
        docRef.set(
            mapOf("lat" to viewport.lat, "lng" to viewport.lng, "zoom" to viewport.zoom.toDouble()),
            SetOptions.merge()
        )
    }
}
