package com.altomedia.beruang.ui.bonus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.altomedia.beruang.ads.AdMobManager
import com.altomedia.beruang.data.AppConstants
import com.altomedia.beruang.data.WalletRepository
import com.altomedia.beruang.ui.components.showToast
import com.altomedia.beruang.ui.feed.rememberActivity
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BgCard
import com.altomedia.beruang.ui.theme.Border
import com.altomedia.beruang.ui.theme.BrandRed
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * Tugas Bonus — daily-task hub shown when the 🎁 nav tab is tapped.
 * Mini cards in a 2-column grid: checkin + 4 daily tasks (watch ads, comments,
 * posts, add-friend).
 */
@Composable
fun BonusScreen(
    meUid: String,
    modifier: Modifier = Modifier,
    vm: BonusViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val activity = rememberActivity()

    LaunchedEffect(meUid) { vm.start(meUid) }

    Column(modifier = modifier.fillMaxSize().background(BgBody)) {
        // Header.
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = BrandYellow, modifier = Modifier.size(24.dp))
            Text("Tugas Bonus", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain)
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandYellow, modifier = Modifier.size(28.dp))
            }
            return@Column
        }

        val cards = listOf(
            BonusCardData(
                title = "Checkin Harian",
                subtitle = "Dapat ${AppConstants.Bonus.POINTS_CHECKIN} poin",
                icon = Icons.Filled.Today,
                progress = if (state.checkin) 1f else 0f,
                label = if (state.checkin) "Selesai" else "Checkin",
                done = state.checkin,
                action = {
                    vm.doCheckin(meUid) { ok, pts ->
                        if (ok) showToast(context, "+$pts poin (checkin)") else showToast(context, "Sudah checkin hari ini")
                    }
                },
            ),
            BonusCardData(
                title = "Tonton Iklan",
                subtitle = "${AppConstants.Bonus.AD_DAILY_LIMIT}x/hari • ${AppConstants.Bonus.POINTS_AD_VALID} poin/ads valid",
                icon = Icons.Filled.PlayCircle,
                progress = state.ads.toFloat() / AppConstants.Bonus.AD_DAILY_LIMIT,
                label = "${state.ads}/${AppConstants.Bonus.AD_DAILY_LIMIT}",
                done = state.ads >= AppConstants.Bonus.AD_DAILY_LIMIT,
                action = {
                    scope.launch {
                        val valid = AdMobManager.showRewarded(activity)
                        if (valid) {
                            vm.watchAd(meUid) { ok, pts ->
                                if (ok) showToast(context, "+$pts poin (iklan valid)") else showToast(context, "Batas iklan harian tercapai")
                            }
                        } else {
                            showToast(context, "Iklan tidak valid / di-skip")
                        }
                    }
                },
            ),
            BonusCardData(
                title = "Komentar",
                subtitle = "${AppConstants.Bonus.COMMENT_DAILY_TARGET}x/hari • ${AppConstants.Bonus.POINTS_COMMENTS} poin",
                icon = Icons.Filled.Comment,
                progress = state.comments.toFloat() / AppConstants.Bonus.COMMENT_DAILY_TARGET,
                label = "${state.comments}/${AppConstants.Bonus.COMMENT_DAILY_TARGET}",
                done = state.comments >= AppConstants.Bonus.COMMENT_DAILY_TARGET,
                action = null,
            ),
            BonusCardData(
                title = "Posting",
                subtitle = "${AppConstants.Bonus.POST_DAILY_TARGET}x/hari • ${AppConstants.Bonus.POINTS_POSTS} poin",
                icon = Icons.Filled.PlayCircle,
                progress = state.posts.toFloat() / AppConstants.Bonus.POST_DAILY_TARGET,
                label = "${state.posts}/${AppConstants.Bonus.POST_DAILY_TARGET}",
                done = state.posts >= AppConstants.Bonus.POST_DAILY_TARGET,
                action = null,
            ),
            BonusCardData(
                title = "Tambah Teman",
                subtitle = "${AppConstants.Bonus.FRIEND_DAILY_TARGET}x/hari • ${AppConstants.Bonus.POINTS_FRIENDS} poin",
                icon = Icons.Filled.GroupAdd,
                progress = state.friends.toFloat() / AppConstants.Bonus.FRIEND_DAILY_TARGET,
                label = "${state.friends}/${AppConstants.Bonus.FRIEND_DAILY_TARGET}",
                done = state.friends >= AppConstants.Bonus.FRIEND_DAILY_TARGET,
                action = null,
            ),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Invite-10-friends milestone — rendered as a normal grid cell so it
            // sits aligned (2-per-row) with the other bonus cards, yet keeps its
            // Undang/Klaim button.
            item {
                InviteGridCard(
                    invitedCount = state.invitedCount,
                    target = AppConstants.INVITE_TARGET,
                    reward = AppConstants.INVITE_REWARD,
                    claimed = state.inviteRewardClaimed,
                    onShare = {
                        scope.launch {
                            val code = WalletRepository.readAcctId(meUid)
                            if (code.isNullOrBlank()) {
                                showToast(context, "Kode undangan belum tersedia")
                                return@launch
                            }
                            val shareText = "Yuk gabung BERUANG! Pakai kode undangan saya: $code\n" +
                                "Unduh di Play Store."
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Undangan BERUANG")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(send, "Bagikan kode undangan"))
                        }
                    },
                    onClaim = {
                        vm.claimInvite(meUid) { ok, pts ->
                            if (ok) {
                                showToast(context, "+$pts poin — bonus undang teman diklaim!")
                            } else {
                                showToast(context, "Belum bisa klaim — ajak ${AppConstants.INVITE_TARGET} teman dulu")
                            }
                        }
                    },
                )
            }
            items(cards) { c -> BonusCard(c) }
        }
    }
}

/**
 * One-time milestone rendered as a compact grid cell (so it aligns 2-per-row
 * with the other bonus cards). Shows live progress (invited/target) and a
 * single context-aware button: "Undang" (share referral code) until the
 * target is met, then "Klaim" (claims the reward), then "Selesai".
 */
@Composable
private fun InviteGridCard(
    invitedCount: Int,
    target: Int,
    reward: Long,
    claimed: Boolean,
    onShare: () -> Unit,
    onClaim: () -> Unit,
) {
    val progress = (invitedCount.toFloat() / target).coerceIn(0f, 1f)
    val canClaim = invitedCount >= target && !claimed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(BrandYellow.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.GroupAdd, contentDescription = null, tint = BrandYellow, modifier = Modifier.size(20.dp))
            }
            if (claimed) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandRed, modifier = Modifier.size(18.dp))
            }
        }
        Text("Undang Teman", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMain)
        Text("$target teman • $reward poin", fontSize = 11.sp, color = TextMuted)
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Border)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (claimed) BrandRed else BrandYellow),
            )
        }
        Text("$invitedCount/$target teman", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (claimed || invitedCount >= target) BrandRed else TextMain)
        // Single context-aware button so the card stays compact in a 2-col grid.
        if (claimed) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = BrandYellow)
                Text(" Undang", color = BrandYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        } else if (canClaim) {
            Button(
                onClick = onClaim,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandYellow),
            ) {
                Text("Klaim $reward", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        } else {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = BrandYellow)
                Text(" Undang", color = BrandYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

private data class BonusCardData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val progress: Float,
    val label: String,
    val done: Boolean,
    val action: (() -> Unit)?,
)

@Composable
private fun BonusCard(c: BonusCardData) {
    val clickable = c.action != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .let { if (clickable) it.clickable { c.action?.invoke() } else it }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(BrandYellow.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(c.icon, contentDescription = null, tint = BrandYellow, modifier = Modifier.size(20.dp))
            }
            if (c.done) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandRed, modifier = Modifier.size(18.dp))
            }
        }
        Text(c.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMain)
        Text(c.subtitle, fontSize = 11.sp, color = TextMuted)
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Border)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(c.progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (c.done) BrandRed else BrandYellow),
            )
        }
        Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (c.done) BrandRed else TextMain)
    }
}
