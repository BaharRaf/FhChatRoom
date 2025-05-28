package com.example.fhchatroom.screen

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
import com.google.firebase.database.FirebaseDatabase
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
                DropdownMenuItem(
                    text = { Text(if (isDarkTheme) "Light Mode" else "Dark Mode") },
                    onClick = {
                        onToggleTheme()
                        menuExpanded.value = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Logout") },
                    onClick = {
                        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
                        if (currentUserEmail != null) {
                            // **Updated**: Mark user offline in Firestore then sign out on completion
                            FirebaseFirestore.getInstance().collection("users")
                                .document(currentUserEmail)
                                .update("isOnline", false)
                                .addOnCompleteListener {
                                    // **Added**: Also mark user offline in Realtime Database
                                    val encodedEmail = currentUserEmail.replace(".", ",")
                                    FirebaseDatabase.getInstance().getReference("status/$encodedEmail")
                                        .setValue(false)
                                        .addOnCompleteListener {
                                            FirebaseAuth.getInstance().signOut()
                                            menuExpanded.value = false
                                            onLogout()
                                        }
                                }
                        } else {
                            // Fallback if no current user (should not happen in practice)
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
