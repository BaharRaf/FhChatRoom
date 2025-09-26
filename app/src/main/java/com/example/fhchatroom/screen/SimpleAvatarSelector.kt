package com.example.fhchatroom.screen


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter

// Predefined avatar URLs from various avatar services
val DEFAULT_AVATARS = listOf(
    "https://api.dicebear.com/7.x/avataaars/svg?seed=Felix",
    "https://api.dicebear.com/7.x/avataaars/svg?seed=Bella",
    "https://api.dicebear.com/7.x/avataaars/svg?seed=Max",
    "https://api.dicebear.com/7.x/avataaars/svg?seed=Luna",
    "https://api.dicebear.com/7.x/adventurer/svg?seed=Felix",
    "https://api.dicebear.com/7.x/adventurer/svg?seed=Bella",
    "https://api.dicebear.com/7.x/adventurer/svg?seed=Max",
    "https://api.dicebear.com/7.x/adventurer/svg?seed=Luna",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Felix",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Bella",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Max",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Luna",
    "https://api.dicebear.com/7.x/fun-emoji/svg?seed=Felix",
    "https://api.dicebear.com/7.x/fun-emoji/svg?seed=Bella",
    "https://api.dicebear.com/7.x/fun-emoji/svg?seed=Max",
    "https://api.dicebear.com/7.x/fun-emoji/svg?seed=Luna",
    "https://api.dicebear.com/7.x/pixel-art/svg?seed=Felix",
    "https://api.dicebear.com/7.x/pixel-art/svg?seed=Bella",
    "https://api.dicebear.com/7.x/pixel-art/svg?seed=Max",
    "https://api.dicebear.com/7.x/pixel-art/svg?seed=Luna",
    "https://api.dicebear.com/7.x/lorelei/svg?seed=Felix",
    "https://api.dicebear.com/7.x/lorelei/svg?seed=Bella",
    "https://api.dicebear.com/7.x/lorelei/svg?seed=Max",
    "https://api.dicebear.com/7.x/lorelei/svg?seed=Luna"
)

@Composable
fun AvatarSelectorDialog(
    onAvatarSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose an Avatar",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Select a profile picture",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(400.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DEFAULT_AVATARS) { avatarUrl ->
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    onAvatarSelected(avatarUrl)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(avatarUrl),
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Simple color-based avatar generator
fun generateColorAvatar(name: String): String {
    val colors = listOf(
        "FF6B6B", "4ECDC4", "45B7D1", "96CEB4",
        "FFEAA7", "DDA0DD", "98D8C8", "F7DC6F",
        "BB8FCE", "85C1E2", "F8B739", "52B788"
    )
    val colorIndex = name.hashCode().mod(colors.size)
    val color = colors[colorIndex]

    // Using ui-avatars.com service for simple letter avatars
    return "https://ui-avatars.com/api/?name=${name.replace(" ", "+")}&background=$color&color=fff&size=200&bold=true"
}