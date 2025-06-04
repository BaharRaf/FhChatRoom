package com.example.fhchatroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.fhchatroom.screen.*
import com.example.fhchatroom.ui.theme.ChatRoomAppTheme
import com.example.fhchatroom.util.OnlineStatusUpdater
import com.example.fhchatroom.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var onlineStatusUpdater: OnlineStatusUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onlineStatusUpdater = OnlineStatusUpdater()

        setContent {
            val context = applicationContext
            val coroutineScope = rememberCoroutineScope()
            var isDarkTheme by remember { mutableStateOf(false) }

            ChatRoomAppTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel = viewModel()

                    // Start at ChatRoomList if logged in
                    val user = FirebaseAuth.getInstance().currentUser
                    val startDestination = if (user != null)
                        Screen.ChatRoomsScreen.route
                    else
                        Screen.SignupScreen.route

                    NavigationGraph(
                        navController = navController,
                        authViewModel = authViewModel,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = {
                            isDarkTheme = !isDarkTheme
                            coroutineScope.launch {}
                        },
                        startDestination = startDestination
                    )
                }
            }

            // Observe app lifecycle to update online status
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    val email = FirebaseAuth.getInstance().currentUser?.email
                    if (email != null) {
                        if (event == Lifecycle.Event.ON_START) {
                            onlineStatusUpdater = OnlineStatusUpdater()
                        }
                        if (event == Lifecycle.Event.ON_STOP) {
                            onlineStatusUpdater.goOffline()
                        }
                    }
                }
                ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                onDispose {
                    ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
                    onlineStatusUpdater.cleanup()
                }
            }
        }
    }
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.SignupScreen.route) {
            SignUpScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.LoginScreen.route) {
                        popUpTo(Screen.SignupScreen.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.LoginScreen.route) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToSignUp = {
                    navController.navigate(Screen.SignupScreen.route) {
                        popUpTo(Screen.LoginScreen.route) { inclusive = true }
                    }
                }
            ) {
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
                    navController.navigate(Screen.LoginScreen.route) {
                        popUpTo(Screen.ChatRoomsScreen.route) { inclusive = true }
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
                onLeaveRoom = {
                    navController.popBackStack(Screen.ChatRoomsScreen.route, inclusive = false)
                }
            )
        }
    }
}
