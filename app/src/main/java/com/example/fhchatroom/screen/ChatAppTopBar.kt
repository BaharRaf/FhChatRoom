package com.example.fhchatroom.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
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
            Text(
                text = "Logout",
                modifier = Modifier
                    .clickable {
                        // Sign out from Firebase authentication
                        FirebaseAuth.getInstance().signOut()
                        onLogout()
                    }
                    .padding(end = 8.dp)
            )
        }
    )
}
