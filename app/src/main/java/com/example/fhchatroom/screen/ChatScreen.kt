package com.example.fhchatroom.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatScreen(  roomId: String) {

    val text = remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Display the chat messages
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {

        }

        // Chat input field and send icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text.value,
                onValueChange = { text.value = it },
                textStyle = TextStyle.Default.copy(fontSize = 16.sp),
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            )

            IconButton(
                onClick = {
                    // Send the message when the icon is clicked
                    if (text.value.isNotEmpty()) {

                        text.value = ""
                    }

                }
            ){
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    ChatScreen(roomId = "PreviewRoom")
}