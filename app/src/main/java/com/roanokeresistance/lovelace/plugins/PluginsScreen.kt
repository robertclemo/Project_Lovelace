package com.roanokeresistance.lovelace.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * IITC plugin management (§3 of the architecture doc) — mirrors IITC
 * Mobile's own plugins tab: check the plugins you want, then reload the
 * map to apply them. Toggling saves immediately to Firestore; the actual
 * WebView only re-injects on reload since there's no clean way to
 * "uninject" a running plugin from a live page.
 */
@Composable
fun PluginsScreen(onReload: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val repository = remember { PluginPreferencesRepository() }
    val scope = rememberCoroutineScope()

    var enabledIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(uid) {
        if (uid != null) {
            enabledIds = repository.getEnabledPluginIds(uid)
        }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(text = "IITC Plugins", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Check the plugins you want, then reload the map to apply them.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            IITC_PLUGIN_CATALOG.forEach { plugin ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = plugin.id in enabledIds,
                        onCheckedChange = { checked ->
                            enabledIds = if (checked) enabledIds + plugin.id else enabledIds - plugin.id
                            val u = uid ?: return@Checkbox
                            scope.launch { repository.setPluginEnabled(u, plugin.id, checked) }
                        }
                    )
                    Column {
                        Text(text = plugin.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(text = plugin.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = onReload) {
                    Text("Reload map")
                }
            }
        }
    }
}
