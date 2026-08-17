package com.altomedia.beruang.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Auth screen (login/register). Fully implemented in the auth UI step. */
@Composable
fun LoginScreen(onAuthed: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("BERUANG — Login (in progress)")
    }
}
