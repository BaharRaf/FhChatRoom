package com.example.fhchatroom.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.fhchatroom.viewmodel.AuthViewModel
import com.example.fhchatroom.data.Result

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
    var errorMessage by remember { mutableStateOf("") }
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

        // Display the error message if it is not empty.
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = Color.Red,
                modifier = Modifier.padding(8.dp)
            )
        }

        Button(
            onClick = {
                // Use ignoreCase true in case the user inputs uppercase letters.
                if (!email.trim().endsWith("@fh-campuswien.ac.at", ignoreCase = true)) {
                    errorMessage = "Please use your institutional email (@fh-campuswien.ac.at)"
                } else {
                    errorMessage = ""
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
                Toast.makeText(context, "Sign up failed: ${r.exception.message}", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }
}
