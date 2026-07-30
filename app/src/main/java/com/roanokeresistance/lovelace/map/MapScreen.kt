package com.roanokeresistance.lovelace.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder for the WebView + IITC-injected intel map (§3 of the
 * architecture doc). The WebView loading intel.ingress.com and the IITC
 * userscript injection land here in the Phase 1 follow-up.
 */
@Composable
fun MapScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(text = "Map (IITC WebView goes here)", modifier = Modifier.align(Alignment.Center))
    }
}
