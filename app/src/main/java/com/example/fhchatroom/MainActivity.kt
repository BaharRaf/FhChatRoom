package com.example.fhchatroom

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
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
import com.example.fhchatroom.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val authViewModel: AuthViewModel = viewModel()
            // Load saved theme preference
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
                            // Toggle theme and persist the choice
                            isDarkTheme = !isDarkTheme
                            prefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
                        }
                    )
                }
            }
        }
    }
    // **Added**: Override lifecycle to update online status on app foreground/background
    override fun onStart() {
        super.onStart()
        // Mark user as online when app comes to foreground
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.email?.let { email ->
            FirebaseFirestore.getInstance().collection("users")
                .document(email).update("isOnline", true)
        }
    }

    override fun onStop() {
        super.onStop()
        // Mark user as offline when app goes to background
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.email?.let { email ->
            FirebaseFirestore.getInstance().collection("users")
                .document(email).update("isOnline", false)
        }
    }
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
        startDestination = Screen.SignupScreen.route
    ) {
        composable(Screen.SignupScreen.route) {
            SignUpScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.LoginScreen.route)
                }
            )
        }
        composable(Screen.LoginScreen.route) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToSignUp = {
                    navController.navigate(Screen.SignupScreen.route)
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
                    // Join room (update members list) and navigate to chat screen
                    // We assume RoomViewModel is used inside ChatRoomListScreen to join
                    navController.navigate("${Screen.ChatScreen.route}/${room.id}")
                },
                onLogout = {
                    navController.navigate(Screen.LoginScreen.route) {
                        popUpTo(Screen.LoginScreen.route) { inclusive = false }
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
                    navController.popBackStack()      // ← now wired to go back
                }
            )
        }
        composable("${Screen.MemberListScreen.route}/{roomId}") { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            MemberListScreen(
                roomId = roomId,
                onBack = { navController.popBackStack() },
                onLeaveRoom = {
                    navController.popBackStack(Screen.ChatRoomsScreen.route, inclusive = false)
                }
            )
        }
    }
}