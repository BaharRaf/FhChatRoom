package com.example.fhchatroom

sealed class Screen(val route: String) {
    object LoginScreen : Screen("loginscreen")
    object SignupScreen : Screen("signupscreen")
    object ChatRoomsScreen : Screen("chatroomscreen")
    object ChatScreen : Screen("chatscreen")
    object MemberListScreen : Screen("memberscreen")
    object ProfileScreen : Screen("profilescreen")
}