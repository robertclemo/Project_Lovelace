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
 * First-run gate combining the invite-code admin-approval check and the
 * app-scoped display-name pick (both §4 of the architecture doc) into one
 * step — a Google sign-in alone doesn't get you in; you also need a code
 * an admin handed out.
 */
@Composable
fun JoinScreen(onJoined: () -> Unit) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val repository = remember { InviteRepository() }
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var isJoining by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Join the cell", style = MaterialTheme.typography.titleLarge)
            Text(text = "You'll need an invite code from someone already in.")
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.padding(top = 16.dp),
                singleLine = true,
                label = { Text("Invite code") }
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.padding(top = 8.dp),
                singleLine = true,
                label = { Text("Display name") }
            )
            Text(
                text = "Display name is shown in chat and to the team — separate from your Google account.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Button(
                enabled = code.isNotBlank() && name.isNotBlank() && !isJoining,
                modifier = Modifier.padding(top = 16.dp),
                onClick = {
                    val uid = currentUser?.uid ?: return@Button
                    errorMessage = null
                    isJoining = true
                    scope.launch {
                        when (repository.redeem(code.trim(), uid, name.trim())) {
                            RedeemResult.SUCCESS -> onJoined()
                            RedeemResult.INVALID_CODE -> {
                                errorMessage = "That invite code isn't valid."
                                isJoining = false
                            }
                            RedeemResult.CODE_EXHAUSTED -> {
                                errorMessage = "That invite code has already been fully used."
                                isJoining = false
                            }
                        }
                    }
                }
            ) {
                Text(if (isJoining) "Joining…" else "Join")
            }
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
