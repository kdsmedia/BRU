package com.altomedia.beruang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.altomedia.beruang.data.NodesRepository
import com.altomedia.beruang.data.Paths
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.str
import com.altomedia.beruang.ui.auth.AuthUser
import com.altomedia.beruang.ui.auth.AuthViewModel
import com.altomedia.beruang.ui.chat.ChatListScreen
import com.altomedia.beruang.ui.chat.ChatScreen
import com.altomedia.beruang.ui.feed.HomeScreen
import com.altomedia.beruang.ui.notif.NotifScreen
import com.altomedia.beruang.ui.profile.ProfileScreen
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BrandRed
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMuted

/** Bottom nav tabs — mirrors the web `.nav-dock` (home/chat/upload/notif/profile). */
enum class Tab(val route: String, val label: String, val icon: ImageVector, val center: Boolean = false) {
    Home("home", "Beranda", Icons.Filled.Home),
    Chat("chat", "Pesan", Icons.Filled.Chat),
    Upload("upload", "Unggah", Icons.Filled.Add, center = true),
    Notif("notif", "Aktivitas", Icons.Filled.Notifications),
    Profile("profile", "Profil", Icons.Filled.Person),
}

/**
 * Main app container with the bottom navigation dock.
 * Replaces the web app's 5 views. Each tab's screen is filled in across the
 * remaining migration steps; placeholders render until then.
 */
@Composable
fun MainScaffold(
    me: AuthUser,
    onLogout: () -> Unit,
    authVm: AuthViewModel = viewModel(),
) {
    var current by remember { mutableStateOf(Tab.Home) }
    var isAdmin by remember { mutableStateOf(false) }
    // Chat window overlay state: when non-null, a ChatScreen is shown on top.
    var chatTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Profile visit overlay: when non-null, ProfileScreen is shown on top.
    var profileTarget by remember { mutableStateOf<String?>(null) }

    // Resolve admin flag from users/{uid}.role (set during bootstrap).
    LaunchedEffect(me.uid) {
        val u = NodesRepository.readValue(Paths.user(me.uid))?.asObject()
        isAdmin = u?.str("role") == "admin"
    }

    Scaffold(
        containerColor = BgBody,
        bottomBar = { NavDock(current = current, onSelect = { current = it }) },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            when (current) {
                Tab.Home -> HomeScreen(
                    me = me,
                    isAdmin = isAdmin,
                    onAddStory = { /* upload step */ },
                    onVisitProfile = { profileTarget = it },
                )
                Tab.Chat -> ChatListScreen(
                    myUid = me.uid,
                    onOpenChat = { uid, name -> chatTarget = uid to name },
                    onVisitProfile = { profileTarget = it },
                )
                Tab.Upload -> Placeholder("Unggah")
                Tab.Notif -> NotifScreen(uid = me.uid)
                Tab.Profile -> ProfileScreen(
                    me = me,
                    targetUid = null,
                    onBack = {},
                    onMessage = { uid -> chatTarget = uid to "" },
                    onEdit = {},
                    onSettings = {},
                    onShowMyQr = {},
                    onScanQr = {},
                    onHistory = {},
                    onUpgrade = {},
                )
            }

            // Chat overlay (full screen on top of the current tab).
            chatTarget?.let { (uid, name) ->
                ChatScreen(
                    me = me,
                    targetUid = uid,
                    targetName = name,
                    onBack = { chatTarget = null },
                    onVisitProfile = { profileTarget = it },
                )
            }

            // Visited profile overlay (full screen on top).
            profileTarget?.let { uid ->
                ProfileScreen(
                    me = me,
                    targetUid = uid,
                    onBack = { profileTarget = null },
                    onMessage = { puid -> chatTarget = puid to ""; profileTarget = null },
                    onEdit = {},
                    onSettings = {},
                    onShowMyQr = {},
                    onScanQr = {},
                    onHistory = {},
                    onUpgrade = {},
                )
            }
        }
    }
}

@Composable
private fun Placeholder(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$label — layar akan diisi langkah berikutnya", color = TextMuted)
    }
}

/**
 * Bottom navigation dock — port of `.nav-dock`: yellow bar, the center upload
 * button is a raised red circle (mirrors `.nav-center-btn`).
 */
@Composable
fun NavDock(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandYellow)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val active = current == tab
            if (tab.center) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(8.dp, CircleShape)
                        .background(BrandRed, CircleShape)
                        .clickable { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            } else {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onSelect(tab) }.padding(horizontal = 8.dp),
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp).alpha(if (active) 1f else 0.6f),
                    )
                    Text(tab.label, color = Color.White, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
