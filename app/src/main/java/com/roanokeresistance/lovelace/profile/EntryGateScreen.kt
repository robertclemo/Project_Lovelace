package com.roanokeresistance.lovelace.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth

/**
 * One-time routing gate run at app launch and right after sign-in: decides
 * whether the user needs to sign in, redeem an invite code (§4 of the
 * architecture doc's admin-approval gate), or can go straight to the map.
 */
@Composable
fun EntryGateScreen(
    onNeedsSignIn: () -> Unit,
    onNeedsToJoin: () -> Unit,
    onReady: () -> Unit
) {
    val repository = remember { UserProfileRepository() }

    LaunchedEffect(Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onNeedsSignIn()
            return@LaunchedEffect
        }
        if (repository.isApproved(user.uid)) {
            onReady()
        } else {
            onNeedsToJoin()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
