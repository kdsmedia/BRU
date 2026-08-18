package com.altomedia.beruang.ui.chat

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted

/** Chat list — port of the web `#view-chat` / `renderChatList`. When [onClose]
 *  is supplied the screen renders as a full-screen overlay with a back button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    myUid: String,
    onOpenChat: (String, String) -> Unit,
    onVisitProfile: (String) -> Unit,
    onClose: (() -> Unit)? = null,
    vm: ChatListViewModel = viewModel(),
) {
    val users by vm.users.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var query by remember { mutableStateOf("") }

    // System back closes the chat list overlay.
    if (onClose != null) {
        BackHandler(enabled = true) { onClose() }
    }

    val visible = remember(users, query) {
        val others = users.filter { it.uid != myUid }
        if (query.isBlank()) others
        else others.filter { it.username.contains(query.trim(), ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBody)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextMain)
                }
            }
            Text(
                "Pesan",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextMain,
            )
            Text(
                "  · ${visible.size}",
                fontSize = 13.sp,
                color = TextMuted,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Cari teman…", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (visible.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
                        Text(
                            if (query.isBlank()) "Belum ada teman untuk diajak obrolan" else "Tidak ada teman yang cocok",
                            color = TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                return@PullToRefreshBox
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            items(visible, key = { it.uid }) { u ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(15.dp))
                        .clickable { onOpenChat(u.uid, u.username) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = u.photo,
                        contentDescription = null,
                        modifier = Modifier.size(50.dp).clip(CircleShape).clickable { onVisitProfile(u.uid) },
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.username, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)
                            if (u.isAdmin) Text(" 🛡️", fontSize = 12.sp)
                            if (u.isAi) Text(" AI", fontSize = 10.sp, color = Color(0xFF6D28D9), fontWeight = FontWeight.Bold)
                        }
                        Text("Ketuk untuk mengobrol", fontSize = 13.sp, color = TextMuted)
                    }
                    IconButton(onClick = { onVisitProfile(u.uid) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "Lihat profil",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        }
    }
}
