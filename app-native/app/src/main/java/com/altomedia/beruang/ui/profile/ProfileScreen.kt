package com.altomedia.beruang.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.altomedia.beruang.data.AppConstants
import com.altomedia.beruang.data.PostRepository
import com.altomedia.beruang.ui.auth.AuthUser
import com.altomedia.beruang.ui.components.showToast
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import com.altomedia.beruang.ui.wallet.WalletCard
import com.altomedia.beruang.ui.wallet.WalletViewModel
import kotlinx.coroutines.launch

/**
 * Profile view — port of the web `#view-profile` (self or visited). Shows
 * header (avatar, name, badges, tier pill), stats (posts/followers/following),
 * own-profile wallet card + action row, and a 3-column post grid.
 */
@Composable
fun ProfileScreen(
    me: AuthUser,
    targetUid: String?,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onShowMyQr: () -> Unit,
    onScanQr: () -> Unit,
    onHistory: () -> Unit,
    onUpgrade: () -> Unit,
    pvm: ProfileViewModel = viewModel(),
    wvm: WalletViewModel = viewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val target = targetUid ?: me.uid
    val state by pvm.state.collectAsState()
    val wallet by wvm.state.collectAsState()

    LaunchedEffect(target, me.uid) { pvm.load(target, me.uid) }
    LaunchedEffect(me.uid) { wvm.start(me.uid) }

    val isSelf = target == me.uid
    val tier = AppConstants.tier(state.tier)

    Column(modifier = Modifier.fillMaxSize().background(BgBody)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isSelf) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    modifier = Modifier.clickable { onBack() }.size(24.dp),
                    tint = TextMain,
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Profil", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextMain)
            }
            if (isSelf) {
                Icon(Icons.Filled.Edit, "Edit", modifier = Modifier.clickable { onEdit() }.padding(4.dp).size(22.dp), tint = TextMain)
                Icon(Icons.Filled.Settings, "Pengaturan", modifier = Modifier.clickable { onSettings() }.padding(4.dp).size(22.dp), tint = TextMain)
            }
        }

        // Header
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = state.photo,
                contentDescription = null,
                modifier = Modifier.size(90.dp).clip(CircleShape).clickable { onEdit() },
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(state.username, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain)
                if (state.isAdmin) Text(" 🛡️", fontSize = 14.sp)
                if (state.isAi) Text(" AI", fontSize = 11.sp, color = Color(0xFF6D28D9), fontWeight = FontWeight.Bold)
            }
            // Tier pill
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(tier.colorHex.toColor().copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tier.name, color = tier.colorHex.toColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Stat("Postingan", state.postCount)
                Stat("Pengikut", state.followersCount)
                Stat("Mengikuti", state.followingCount)
            }

            // Action buttons
            if (!isSelf) {
                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionPill(
                        if (state.isFollowing) "Mengikuti" else "Ikuti",
                        color = if (state.isFollowing) TextMuted else BrandYellow,
                        onClick = {
                            scope.launch {
                                val now = PostRepository.toggleFollow(me, target, state.username)
                                showToast(context, if (now) "Mengikuti ${state.username}" else "Berhenti mengikuti")
                            }
                        },
                    )
                    ActionPill("Pesan", color = TextMain, icon = Icons.Filled.Chat, onClick = { onMessage(target) })
                }
            }
        }

        // Wallet card (self only)
        if (isSelf) {
            WalletCard(
                state = wallet,
                myName = state.username,
                onShowMyQr = onShowMyQr,
                onScanQr = onScanQr,
                onHistory = onHistory,
                onUpgrade = onUpgrade,
            )
        }

        // Post grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(state.gridImages) { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextMain)
        Text(label, fontSize = 12.sp, color = TextMuted)
    }
}

@Composable
private fun ActionPill(label: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

private fun Long.toColor(): Color = Color(this.toInt())
