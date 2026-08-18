package com.altomedia.beruang.ui.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.altomedia.beruang.data.AppConstants
import com.altomedia.beruang.data.NodesRepository
import com.altomedia.beruang.data.Paths
import com.altomedia.beruang.data.PostRepository
import com.altomedia.beruang.data.StorageRepository
import com.altomedia.beruang.data.WalletRepository
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.long
import com.altomedia.beruang.data.str
import com.altomedia.beruang.ui.auth.AuthUser
import com.altomedia.beruang.ui.components.PrimaryButton
import com.altomedia.beruang.ui.components.showToast
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * Upload view — port of the web `#view-upload`. Image is optional; caption is
 * optional; at least one must be present. Enforces the daily post limit and
 * awards points on success — mirrors `uploadPost`.
 */
@Composable
fun UploadScreen(
    me: AuthUser,
    onPosted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgBody)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Posting Baru", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain)

        // Upload area (dashed).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFF8FAFC))
                .clickable { picker.launch("image/*") },
            contentAlignment = Alignment.Center,
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CloudUpload, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(28.dp))
                    Text("Ketuk untuk unggah gambar (opsional)", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        if (imageUri != null) {
            Icon(
                Icons.Filled.Delete,
                "Hapus gambar",
                tint = Color(0xFFEF4444),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(28.dp)
                    .clickable { imageUri = null },
            )
        }

        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            placeholder = { Text("Tulis keterangan...") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            minLines = 3,
        )

        PrimaryButton(
            "Terbitkan Postingan",
            onClick = {
                if (busy) return@PrimaryButton
                if (imageUri == null && caption.isBlank()) {
                    showToast(context, "Tulis sesuatu atau pilih gambar untuk membuat postingan.")
                    return@PrimaryButton
                }
                scope.launch {
                    // Check tier daily limit (mirrors checkLimit('posts')).
                    val usage = WalletRepository.loadUsage(me.uid)
                    val tierName = NodesRepository.readValue(Paths.wallet(me.uid))?.asObject()?.str("tier") ?: "Star"
                    val c = WalletRepository.checkLimit(tierName, usage, "posts")
                    if (!c.ok) {
                        showToast(context, "Batas posting harian tercapai (${c.limit}x untuk $tierName). Naik kelas akun untuk lebih.")
                        return@launch
                    }
                    busy = true
                    val imageUrl = if (imageUri != null) {
                        StorageRepository.uploadImage(context, imageUri!!, "posts", maxWidth = 800, quality = 0.7f, uid = me.uid)
                    } else null
                    PostRepository.createPost(me, caption.trim(), imageUrl)
                    WalletRepository.recordUsage(me.uid, usage, "posts")
                    WalletRepository.awardPoints(me.uid, AppConstants.POINTS_POST, "post")
                    com.altomedia.beruang.data.BonusRepository.recordPost(me.uid)
                    showToast(context, "+${AppConstants.POINTS_POST} poin (posting)")
                    busy = false
                    imageUri = null
                    caption = ""
                    onPosted()
                }
            },
            loading = busy,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
