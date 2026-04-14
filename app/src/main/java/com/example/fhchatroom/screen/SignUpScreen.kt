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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    authViewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    val result by authViewModel.authResult.observeAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            visualTransformation = PasswordVisualTransformation()
        )
        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("First Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Last Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )

        Button(
            onClick = {
                // Use ignoreCase true in case the user inputs uppercase letters.
                if (!email.trim().endsWith("@stud.fh-campuswien.ac.at", ignoreCase = true)) {
                    // **Updated**: Show validation errors with Toast (disappear after a few seconds)
                    Toast.makeText(context, "Please use your institutional email (@stud.fh-campuswien.ac.at)", Toast.LENGTH_LONG).show()
                } else if (password.length < 8 || password.all { it.isLetterOrDigit() }) {
                    Toast.makeText(context, "Password must be at least 8 characters long and include at least one special character", Toast.LENGTH_LONG).show()
                } else {
                    authViewModel.signUp(email, password, firstName, lastName)
                    // Clear the input fields after attempting sign up.
                    email = ""
                    password = ""
                    firstName = ""
                    lastName = ""
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("Sign Up")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Already have an account? Sign in.",
            modifier = Modifier.clickable { onNavigateToLogin() }
        )
    }

    // Show Toast messages for sign-up success or failure
    LaunchedEffect(result) {
        when (val r = result) {
            is Result.Success -> {
                Toast.makeText(context, "Account created successfully", Toast.LENGTH_SHORT).show()
                onNavigateToLogin()
            }
            is Result.Error -> {
                r.exception?.message?.let { msg ->
                    val toastText = when {
                        msg.contains("address is already in use", ignoreCase = true) -> "Email is already registered."
                        msg.contains("badly formatted", ignoreCase = true) -> "Invalid email address format."
                        else -> "Sign up failed: $msg"
                    }
                    Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
                } ?: Toast.makeText(context, "Sign up failed: Unknown error", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }
}
