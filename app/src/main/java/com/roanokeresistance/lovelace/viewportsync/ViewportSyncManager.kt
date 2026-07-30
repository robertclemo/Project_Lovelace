package com.roanokeresistance.lovelace.viewportsync

/**
 * Placeholder for leader-follow viewport sync (§6 of the architecture doc).
 * Not a screen of its own — this is a background module that, once
 * implemented, publishes the leader's {lat, lng, zoom} and drives the
 * followers' MapScreen WebViews via injected JS.
 */
data class Viewport(val lat: Double, val lng: Double, val zoom: Float)

class ViewportSyncManager {
    fun startLeading(onViewportChanged: (Viewport) -> Unit) {
        TODO("Publish viewport updates to the sync channel (Firestore/WebSocket)")
    }

    fun startFollowing(onViewportReceived: (Viewport) -> Unit) {
        TODO("Subscribe to the current leader's viewport updates")
    }
}
