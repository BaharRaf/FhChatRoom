package com.example.fhchatroom.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

// Using services that provide actual PNG/JPG images with diverse human avatars
val WORKING_AVATARS = listOf(
    // Diverse avatars using various services that provide PNG images

    // UI Avatars with different colors and initials (these definitely work)
    "https://ui-avatars.com/api/?name=Emma+Johnson&background=FFB6C1&color=fff&size=200",
    "https://ui-avatars.com/api/?name=James+Smith&background=87CEEB&color=fff&size=200",
    "https://ui-avatars.com/api/?name=Maria+Garcia&background=DDA0DD&color=fff&size=200",
    "https://ui-avatars.com/api/?name=Ahmed+Hassan&background=98D8C8&color=fff&size=200",
    "https://ui-avatars.com/api/?name=Aisha+Williams&background=F0E68C&color=333&size=200",
    "https://ui-avatars.com/api/?name=Chen+Wei&background=FFB347&color=fff&size=200",
    "https://ui-avatars.com/api/?name=Priya+Patel&background=87CEEB&color=fff&size=200",
    "https://ui-avatars.com/api/?name=Carlos+Rodriguez&background=98FB98&color=fff&size=200",
    "https://ui-avatars.com/api/?name=Fatima+Ali&background=DDA0DD&color=fff&size=200",
    "https://ui-avatars.com/api/?name=David+Kim&background=F0E68C&color=333&size=200",
    "https://ui-avatars.com/api/?name=Sarah+Brown&background=FFB6C1&color=fff&size=200",
    "https://ui-avatars.com/api/?name=Michael+Davis&background=87CEEB&color=fff&size=200",

    // Gravatar Identicons (geometric patterns, always work)
    "https://www.gravatar.com/avatar/user1?d=identicon&s=200",
    "https://www.gravatar.com/avatar/user2?d=identicon&s=200",
    "https://www.gravatar.com/avatar/user3?d=identicon&s=200",
    "https://www.gravatar.com/avatar/user4?d=identicon&s=200",

    // RoboHash avatars (robot style, fun and always work)
    "https://robohash.org/avatar1.png?size=200x200&set=set1",
    "https://robohash.org/avatar2.png?size=200x200&set=set1",
    "https://robohash.org/avatar3.png?size=200x200&set=set1",
    "https://robohash.org/avatar4.png?size=200x200&set=set1",

    // Boring Avatars (abstract but colorful)
    "https://source.boringavatars.com/beam/200/Maria%20Mitchell?colors=264653,2a9d8f,e9c46a",
    "https://source.boringavatars.com/marble/200/James%20Cook?colors=264653,2a9d8f,e9c46a",
    "https://source.boringavatars.com/pixel/200/Grace%20Hopper?colors=264653,2a9d8f,e9c46a",
    "https://source.boringavatars.com/sunset/200/Alan%20Turing?colors=264653,2a9d8f,e9c46a"
)

// Avatar data class for better organization
data class Avatar(
    val url: String,
    val name: String,
    val style: String
)

// Create avatar list with metadata
val AVATAR_OPTIONS = listOf(
    // Professional initials style
    Avatar("https://ui-avatars.com/api/?name=EJ&background=6366F1&color=fff&size=200&bold=true", "EJ", "Professional Blue"),
    Avatar("https://ui-avatars.com/api/?name=MS&background=EC4899&color=fff&size=200&bold=true", "MS", "Professional Pink"),
    Avatar("https://ui-avatars.com/api/?name=AH&background=10B981&color=fff&size=200&bold=true", "AH", "Professional Green"),
    Avatar("https://ui-avatars.com/api/?name=LW&background=F59E0B&color=fff&size=200&bold=true", "LW", "Professional Orange"),
    Avatar("https://ui-avatars.com/api/?name=CK&background=8B5CF6&color=fff&size=200&bold=true", "CK", "Professional Purple"),
    Avatar("https://ui-avatars.com/api/?name=RP&background=EF4444&color=fff&size=200&bold=true", "RP", "Professional Red"),
    Avatar("https://ui-avatars.com/api/?name=JD&background=3B82F6&color=fff&size=200&bold=true", "JD", "Classic Blue"),
    Avatar("https://ui-avatars.com/api/?name=NK&background=14B8A6&color=fff&size=200&bold=true", "NK", "Teal"),

    // Geometric patterns
    Avatar("https://www.gravatar.com/avatar/person1?d=identicon&s=200", "Pattern 1", "Geometric"),
    Avatar("https://www.gravatar.com/avatar/person2?d=identicon&s=200", "Pattern 2", "Geometric"),
    Avatar("https://www.gravatar.com/avatar/person3?d=identicon&s=200", "Pattern 3", "Geometric"),
    Avatar("https://www.gravatar.com/avatar/person4?d=identicon&s=200", "Pattern 4", "Geometric"),

    // Robotic avatars
    Avatar("https://robohash.org/user1.png?size=200x200&set=set1", "Robot 1", "Fun Robot"),
    Avatar("https://robohash.org/user2.png?size=200x200&set=set1", "Robot 2", "Fun Robot"),
    Avatar("https://robohash.org/user3.png?size=200x200&set=set2", "Robot 3", "Fun Robot"),
    Avatar("https://robohash.org/user4.png?size=200x200&set=set3", "Robot 4", "Fun Robot"),

    // Abstract colorful
    Avatar("https://source.boringavatars.com/beam/200/Avatar1?colors=FF6B6B,4ECDC4,45B7D1", "Abstract 1", "Abstract Art"),
    Avatar("https://source.boringavatars.com/marble/200/Avatar2?colors=FF6B6B,4ECDC4,45B7D1", "Abstract 2", "Abstract Art"),
    Avatar("https://source.boringavatars.com/pixel/200/Avatar3?colors=FF6B6B,4ECDC4,45B7D1", "Abstract 3", "Pixel Art"),
    Avatar("https://source.boringavatars.com/sunset/200/Avatar4?colors=FF6B6B,4ECDC4,45B7D1", "Abstract 4", "Sunset Style"),

    // Additional colorful initials
    Avatar("https://ui-avatars.com/api/?name=YZ&background=FF1493&color=fff&size=200&bold=true", "YZ", "Hot Pink"),
    Avatar("https://ui-avatars.com/api/?name=QR&background=00CED1&color=fff&size=200&bold=true", "QR", "Turquoise"),
    Avatar("https://ui-avatars.com/api/?name=UV&background=FF8C00&color=fff&size=200&bold=true", "UV", "Dark Orange"),
    Avatar("https://ui-avatars.com/api/?name=WX&background=9370DB&color=fff&size=200&bold=true", "WX", "Medium Purple")
)

@Composable
fun AvatarSelectorDialog(
    onAvatarSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAvatar by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Column {
                Text(
                    text = "Choose Your Avatar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select a style that represents you",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column {
                // Tab selector for different avatar styles
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Initials", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Patterns", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Fun", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter avatars based on selected tab
                val displayedAvatars = when (selectedTab) {
                    0 -> AVATAR_OPTIONS.filter { it.style.contains("Professional") || it.style.contains("Pink") || it.style.contains("Turquoise") || it.style.contains("Orange") || it.style.contains("Purple") || it.style.contains("Teal") || it.style.contains("Blue") }
                    1 -> AVATAR_OPTIONS.filter { it.style.contains("Geometric") || it.style.contains("Abstract") || it.style.contains("Pixel") || it.style.contains("Sunset") }
                    2 -> AVATAR_OPTIONS.filter { it.style.contains("Robot") }
                    else -> AVATAR_OPTIONS
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(displayedAvatars) { avatar ->
                            AvatarItem(
                                avatarUrl = avatar.url,
                                isSelected = selectedAvatar == avatar.url,
                                onClick = {
                                    selectedAvatar = avatar.url
                                    onAvatarSelected(avatar.url)
                                    onDismiss()
                                }
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

@Composable
fun AvatarItem(
    avatarUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(85.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isSelected) {
                    Modifier.border(
                        3.dp,
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        CircleShape
                    )
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        var isLoading by remember { mutableStateOf(true) }
        var hasError by remember { mutableStateOf(false) }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }

        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarUrl)
                .crossfade(true)
                .build(),
            onState = { state ->
                isLoading = state is AsyncImagePainter.State.Loading
                hasError = state is AsyncImagePainter.State.Error

                // Log for debugging
                if (state is AsyncImagePainter.State.Error) {
                    println("Failed to load avatar: $avatarUrl")
                }
            }
        )

        Image(
            painter = painter,
            contentDescription = "Avatar option",
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

// Generate a personalized avatar based on user's actual name
fun generatePersonalAvatar(firstName: String, lastName: String): String {
    val initials = "${firstName.firstOrNull() ?: ""}${lastName.firstOrNull() ?: ""}"
    val colors = listOf(
        "6366F1", "EC4899", "10B981", "F59E0B", "8B5CF6", "EF4444",
        "3B82F6", "14B8A6", "FF1493", "00CED1", "FF8C00", "9370DB"
    )
    val colorIndex = (firstName + lastName).hashCode().mod(colors.size)
    val color = colors[Math.abs(colorIndex)]

    return "https://ui-avatars.com/api/?name=$initials&background=$color&color=fff&size=200&bold=true"
}