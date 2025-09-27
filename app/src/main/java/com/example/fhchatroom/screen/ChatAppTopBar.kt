package com.example.fhchatroom.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAppTopBar(
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToFriends: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    TopAppBar(
        title = { Text("Chat Rooms") },
        actions = {
            // Friends Icon
            IconButton(onClick = onNavigateToFriends) {
                Icon(imageVector = Icons.Filled.People, contentDescription = "Friends")
            }

            // Profile Icon
            IconButton(onClick = onNavigateToProfile) {
                Icon(imageVector = Icons.Filled.AccountCircle, contentDescription = "Profile")
            }

            // Menu with other options
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