package com.example.fhchatroom.screen

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAppTopBar(
    onLogout: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    TopAppBar(
        title = { Text("Chat Rooms") },
        actions = {
            val menuExpanded = remember { mutableStateOf(false) }
            IconButton(onClick = { menuExpanded.value = true }) {
                Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Menu")
            }
            DropdownMenu(
                expanded = menuExpanded.value,
                onDismissRequest = { menuExpanded.value = false }
            ) {
                // … your theme toggle item …

                DropdownMenuItem(
                    text = { Text("Logout") },
                    onClick = {
                        val email = FirebaseAuth.getInstance().currentUser?.email
                        if (email != null) {
                            // 1) mark offline in Firestore
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(email)
                                .update("isOnline", false)
                                .addOnSuccessListener {
                                    // 2) sign out only after Firestore update
                                    FirebaseAuth.getInstance().signOut()
                                    menuExpanded.value = false
                                    onLogout()
                                }
                                .addOnFailureListener { e ->
                                    // even on failure, sign out to avoid stuck state
                                    Log.e("ChatAppTopBar", "Could not set isOnline=false", e)
                                    FirebaseAuth.getInstance().signOut()
                                    menuExpanded.value = false
                                    onLogout()
                                }
                        } else {
                            // fallback
                            FirebaseAuth.getInstance().signOut()
                            menuExpanded.value = false
                            onLogout()
                        }
                    }
                )
            }
        }
    )
}
