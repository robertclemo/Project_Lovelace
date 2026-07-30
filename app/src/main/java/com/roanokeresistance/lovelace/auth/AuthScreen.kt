package com.roanokeresistance.lovelace.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder for Google Sign-In (Firebase Auth), scoped to an app-only
 * identity per §4 of the architecture doc. Wiring to Firebase is deferred
 * until a Firebase project exists (Phase 1 follow-up).
 */
@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Roanoke Resistance", style = MaterialTheme.typography.titleLarge)
            Text(text = "Sign in to continue")
            Button(onClick = onSignedIn) {
                Text("Sign in with Google (stub)")
            }
        }
    }
}
