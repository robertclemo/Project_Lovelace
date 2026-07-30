package com.roanokeresistance.lovelace.auth

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.roanokeresistance.lovelace.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "AuthScreen"

/**
 * Google Sign-In via Firebase Auth (§4 of the architecture doc). This
 * only establishes an app-scoped Firebase identity — it's deliberately
 * separate from the Ingress/Google session inside the Map WebView, which
 * the player signs into independently.
 */
@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSigningIn by remember { mutableStateOf(false) }
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
            Text(text = "Roanoke Resistance", style = MaterialTheme.typography.titleLarge)
            Text(text = "Sign in to continue")
            Button(
                enabled = !isSigningIn,
                onClick = {
                    errorMessage = null
                    isSigningIn = true
                    scope.launch {
                        try {
                            signInWithGoogle(context)
                            onSignedIn()
                        } catch (e: GetCredentialException) {
                            Log.w(TAG, "Sign-in cancelled or unavailable", e)
                            errorMessage = "Sign-in cancelled or no Google account available on this device."
                        } catch (e: Exception) {
                            Log.e(TAG, "Sign-in failed", e)
                            errorMessage = "Sign-in failed: ${e.message}"
                        } finally {
                            isSigningIn = false
                        }
                    }
                }
            ) {
                Text(if (isSigningIn) "Signing in…" else "Sign in with Google")
            }
            if (isSigningIn) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
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

private suspend fun signInWithGoogle(context: Context) {
    val serverClientId = context.getString(R.string.default_web_client_id)
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    val credentialManager = CredentialManager.create(context)
    val result = credentialManager.getCredential(context, request)

    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
    FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
}
