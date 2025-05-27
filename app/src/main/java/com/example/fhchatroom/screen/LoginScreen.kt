package com.example.fhchatroom.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.fhchatroom.data.Result
import com.example.fhchatroom.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigateToSignUp: () -> Unit,
    onSignInSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val result by authViewModel.authResult.observeAsState()
    val context = LocalContext.current

    // Clear previous auth result when screen loads
    LaunchedEffect(Unit) {
        authViewModel.clearAuthResult()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = { authViewModel.login(email, password) },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Text("Login")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Don't have an account? Sign up.",
            modifier = Modifier.clickable { onNavigateToSignUp() }
        )
    }

    // Show result of login attempt
    LaunchedEffect(result) {
        when (val r = result) {
            is Result.Success -> {
                Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
                onSignInSuccess()
            }
            is Result.Error -> {
                val e = r.exception
                val toastText = when {
                    // check the exception class
                    e is FirebaseAuthInvalidCredentialsException ->
                        "Incorrect Email or password."
                    e is FirebaseAuthInvalidUserException ->
                        "No account found with this email."
                    e is FirebaseAuthException -> {
                        // you can also branch on the FirebaseAuthException.errorCode if you
                        // want even finer control:
                        when (e.errorCode) {
                            "ERROR_INVALID_EMAIL" -> "That email address is malformed."
                            else -> "Login failed: ${e.localizedMessage}"
                        }
                    }
                    else -> "Login failed: ${e?.localizedMessage ?: "Unknown error"}"
                }
                Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
            }
            else -> Unit
        }
    }
}