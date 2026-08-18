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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import com.altomedia.beruang.data.WalletRepository
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.str
import com.altomedia.beruang.ui.components.showToast
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.ErrorRed
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import androidx.compose.ui.text.input.KeyboardType
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
                                IconBtn(Icons.Filled.Block, "Blokir") { pending = AdminAction.Block(u) }
                            }
                            IconBtn(Icons.Filled.Savings, "Atur saldo") { pending = AdminAction.AdjustBalance(u) }
                            IconBtn(Icons.Filled.Delete, "Hapus akun", danger = true) { pending = AdminAction.Delete(u) }
                        }
                    }
                }
            }
        }
    }

    // Adjust-balance dialog: pilih tambah/kurangi + nominal + catatan + OKE.
    val adjust = pending as? AdminAction.AdjustBalance
    if (adjust != null) {
        val u = adjust.user
        var balance by remember(u.uid) { mutableStateOf<Long?>(null) }
        var amountText by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("Penyesuaian admin") }
        var isAdd by remember { mutableStateOf(true) } // true = tambah, false = kurangi
        var busy by remember { mutableStateOf(false) }
        LaunchedEffect(u.uid) { balance = WalletRepository.readBalance(u.uid) }
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("Atur Saldo — ${u.username}") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Saldo saat ini: ${balance?.let { "%,d".format(it) } ?: "…"} poin",
                        fontSize = 13.sp,
                        color = TextMuted,
                    )
                    // Pilihan: Tambah / Kurangi (segmen).
                    val modes = listOf(
                        Triple(true, "Tambah", Icons.Filled.Add),
                        Triple(false, "Kurangi", Icons.Filled.Remove),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgBody),
                    ) {
                        modes.forEach { (mode, label, icon) ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isAdd == mode) BrandYellow else Color.Transparent)
                                    .clickable(enabled = !busy) { isAdd = mode }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(icon, contentDescription = null, tint = if (isAdd == mode) Color.White else TextMuted, modifier = Modifier.size(18.dp))
                                Text(" $label", color = if (isAdd == mode) Color.White else TextMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { s -> amountText = s.filter { c -> c.isDigit() }.take(9) },
                        label = { Text("Nominal (poin)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { amountText = "100" }, enabled = !busy) { Text("+100") }
                        TextButton(onClick = { amountText = "500" }, enabled = !busy) { Text("+500") }
                        TextButton(onClick = { amountText = "1000" }, enabled = !busy) { Text("+1.000") }
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Catatan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (busy) return@Button
                        val amt = amountText.toLongOrNull() ?: 0L
                        if (amt <= 0) { showToast(context, "Masukkan nominal valid"); return@Button }
                        busy = true
                        scope.launch {
                            val delta = if (isAdd) amt else -amt
                            val newBal = AdminRepository.adjustBalance(u.uid, delta, reason)
                            if (newBal != null) {
                                showToast(context, "Saldo ${if (isAdd) "ditambah" else "dikurangi"} $amt → ${"%,d".format(newBal)} poin")
                                balance = newBal
                            } else {
                                showToast(context, "Gagal memperbarui saldo")
                            }
                            busy = false
                            pending = null
                        }
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandYellow),
                ) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text("OKE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { if (!busy) pending = null }, enabled = !busy) { Text("Batal") } },
        )
    }

    pending?.let { action ->
        // AdjustBalance has its own dialog above; skip the generic confirm here.
        if (action is AdminAction.AdjustBalance) return@let
        val (title, msg) = when (action) {
            is AdminAction.Block -> "Blokir akun" to "Blokir akun \"${action.user.username}\"? Pengguna tidak dapat masuk lagi."
            is AdminAction.Unblock -> "Buka blokir" to "Buka blokir akun ini?"
            is AdminAction.Delete -> "Hapus akun" to "Hapus akun \"${action.user.username}\"? Semua data pengguna (profil, dompet, postingan) akan dihapus permanen."
            is AdminAction.AdjustBalance -> return@let
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
                            is AdminAction.AdjustBalance -> Unit // handled in its own dialog
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
    class AdjustBalance(user: AdminUser) : AdminAction(user)
}
