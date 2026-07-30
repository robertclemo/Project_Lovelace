package com.roanokeresistance.lovelace.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * First-run prompt for an app-scoped display name (§4 of the architecture
 * doc) — deliberately decoupled from the player's real Ingress/Google
 * identity. Pre-filled from their Google name only as a starting
 * suggestion; they can change it before continuing.
 */
@Composable
fun ChooseDisplayNameScreen(onDone: () -> Unit) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val repository = remember { UserProfileRepository() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Pick a display name", style = MaterialTheme.typography.titleLarge)
            Text(text = "Shown in chat and to the team — separate from your Google account.")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.padding(top = 16.dp),
                singleLine = true,
                label = { Text("Display name") }
            )
            Button(
                enabled = name.isNotBlank() && !isSaving,
                modifier = Modifier.padding(top = 16.dp),
                onClick = {
                    val uid = currentUser?.uid ?: return@Button
                    isSaving = true
                    scope.launch {
                        repository.setDisplayName(uid, name.trim())
                        onDone()
                    }
                }
            ) {
                Text(if (isSaving) "Saving…" else "Continue")
            }
        }
    }
}
