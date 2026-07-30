package com.roanokeresistance.lovelace.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.roanokeresistance.lovelace.profile.UserProfileRepository
import kotlinx.coroutines.launch

private const val MESSAGES_COLLECTION = "messages"

private data class ChatMessage(
    val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestampMillis: Long = 0L
)

/**
 * Team chat (§5 of the architecture doc) — currently plaintext over
 * Firestore. End-to-end encryption (Signal Protocol / sender keys) is a
 * planned follow-up; this establishes the real-time message pipeline and
 * UI first.
 */
@Composable
fun ChatScreen() {
    val firestore = remember { FirebaseFirestore.getInstance() }
    val currentUser = FirebaseAuth.getInstance().currentUser
    val profileRepository = remember { UserProfileRepository() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var draft by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: return@LaunchedEffect
        displayName = profileRepository.getDisplayName(uid)
    }

    DisposableEffect(Unit) {
        val registration: ListenerRegistration = firestore.collection(MESSAGES_COLLECTION)
            .orderBy("timestampMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    messages = snapshot.documents.map { doc ->
                        ChatMessage(
                            id = doc.id,
                            senderUid = doc.getString("senderUid") ?: "",
                            senderName = doc.getString("senderName") ?: "Agent",
                            text = doc.getString("text") ?: "",
                            timestampMillis = doc.getLong("timestampMillis") ?: 0L
                        )
                    }
                }
            }
        onDispose { registration.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val isMine = message.senderUid == currentUser?.uid
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMine) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = message.senderName, style = MaterialTheme.typography.labelMedium)
                        Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message the team") }
            )
            IconButton(
                onClick = {
                    val text = draft.trim()
                    val name = displayName
                    if (text.isEmpty() || currentUser == null || name == null) return@IconButton
                    draft = ""
                    scope.launch {
                        val data = hashMapOf(
                            "senderUid" to currentUser.uid,
                            "senderName" to name,
                            "text" to text,
                            "timestampMillis" to System.currentTimeMillis()
                        )
                        firestore.collection(MESSAGES_COLLECTION).add(data)
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
