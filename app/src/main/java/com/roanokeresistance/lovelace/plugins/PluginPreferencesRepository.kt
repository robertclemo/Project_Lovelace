package com.roanokeresistance.lovelace.plugins

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

private const val USERS_COLLECTION = "users"
private const val ENABLED_PLUGINS_FIELD = "enabledPlugins"

/**
 * Which bundled IITC plugins a user has switched on, synced via their own
 * `users/{uid}` doc (same one display name/approval live on — no separate
 * Firestore rule needed, it's already owner-only read/write).
 */
class PluginPreferencesRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun getEnabledPluginIds(uid: String): Set<String> {
        val snapshot = firestore.collection(USERS_COLLECTION).document(uid).get().await()
        @Suppress("UNCHECKED_CAST")
        val ids = snapshot.get(ENABLED_PLUGINS_FIELD) as? List<String>
        return ids?.toSet() ?: emptySet()
    }

    suspend fun setPluginEnabled(uid: String, pluginId: String, enabled: Boolean) {
        val current = getEnabledPluginIds(uid)
        val updated = if (enabled) current + pluginId else current - pluginId
        firestore.collection(USERS_COLLECTION).document(uid)
            .set(mapOf(ENABLED_PLUGINS_FIELD to updated.toList()), SetOptions.merge())
            .await()
    }
}
