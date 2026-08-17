package com.altomedia.beruang.ui.admin

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Ban
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.altomedia.beruang.data.AdminRepository
import com.altomedia.beruang.data.AppConstants
import com.altomedia.beruang.data.NodesRepository
import com.altomedia.beruang.data.Paths
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.str
import com.altomedia.beruang.ui.components.showToast
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.ErrorRed
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import kotlinx.coroutines.launch

/** Admin panel — port of the web `renderAdminUserList` + admin actions. */
@Composable
fun AdminScreen(myUid: String, onVisitProfile: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var blocked by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var pending by remember { mutableStateOf<AdminAction?>(null) }

    LaunchedEffect(myUid) {
        val raw = NodesRepository.readValue(Paths.users())?.asObject()
        users = raw?.entries?.map { (uid, u) ->
            val o = u.asObject()
            AdminUser(
                uid = uid,
                username = o.str("username") ?: "Pengguna",
                photo = o.str("photo") ?: AppConstants.DEFAULT_AVATAR,
                phone = o.str("phone") ?: "-",
                role = o.str("role"),
            )
        } ?: emptyList()
        blocked = AdminRepository.loadBlocked()
    }

    Column(modifier = Modifier.fillMaxSize().background(BgBody)) {
        Text(
            "Kelola Pengguna",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TextMain,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users, key = { it.uid }) { u ->
                val isMe = u.uid == myUid
                val isThisAdmin = u.role == "admin" || isMe
                val isBlocked = blocked[u.uid] == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = u.photo,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onVisitProfile(u.uid) },
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.username, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMain)
                            if (u.role == "admin") Text(" 🛡️", fontSize = 12.sp)
                            if (isBlocked) Text(" (diblokir)", fontSize = 10.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                        }
                        Text("HP: ${u.phone}", fontSize = 12.sp, color = TextMuted)
                    }
                    when {
                        isMe -> Text("Anda", fontSize = 12.sp, color = TextMuted)
                        isThisAdmin -> Text("Admin", fontSize = 12.sp, color = BrandYellow, fontWeight = FontWeight.Bold)
                        else -> Row {
                            if (isBlocked) {
                                IconBtn(Icons.Filled.LockOpen, "Buka blokir") { pending = AdminAction.Unblock(u) }
                            } else {
                                IconBtn(Icons.Filled.Ban, "Blokir") { pending = AdminAction.Block(u) }
                            }
                            IconBtn(Icons.Filled.Delete, "Hapus akun", danger = true) { pending = AdminAction.Delete(u) }
                        }
                    }
                }
            }
        }
    }

    pending?.let { action ->
        val (title, msg) = when (action) {
            is AdminAction.Block -> "Blokir akun" to "Blokir akun \"${action.user.username}\"? Pengguna tidak dapat masuk lagi."
            is AdminAction.Unblock -> "Buka blokir" to "Buka blokir akun ini?"
            is AdminAction.Delete -> "Hapus akun" to "Hapus akun \"${action.user.username}\"? Semua data pengguna (profil, dompet, postingan) akan dihapus permanen."
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(title) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    val u = action.user
                    scope.launch {
                        when (action) {
                            is AdminAction.Block -> { AdminRepository.blockUser(u.uid); showToast(context, "Pengguna diblokir") }
                            is AdminAction.Unblock -> { AdminRepository.unblockUser(u.uid); showToast(context, "Blokir dibuka") }
                            is AdminAction.Delete -> { AdminRepository.deleteUserData(u.uid); showToast(context, "Akun dihapus") }
                        }
                        blocked = AdminRepository.loadBlocked()
                        if (action is AdminAction.Delete) {
                            users = users.filter { it.uid != u.uid }
                        }
                    }
                    pending = null
                }) { Text("Ya", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, danger: Boolean = false, onClick: () -> Unit) {
    Icon(
        icon,
        contentDescription = desc,
        tint = if (danger) ErrorRed else TextMuted,
        modifier = Modifier.clickable { onClick() }.padding(6.dp).size(22.dp),
    )
}

data class AdminUser(val uid: String, val username: String, val photo: String, val phone: String, val role: String?)

sealed class AdminAction(val user: AdminUser) {
    class Block(user: AdminUser) : AdminAction(user)
    class Unblock(user: AdminUser) : AdminAction(user)
    class Delete(user: AdminUser) : AdminAction(user)
}
