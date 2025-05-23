package com.example.fhchatroom.screen

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
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
            IconButton(
                onClick = { menuExpanded.value = true },
                modifier = Modifier
            ) {
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
                            FirebaseFirestore.getInstance().collection("users")
                                .document(currentUserEmail)
                                .update("isOnline", false)
                        }
                        FirebaseAuth.getInstance().signOut()
                        menuExpanded.value = false
                        onLogout()
                    }
                )
            }
        }
    )
}
