package com.example.fhchatroom.screen

import android.util.Log
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun MemberListScreen(
    roomId: String,
    onBack: () -> Unit,
    onLeaveRoom: () -> Unit = {}
) {
    val context = LocalContext.current
    var members by remember { mutableStateOf(listOf<User>()) }
    val coroutineScope = rememberCoroutineScope()
    val firestore = Injection.instance()
    val database = FirebaseDatabase.getInstance()
    val activeListeners = remember { mutableMapOf<String, ValueEventListener>() }
    var roomListener: ListenerRegistration? by remember { mutableStateOf(null) }
    val TAG = "MemberListScreen"

    DisposableEffect(roomId) {
        Log.d(TAG, "Start member listener for room $roomId")
        roomListener = firestore.collection("rooms").document(roomId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Room listen error", err)
                    Toast.makeText(context, "Failed to load members", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    val room = snap.toObject(Room::class.java)
                    val emails = room?.members ?: emptyList()
                    // clear old listeners
                    activeListeners.forEach { (email, listener) ->
                        val enc = email.replace(".", ",")
                        database.getReference("status/$enc").removeEventListener(listener)
                    }
                    activeListeners.clear()
                    if (emails.isNotEmpty()) {
                        // fetch user profiles
                        firestore.collection("users").whereIn("email", emails).get()
                            .addOnSuccessListener { docs ->
                                val users = docs.documents.mapNotNull { it.toObject(User::class.java) }
                                // initialize all as offline
                                members = users.map { it.copy(isOnline = false) }
                                // attach RTDB listeners
                                users.forEach { user ->
                                    val enc = user.email.replace(".", ",")
                                    val ref = database.getReference("status/$enc")
                                    val listener = object: ValueEventListener {
                                        override fun onDataChange(ds: DataSnapshot) {
                                            val online = ds.getValue(Boolean::class.java) ?: false
                                            members = members.map {
                                                if (it.email == user.email) it.copy(isOnline = online)
                                                else it
                                            }
                                        }
                                        override fun onCancelled(e: DatabaseError) {
                                            Log.e(TAG, "Status listener cancelled for ${user.email}", e.toException())
                                        }
                                    }
                                    ref.addValueEventListener(listener)
                                    activeListeners[user.email] = listener
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Fetch users failed", e)
                                Toast.makeText(context, "Failed to load members", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        members = emptyList()
                    }
                } else {
                    Toast.makeText(context, "Room not found", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
        onDispose {
            roomListener?.remove()
            activeListeners.forEach { (email, listener) ->
                val enc = email.replace(".", ",")
                database.getReference("status/$enc").removeEventListener(listener)
            }
            activeListeners.clear()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Members", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            items(members, key = { it.email }) { user ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${user.firstName} ${user.lastName}")
                    Box(
                        Modifier.size(12.dp).background(
                            color = if (user.isOnline) Color.Green else Color.Gray,
                            shape = CircleShape
                        )
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = {
                coroutineScope.launch {
                    val email = FirebaseAuth.getInstance().currentUser?.email
                    if (email != null) {
                        try {
                            firestore.collection("rooms").document(roomId)
                                .update("members", FieldValue.arrayRemove(email)).await()
                            Toast.makeText(context, "Left room", Toast.LENGTH_SHORT).show()
                            onLeaveRoom()
                        } catch(e: Exception) {
                            Log.e(TAG, "Leave failed", e)
                            Toast.makeText(context, "Failed to leave", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }, Modifier.padding(end = 8.dp)) { Text("Leave Room") }
            Button(onClick = onBack) { Text("Close") }
        }
    }
}
