package com.example.fhchatroom.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.Room
import com.example.fhchatroom.data.User
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun MemberListScreen(
    roomId: String,
    onBack: () -> Unit,
    onLeaveRoom: () -> Unit = {}
) {
    val context = LocalContext.current  // for Toast
    var members by remember { mutableStateOf(listOf<User>()) }
    val coroutineScope = rememberCoroutineScope()


    DisposableEffect(roomId) {
        val firestore = Injection.instance()
        val registration = firestore.collection("rooms").document(roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "Failed to load members: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val room = snapshot.toObject(Room::class.java)
                    val memberEmails = room?.members ?: emptyList()
                    coroutineScope.launch {
                        // Fetch latest User details for each member email
                        val loadedMembers = mutableListOf<User>()
                        for (email in memberEmails) {
                            try {
                                val userDoc = firestore.collection("users").document(email).get().await()
                                userDoc.toObject(User::class.java)?.let { loadedMembers.add(it) }
                            } catch (e: Exception) {
                                // Ignore individual fetch errors
                            }
                        }
                        members = loadedMembers  // update member list state
                    }
                } else {
                    // Room was deleted or not found
                    Toast.makeText(context, "Room not found", Toast.LENGTH_SHORT).show()
                    onBack()  // navigate back if the room no longer exists
                }
            }
        onDispose {
            registration.remove()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // extra gap from the top
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Members",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(members) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${user.firstName} ${user.lastName}")
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (user.isOnline) Color.Green else Color.Gray,
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = { /* … */ },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Leave Room")
            }
            Button(onClick = onBack) {
                Text("Close")
            }
        }
    }
}