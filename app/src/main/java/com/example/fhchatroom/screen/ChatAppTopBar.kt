package com.example.fhchatroom.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAppTopBar(
    onLogout: () -> Unit
) {
    TopAppBar(
        title = { Text("Chat Rooms") },
        actions = {
            Button(
                onClick = {
                    // Sign out from Firebase authentication and perform any additional backend logout if necessary.
                    FirebaseAuth.getInstance().signOut()
                    onLogout()
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Logout")
            }
        }
    )
}
