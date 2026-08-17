package com.altomedia.beruang.ui.profile

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altomedia.beruang.data.NodesRepository
import com.altomedia.beruang.data.Paths
import com.altomedia.beruang.data.StorageRepository
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.long
import com.altomedia.beruang.data.str
import com.altomedia.beruang.ui.components.PrimaryButton
import com.altomedia.beruang.ui.components.showToast
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.ErrorRed
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---- info modal (about / privacy / disclaimer / terms) --------------

/** Simple info modal — port of the web `openInfoModal(title, html)`. */
@Composable
fun InfoModal(title: String, content: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(content, fontSize = 13.sp, color = Color(0xFF333333), lineHeight = 19.sp)
            }
        },
    )
}

const val ABOUT_TEXT = """BERUANG — Terhubung & Berbagi.

BERUANG adalah aplikasi sosial media berbasis poin yang menggabungkan berbagi momen, interaksi sosial, dan sistem dompet poin virtual.

Fitur Utama
• Posting foto & caption
• Suka & berkomentar
• Tambah teman & chat pribadi
• Dompet poin & transfer via QR
• Sistem tier akun
• Program referral

Sistem Poin
Komentar +50, Posting +20, Tambah teman +10, Suka +2, Referral +500 (atasan & bawahan).

Versi aplikasi: 1.0.0"""

const val PRIVACY_TEXT = """Privasi Anda penting bagi kami. Kebijakan ini menjelaskan bagaimana BERUANG mengumpulkan, menggunakan, dan melindungi data Anda.

Data yang Dikumpulkan
Nama, nomor HP, foto profil, dan aktivitas dalam aplikasi (posting, komentar, poin, transaksi).

Penggunaan Data
Data digunakan untuk menjalankan layanan sosial, sistem poin, dan transaksi antar pengguna. Data tidak dijual ke pihak ketiga.

Keamanan
Data disimpan pada infrastruktur Supabase dengan aturan keamanan. PIN transaksi di-hash dan tidak ditampilkan kepada siapapun.

Hak Pengguna
Anda dapat mengubah profil kapan saja. Penghapusan akun dapat dilakukan melalui kontak dukungan."""

const val DISCLAIMER_TEXT = """Dengan menggunakan BERUANG, Anda menyetujui sangkalan berikut:

Poin Virtual
Poin dalam BERUANG adalah mata uang virtual yang tidak memiliki nilai riil dan tidak dapat ditukar dengan uang fisik atau aset nyata.

Tanggung Jawab Pengguna
Pengguna bertanggung jawab penuh atas konten yang diunggah dan transaksi yang dilakukan. BERUANG tidak bertanggung jawab atas kehilangan poin akibat kelalaian pengguna (mis. membagikan PIN).

Konten Pengguna
BERUANG tidak menyensor konten secara aktif. Dilarang mengunggah konten melanggar hukum, SARA, atau pornografi.

Layanan "Sebagaimana Adanya"
Layanan disediakan tanpa jaminan. Kami berhak mengubah atau menghentikan layanan sewaktu-waktu."""

const val TERMS_TEXT = """Dengan mendaftar dan menggunakan BERUANG, Anda menyetujui syarat berikut:

1. Akun
Anda wajib menggunakan nomor HP yang valid dan menjaga kerahasiaan sandi serta PIN transaksi.

2. Penggunaan
Dilarang melakukan tindakan penyalahgunaan, termasuk farming poin otomatis, akun ganda untuk manipulasi referral, atau spam.

3. Transaksi
Transfer poin bersifat final. Konfirmasi penerima sebelum mengirim. Pembukaan PIN adalah tanggung jawab pengguna.

4. Tier & Naik Kelas
Pembelian upgrade tier tidak dapat dikembalikan (non-refundable).

5. Larangan
Kami berhak menonaktifkan akun yang melanggar syarat ini tanpa pemberitahuan terlebih dahulu."""

// ---- edit profile --------------------------------------------------

/** Edit profile modal — port of `openEditProfileModal` / `saveEditProfile`. */
@Composable
fun EditProfileSheet(
    uid: String,
    currentName: String,
    currentPhoto: String,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(currentName) }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var photoUrl by remember { mutableStateOf(currentPhoto) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        photoUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil Lengkap", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val model = photoUri ?: photoUrl
                    if (model != null) {
                        coil.compose.AsyncImage(
                            model = model,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(CircleShape).clickable { picker.launch("image/*") },
                        )
                    } else {
                        Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(80.dp).clickable { picker.launch("image/*") }, tint = BrandYellow)
                    }
                }
                TextButton(onClick = { picker.launch("image/*") }) { Text("Ganti Foto", color = BrandYellow) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                "Simpan",
                onClick = {
                    if (busy) return@PrimaryButton
                    scope.launch {
                        busy = true
                        val finalPhoto = if (photoUri != null) {
                            StorageRepository.uploadImage(context, photoUri!!, "avatars", maxWidth = 400, quality = 0.7f, uid = uid)
                        } else photoUrl
                        NodesRepository.update(NodesRepository.ref(Paths.user(uid)), buildJsonObject {
                            put("username", name.trim())
                            put("photo", finalPhoto)
                        })
                        busy = false
                        showToast(context, "Profil diperbarui")
                        onSaved()
                    }
                },
                loading = busy,
                modifier = Modifier,
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = TextMuted) } },
    )
}

// ---- settings ------------------------------------------------------

/** Settings modal — port of `openSettingsModal`. */
@Composable
fun SettingsSheet(
    onEditProfile: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    isAdmin: Boolean = false,
    onAdmin: () -> Unit = {},
) {
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    if (info != null) {
        InfoModal(info!!.first, info!!.second) { info = null }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsRow(Icons.Filled.AccountCircle, "Pengaturan Akun", "Ubah nama, foto, dan profil") { onEditProfile() }
                if (isAdmin) {
                    SettingsRow(Icons.Filled.Shield, "Kelola Pengguna", "Panel admin") { onAdmin() }
                }
                SettingsRow(Icons.Filled.Info, "Tentang", "Tentang BERUANG") { info = "Tentang BERUANG" to ABOUT_TEXT }
                SettingsRow(Icons.Filled.PrivacyTip, "Kebijakan Privasi", "Kebijakan privasi") { info = "Kebijakan Privasi" to PRIVACY_TEXT }
                SettingsRow(Icons.Filled.Warning, "Sangkalan", "Sangkalan") { info = "Sangkalan" to DISCLAIMER_TEXT }
                SettingsRow(Icons.Filled.Info, "Syarat & Ketentuan", "Syarat & ketentuan") { info = "Syarat & Ketentuan" to TERMS_TEXT }
            }
        },
        confirmButton = {
            TextButton(onClick = onLogout) {
                Icon(Icons.Filled.Logout, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                Text(" Keluar", color = ErrorRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = TextMuted) } },
    )
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = BrandYellow, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMain)
            Text(sub, fontSize = 12.sp, color = TextMuted)
        }
    }
}

// ---- my QR ---------------------------------------------------------

/** My-QR modal — port of `openMyQRModal`. Renders a ZXing QR bitmap. */
@Composable
fun QrSheet(uid: String, acctId: String, name: String, balance: Long, onDismiss: () -> Unit) {
    val payload = remember(acctId, uid) {
        buildJsonObject { put("acctId", acctId); put("uid", uid) }.toString()
    }
    val qrBitmap = rememberQrBitmap(payload, 400)
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text("Kode QR Akun Saya", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tunjukkan QR ini untuk menerima transfer poin.", fontSize = 12.sp, color = TextMuted)
                Box(modifier = Modifier.padding(vertical = 12.dp)) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR",
                            modifier = Modifier.size(200.dp),
                        )
                    } else {
                        Box(modifier = Modifier.size(200.dp).background(Color(0xFFF1F5F9)))
                    }
                }
                Text(acctId, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Text(name, fontSize = 13.sp, color = TextMuted)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("acctId", acctId))
                        showToast(context, "ID Akun disalin")
                    }.padding(top = 4.dp),
                ) {
                    Icon(Icons.Filled.ContentCopy, null, tint = BrandYellow, modifier = Modifier.size(14.dp))
                    Text(" Salin ID", fontSize = 12.sp, color = BrandYellow)
                }
                Text("Saldo: ${"%,d".format(balance)} poin", fontSize = 12.sp, color = TextMuted)
            }
        },
    )
}

/** Generate a QR bitmap off the main thread. */
@Composable
fun rememberQrBitmap(content: String, sizePx: Int): Bitmap? {
    var bmp by remember(content, sizePx) { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(content, sizePx) {
        scope.launch {
            bmp = withContext(Dispatchers.Default) {
                val matrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
                val w = matrix.width; val h = matrix.height
                val pixels = IntArray(w * h)
                for (y in 0 until h) for (x in 0 until w) {
                    pixels[y * w + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, w, 0, 0, w, h) }
            }
        }
    }
    return bmp
}

// ---- transaction history -------------------------------------------

/** Transaction history modal — port of `openTxnHistoryModal`. */
@Composable
fun TxnHistorySheet(uid: String, onDismiss: () -> Unit) {
    var history by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(uid) {
        val raw = NodesRepository.readValue(Paths.walletHistory(uid))?.asObject()
        history = raw?.entries?.map { (k, v) ->
            val o = v.asObject()
            HistoryItem(o.str("type") ?: "", o.long("amount") ?: 0L, o.str("reason") ?: "", o.long("timestamp") ?: 0L)
        }?.sortedByDescending { it.timestamp } ?: emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text("Riwayat Transaksi", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (history.isEmpty()) {
                    Text("Belum ada transaksi.", color = TextMuted, fontSize = 13.sp)
                }
                history.forEach { h ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(h.reason.ifBlank { h.type }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextMain)
                            Text(h.type, fontSize = 11.sp, color = TextMuted)
                        }
                        Text(
                            "${if (h.amount >= 0) "+" else ""}%,d".format(h.amount),
                            color = if (h.amount >= 0) BrandYellow else ErrorRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        },
    )
}

data class HistoryItem(val type: String, val amount: Long, val reason: String, val timestamp: Long)
