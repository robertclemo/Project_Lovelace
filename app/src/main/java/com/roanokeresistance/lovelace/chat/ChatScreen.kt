package com.roanokeresistance.lovelace.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder for the E2E-encrypted team chat (§5 of the architecture doc).
 * Signal Protocol / sender-key group messaging lands here in Phase 2.
 */
@Composable
fun ChatScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(text = "Chat (Signal Protocol goes here)", modifier = Modifier.align(Alignment.Center))
    }
}
