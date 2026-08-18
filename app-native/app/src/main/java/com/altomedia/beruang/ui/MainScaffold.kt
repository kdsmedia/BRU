package com.altomedia.beruang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import com.altomedia.beruang.ads.AdMobManager
import com.altomedia.beruang.data.NodesRepository
import com.altomedia.beruang.data.Paths
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.str
import com.altomedia.beruang.ui.admin.AdminScreen
import com.altomedia.beruang.ui.auth.AuthUser
import com.altomedia.beruang.ui.auth.AuthViewModel
import com.altomedia.beruang.ui.bonus.BonusScreen
import com.altomedia.beruang.ui.chat.ChatListScreen
import com.altomedia.beruang.ui.chat.ChatScreen
import com.altomedia.beruang.ui.feed.HomeScreen
import com.altomedia.beruang.ui.game.GameScreen
import com.altomedia.beruang.ui.notif.NotifScreen
import com.altomedia.beruang.ui.profile.ProfileScreen
import com.altomedia.beruang.ui.upload.UploadScreen
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BrandRed
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMuted

/** Bottom nav tabs — home/bonus/upload/game/profile (bonus & game replaced the
 *  old chat & notif tabs; those are now reached via header icons on Home). */
enum class Tab(val route: String, val label: String, val icon: ImageVector, val center: Boolean = false) {
    Home("home", "Beranda", Icons.Filled.Home),
    Bonus("bonus", "Tugas", Icons.Filled.CardGiftcard),
    Upload("upload", "Unggah", Icons.Filled.Add, center = true),
    Game("game", "Game", Icons.Filled.SportsEsports),
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
    // Admin panel overlay (admin only).
    var showAdmin by remember { mutableStateOf(false) }
    // Quick-access overlays opened from the Home header icons.
    var showMessages by remember { mutableStateOf(false) }
    var showNotif by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val activity = com.altomedia.beruang.ui.feed.rememberActivity()

    // Story upload picker — port of web `uploadStory` (image → stories, 300px q0.5).
    val storyPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val url = com.altomedia.beruang.data.StorageRepository.uploadImage(
                    context, uri, "stories", maxWidth = 300, quality = 0.5f, uid = me.uid,
                )
                if (url != null) {
                    com.altomedia.beruang.data.PostRepository.createStory(me, url)
                    com.altomedia.beruang.ui.components.showToast(context, "Story ditambahkan")
                } else {
                    com.altomedia.beruang.ui.components.showToast(context, "Gagal mengunggah story")
                }
            }
        }
    }

    // Resolve admin flag from users/{uid}.role (set during bootstrap).
    LaunchedEffect(me.uid) {
        val u = NodesRepository.readValue(Paths.user(me.uid))?.asObject()
        isAdmin = u?.str("role") == "admin"
    }
    // Preload the first interstitial so a fresh ad is ready for the first
    // tab change (mirrors web `ADMOB.init().then(prepareInterstitial)`).
    LaunchedEffect(Unit) {
        AdMobManager.init()
        AdMobManager.prepareInterstitial()
    }

    Scaffold(
        containerColor = BgBody,
        bottomBar = {
            NavDock(
                current = current,
                onSelect = { next ->
                    // Full-screen interstitial: only fires on a real view change,
                    // only if an ad is preloaded & ready, and at most once per 15
                    // minutes. Mirrors web `nav()` → `ADMOB.maybeShowInterstitial`.
                    if (next != current) {
                        AdMobManager.maybeShowInterstitial(activity)
                    }
                    current = next
                },
            )
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            when (current) {
                Tab.Home -> HomeScreen(
                    me = me,
                    isAdmin = isAdmin,
                    onAddStory = { storyPicker.launch("image/*") },
                    onVisitProfile = { profileTarget = it },
                    onOpenMessages = { showMessages = true },
                    onOpenNotif = { showNotif = true },
                )
                Tab.Bonus -> BonusScreen(meUid = me.uid)
                Tab.Upload -> UploadScreen(
                    me = me,
                    onPosted = { current = Tab.Home },
                )
                Tab.Game -> GameScreen()
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
                    onLogout = onLogout,
                    isAdmin = isAdmin,
                    onAdmin = { showAdmin = true },
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

            // Admin overlay (admin only).
            if (showAdmin) {
                AdminScreen(
                    myUid = me.uid,
                    onVisitProfile = { profileTarget = it; showAdmin = false },
                    onClose = { showAdmin = false },
                )
            }

            // Quick-access Messages overlay (opened from Home header icon).
            if (showMessages) {
                ChatListScreen(
                    myUid = me.uid,
                    onOpenChat = { uid, name -> chatTarget = uid to name },
                    onVisitProfile = { profileTarget = it },
                    onClose = { showMessages = false },
                )
            }

            // Quick-access Notifications overlay (opened from Home header icon).
            if (showNotif) {
                NotifScreen(
                    uid = me.uid,
                    onClose = { showNotif = false },
                )
            }

            // Play Store rating prompt — appears once per day (device-local day).
            com.altomedia.beruang.ui.components.RateAppDialog()
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
            .navigationBarsPadding()
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
