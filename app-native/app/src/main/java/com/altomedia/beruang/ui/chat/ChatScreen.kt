package com.altomedia.beruang.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.altomedia.beruang.data.ChatRepository
import com.altomedia.beruang.ui.auth.AuthUser
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * Chat window — port of the web chat overlay (`openChat` / `sendMsg` /
 * `deleteChatMsg` / `clearChat`). Bubbles align right (mine) or left (theirs).
 */
@Composable
fun ChatScreen(
    me: AuthUser,
    targetUid: String,
    targetName: String,
    onBack: () -> Unit,
    onVisitProfile: (String) -> Unit,
    vm: ChatViewModel = viewModel(),
) {
    val messages by vm.messages.collectAsState()
    val scope = rememberCoroutineScope()
    val chatId = remember(targetUid) { ChatRepository.chatId(me.uid, targetUid) }
    var input by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ChatMessage?>(null) }

    LaunchedEffect(chatId) { vm.open(chatId) }

    Column(modifier = Modifier.fillMaxSize().background(BgBody).imePadding()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = TextMain)
            }
            Text(
                "$targetName",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextMain,
                modifier = Modifier.weight(1f).clickable { onVisitProfile(targetUid) },
            )
            IconButton(onClick = {
                scope.launch { ChatRepository.clearChat(chatId); onBack() }
            }) {
                Icon(Icons.Filled.Delete, "Hapus percakapan", tint = TextMuted)
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.key }) { msg ->
                val isMine = msg.uid == me.uid
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Text(
                        msg.text,
                        color = if (isMine) Color.White else TextMain,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .background(
                                if (isMine) BrandYellow else Color.White,
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { if (isMine) pendingDelete = msg }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .width(200.dp),
                    )
                }
            }
        }

        // Input
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Tulis pesan...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (input.isNotBlank()) {
                    val t = input; input = ""
                    scope.launch { ChatRepository.send(me, chatId, t) }
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = BrandYellow)
            }
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus pesan") },
            text = { Text("Hapus pesan ini?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete?.let { msg ->
                        scope.launch { ChatRepository.deleteMessage(chatId, msg.key) }
                    }
                    pendingDelete = null
                }) { Text("Hapus", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } },
        )
    }
}
