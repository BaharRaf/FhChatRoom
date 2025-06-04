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
import com.example.fhchatroom.util.OnlineStatusUpdater
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
                DropdownMenuItem(
                    text = { Text(if (isDarkTheme) "Light Theme" else "Dark Theme") },
                    onClick = {
                        onToggleTheme()
                        menuExpanded.value = false
                    }
                )


                DropdownMenuItem(
                    text = { Text("Logout") },
                    onClick = {
                        val email = FirebaseAuth.getInstance().currentUser?.email
                        val onlineStatusUpdater = OnlineStatusUpdater()
                        if (email != null) {

                            // Just mark offline in RTDB and sign out
                            onlineStatusUpdater.goOffline()
                            FirebaseAuth.getInstance().signOut()
                            menuExpanded.value = false
                            onLogout()


                        } else {
                            // fallback
                            onlineStatusUpdater.goOffline()
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
