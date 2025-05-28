package com.example.fhchatroom

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.fhchatroom.screen.ChatRoomListScreen
import com.example.fhchatroom.screen.ChatScreen
import com.example.fhchatroom.screen.LoginScreen
import com.example.fhchatroom.screen.MemberListScreen
import com.example.fhchatroom.screen.SignUpScreen
import com.example.fhchatroom.ui.theme.ChatRoomAppTheme
import com.example.fhchatroom.util.OnlineStatusUpdater // Import the new class
import com.example.fhchatroom.viewmodel.AuthViewModel
// Remove these Firebase imports if they are no longer directly used here
// import com.google.firebase.auth.FirebaseAuth
// import com.google.firebase.database.DataSnapshot
// import com.google.firebase.database.DatabaseError
// import com.google.firebase.database.DatabaseReference
// import com.google.firebase.database.FirebaseDatabase
// import com.google.firebase.database.ValueEventListener
// import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    // Remove old presence variables:
    // private var connectionListener: ValueEventListener? = null
    // private var connectedRef: DatabaseReference? = null
    // private var lastEmail: String? = null
    // private val authListener = FirebaseAuth.AuthStateListener { ... } // Remove old listener

    private lateinit var onlineStatusUpdater: OnlineStatusUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onlineStatusUpdater = OnlineStatusUpdater() // Initialize the updater

        setContent {
            val navController = rememberNavController()
            val authViewModel: AuthViewModel = viewModel()
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            var isDarkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }

            ChatRoomAppTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavigationGraph(
                        navController = navController,
                        authViewModel = authViewModel,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = {
                            isDarkTheme = !isDarkTheme
                            prefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The OnlineStatusUpdater's AuthStateListener and .info/connected listener
        // will primarily handle setting the user online.
        // This call ensures that if the app is resuming and listeners are ready,
        // an explicit online status is attempted.
        onlineStatusUpdater.markUserAsOnlineIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        // Called when the activity is no longer visible.
        // User is likely switching apps or app is being backgrounded.
        onlineStatusUpdater.markUserAsOffline()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Called before the activity is destroyed.
        onlineStatusUpdater.performCleanupOnAppDestroy()
    }

    // Remove the old markUserOffline helper function if it existed here.
    // private fun markUserOffline(email: String) { ... }
}


@Composable
fun NavigationGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    NavHost(
        navController = navController,
        // Consider making LoginScreen the start if user can be logged out
        // Or implement logic to check auth state and navigate accordingly
        startDestination = Screen.SignupScreen.route // Or Screen.LoginScreen.route
    ) {
        composable(Screen.SignupScreen.route) {
            SignUpScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.LoginScreen.route) {
                        popUpTo(Screen.SignupScreen.route) { inclusive = true } // Clear signup from backstack
                    }
                }
            )
        }
        composable(Screen.LoginScreen.route) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToSignUp = {
                    navController.navigate(Screen.SignupScreen.route) {
                        popUpTo(Screen.LoginScreen.route) { inclusive = true } // Clear login from backstack
                    }
                }
            ) {
                // On successful login, navigate to chat rooms
                navController.navigate(Screen.ChatRoomsScreen.route) {
                    popUpTo(Screen.LoginScreen.route) { inclusive = true }
                }
            }
        }
        composable(Screen.ChatRoomsScreen.route) {
            ChatRoomListScreen(
                onJoinClicked = { room ->
                    navController.navigate("${Screen.ChatScreen.route}/${room.id}")
                },
                onLogout = {
                    // AuthViewModel should handle sign out, which triggers OnlineStatusUpdater
                    // Navigate back to login screen
                    navController.navigate(Screen.LoginScreen.route) {
                        popUpTo(Screen.ChatRoomsScreen.route) { inclusive = true } // Clear all chat related screens
                    }
                },
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )
        }
        composable("${Screen.ChatScreen.route}/{roomId}") { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            ChatScreen(
                roomId = roomId,
                onShowMembers = {
                    navController.navigate("${Screen.MemberListScreen.route}/$roomId")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("${Screen.MemberListScreen.route}/{roomId}") { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            MemberListScreen(
                roomId = roomId,
                onBack = { navController.popBackStack() },
                onLeaveRoom = { // After leaving room
                    navController.popBackStack(Screen.ChatRoomsScreen.route, inclusive = false)
                }
            )
        }
    }
}
