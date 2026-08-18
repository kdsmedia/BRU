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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.altomedia.beruang.ui.components.tierIcon
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import com.altomedia.beruang.ui.wallet.QrScannerSheet
import com.altomedia.beruang.ui.wallet.WalletCard
import com.altomedia.beruang.ui.wallet.WalletViewModel
import kotlinx.coroutines.launch

/**
 * Profile view — port of the web `#view-profile` (self or visited). Shows
 * header (avatar, name, badges, tier pill), stats (posts/followers/following),
 * own-profile wallet card + action row, and a 3-column post grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onLogout: () -> Unit = {},
    isAdmin: Boolean = false,
    onAdmin: () -> Unit = {},
    modifier: Modifier = Modifier,
    pvm: ProfileViewModel = viewModel(),
    wvm: WalletViewModel = viewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val target = targetUid ?: me.uid
    val state by pvm.state.collectAsState()
    val wallet by wvm.state.collectAsState()
    val refreshing by pvm.refreshing.collectAsState()

    // Modal state (self only): edit, settings, QR, history, scanner, upgrade.
    var showEdit by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showUpgrade by remember { mutableStateOf(false) }

    // System back: close any open modal first, otherwise go back.
    BackHandler(enabled = true) {
        when {
            showEdit -> showEdit = false
            showSettings -> showSettings = false
            showQr -> showQr = false
            showHistory -> showHistory = false
            showScanner -> showScanner = false
            showUpgrade -> showUpgrade = false
            else -> onBack()
        }
    }

    LaunchedEffect(target, me.uid) { pvm.load(target, me.uid) }
    LaunchedEffect(me.uid) { wvm.start(me.uid) }

    val isSelf = target == me.uid
    val tier = AppConstants.tier(state.tier)

    Column(modifier = modifier.fillMaxSize().background(BgBody)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isSelf) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    modifier = Modifier.clickable { onBack() }.size(24.dp),
                    tint = TextMain,
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Profil", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextMain)
            }
            if (isSelf) {
                Icon(Icons.Filled.QrCodeScanner, "Scan QR", modifier = Modifier.clickable { showScanner = true }.padding(4.dp).size(22.dp), tint = TextMain)
                Icon(Icons.Filled.Edit, "Edit", modifier = Modifier.clickable { showEdit = true }.padding(4.dp).size(22.dp), tint = TextMain)
                Icon(Icons.Filled.Settings, "Pengaturan", modifier = Modifier.clickable { showSettings = true }.padding(4.dp).size(22.dp), tint = TextMain)
            }
        }

        // Scrollable body wrapped in pull-to-refresh.
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { pvm.refresh() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = state.photo,
                contentDescription = null,
                modifier = Modifier.size(90.dp).clip(CircleShape).clickable { if (isSelf) showEdit = true },
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
                Icon(
                    tierIcon(state.tier),
                    contentDescription = null,
                    tint = tier.colorHex.toColor(),
                    modifier = Modifier.size(14.dp),
                )
                Text(" " + tier.name, color = tier.colorHex.toColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                onShowMyQr = { showQr = true },
                onScanQr = { showScanner = true },
                onHistory = { showHistory = true },
                onUpgrade = { showUpgrade = true },
            )
        }

        // Banner ad just above the post grid (web: ad block above profile posts).
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            com.altomedia.beruang.ads.BannerAdBlock()
        }

        // Post grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
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
    }

    // Self modals.
    if (showEdit) {
        EditProfileSheet(
            uid = me.uid,
            currentName = state.username,
            currentPhoto = state.photo,
            currentEmail = state.email,
            currentPhone = state.phone,
            currentGender = state.gender,
            onSaved = { showEdit = false; pvm.load(me.uid, me.uid) },
            onDismiss = { showEdit = false },
        )
    }
    if (showSettings) {
        SettingsSheet(
            onEditProfile = { showSettings = false; showEdit = true },
            onLogout = { showSettings = false; onLogout() },
            onAdmin = { showSettings = false; onAdmin() },
            isAdmin = isAdmin,
            onDismiss = { showSettings = false },
        )
    }
    if (showQr) {
        QrSheet(
            uid = me.uid,
            acctId = wallet.acctId,
            name = state.username,
            balance = wallet.balance,
            onDismiss = { showQr = false },
        )
    }
    if (showHistory) {
        TxnHistorySheet(uid = me.uid, onDismiss = { showHistory = false })
    }
    if (showScanner) {
        QrScannerSheet(
            meUid = me.uid,
            myName = state.username,
            myAcctId = wallet.acctId,
            myBalance = wallet.balance,
            onDismiss = { showScanner = false },
        )
    }
    if (showUpgrade) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUpgrade = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showUpgrade = false }) {
                    Text("Tutup", color = TextMuted)
                }
            },
            text = {
                com.altomedia.beruang.ui.wallet.TierSheet(
                    currentTier = state.tier,
                    balance = wallet.balance,
                    onBuy = { target ->
                        scope.launch {
                            val err = com.altomedia.beruang.data.WalletRepository
                                .buyTier(me.uid, state.tier, target)
                            showToast(
                                context,
                                if (err == null) "Berhasil naik ke $target" else err,
                            )
                            if (err == null) { showUpgrade = false; wvm.start(me.uid) }
                        }
                    },
                    onSwitch = { target ->
                        scope.launch {
                            com.altomedia.beruang.data.WalletRepository
                                .switchTier(me.uid, target)
                            showToast(context, "Tier aktif: $target")
                            showUpgrade = false
                            wvm.start(me.uid)
                        }
                    },
                )
            },
        )
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
