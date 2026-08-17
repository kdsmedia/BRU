package com.altomedia.beruang.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Main app container with the bottom navigation dock.
 * Replaces the web app's 5 views (home / chat / upload / notif / profile).
 *
 * TODO: fleshed out in later migration steps (feed, chat, upload, notif, profile).
 */
@Composable
fun MainScaffold(onLogout: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("BERUANG — native migration in progress")
    }
}
