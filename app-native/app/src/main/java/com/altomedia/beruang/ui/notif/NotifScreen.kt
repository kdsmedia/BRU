package com.altomedia.beruang.ui.notif

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted

/** Notifications — port of the web `#view-notif` / `listenNotifications`. When
 *  [onClose] is supplied the screen renders as a full-screen overlay with a
 *  back button (used by the Home header icon). */
@Composable
fun NotifScreen(uid: String, onClose: (() -> Unit)? = null, vm: NotifViewModel = viewModel()) {
    val items by vm.items.collectAsState()
    LaunchedEffect(uid) { vm.start(uid) }

    Column(modifier = Modifier.fillMaxSize().background(BgBody)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = TextMain)
                }
            }
            Text(
                "Aktivitas",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextMain,
            )
        }
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada aktivitas.", color = TextMuted)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(items, key = { _, n -> n.key }) { index, n ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .clickable { if (!n.read) vm.markRead(uid, n.key) }
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Unread indicator (green dot) — disappears once opened.
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape)
                                .background(if (n.read) Color.Transparent else Color(0xFF22C55E)),
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BrandYellow.copy(alpha = 0.15f))
                                .padding(start = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Bolt, null, tint = BrandYellow, modifier = Modifier.size(18.dp)) }
                        Text(
                            n.text,
                            fontSize = 14.sp,
                            color = Color(0xFF333333),
                            fontWeight = if (n.read) FontWeight.Normal else FontWeight.Medium,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        IconButton(onClick = { vm.deleteNotif(uid, n.key) }) {
                            Icon(Icons.Filled.Delete, "Hapus aktivitas", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                    // Inline banner ad every 5 activities.
                    if ((index + 1) % 5 == 0 && index + 1 < items.size) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            com.altomedia.beruang.ads.BannerAdBlock()
                        }
                    }
                }
            }
        }
    }
}
