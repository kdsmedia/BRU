package com.altomedia.beruang.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altomedia.beruang.data.AppConstants
import com.altomedia.beruang.data.AuthRepository
import com.altomedia.beruang.data.WalletRepository
import com.altomedia.beruang.ui.components.PrimaryButton
import com.altomedia.beruang.ui.components.showToast
import com.altomedia.beruang.ui.theme.ErrorRed
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * PIN entry (4 dots) — port of the web PIN modal. Used for both confirming a
 * transfer and creating a new PIN.
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    error: String,
    onSubmit: (String) -> Unit,
    onSetup: (() -> Unit)? = null,
) {
    val digits = remember { mutableStateListOf("", "", "", "") }
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain)
        Text(subtitle, fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            digits.forEachIndexed { i, _ ->
                OutlinedTextField(
                    value = digits[i],
                    onValueChange = { v ->
                        val d = v.filter { it.isDigit() }.take(1)
                        digits[i] = d
                        if (d.isNotEmpty() && i < 3) { /* focus next handled by UI */ }
                        if (digits.joinToString("").length == 4) onSubmit(digits.joinToString(""))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Text(error, color = ErrorRed, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        PrimaryButton("Kirim", onClick = { onSubmit(digits.joinToString("")) })
        if (onSetup != null) {
            Text(
                "Belum punya PIN? Buat di sini",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onSetup() }.padding(8.dp),
            )
        }
    }
}

/**
 * Create-PIN sheet — port of `openSetPinModal`.
 */
@Composable
fun SetPinSheet(onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var error by remember { mutableStateOf("") }
    val me = AuthRepository.currentUser()
    PinPad(
        title = "Buat PIN Transaksi",
        subtitle = "Buat 4 digit PIN untuk mengamankan transfer poin Anda.",
        error = error,
        onSubmit = { pin ->
            if (!pin.matches(Regex("\\d{4}"))) { error = "PIN harus 4 digit angka"; return@PinPad }
            if (me == null) { error = "Sesi habis"; return@PinPad }
            scope.launch {
                WalletRepository.setPin(me.uid, pin)
                showToast(context, "PIN berhasil dibuat")
                onSaved()
            }
        },
    )
}

/**
 * Amount entry sheet — port of `openAmountModal`.
 */
@Composable
fun AmountSheet(
    recipient: WalletRepository.Recipient,
    balance: Long,
    onContinue: (Long) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text("Transfer Poin", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain)
        Text("Masukkan nominal poin yang akan dikirim.", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
        RecipientRow(recipient.username, recipient.acctId)
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() } },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Text("Saldo Anda: $balance poin", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(top = 6.dp))
        if (error.isNotEmpty()) Text(error, color = ErrorRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        PrimaryButton(
            "Lanjut",
            onClick = {
                val amt = amount.toLongOrNull() ?: 0
                if (amt <= 0) { error = "Masukkan nominal valid"; return@PrimaryButton }
                if (amt > balance) { error = "Saldo tidak cukup"; return@PrimaryButton }
                error = ""
                onContinue(amt)
            },
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun RecipientRow(name: String, acctId: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMain)
        Text("  ID: $acctId", fontSize = 12.sp, color = TextMuted)
    }
}

/** Tier upgrade sheet — port of `openUpgradeModal` (list of tiers + buy/switch). */
@Composable
fun TierSheet(
    currentTier: String,
    balance: Long,
    onBuy: (String) -> Unit,
    onSwitch: (String) -> Unit,
) {
    val curIdx = AppConstants.tierIndex(currentTier)
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text("Naik Kelas Akun", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain)
        Text("Buka batas lebih besar dengan naik tier.", fontSize = 13.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
        AppConstants.TIERS.forEachIndexed { i, t ->
            val isCurrent = t.name == currentTier
            val isOwned = i <= curIdx
            val canBuy = i > curIdx && balance >= t.price
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(
                        if (isCurrent) Color(0xFFFFFBEB) else Color.White,
                        RoundedCornerShape(14.dp),
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(t.colorHex.toColor().copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(t.name.first().toString(), color = t.colorHex.toColor(), fontWeight = FontWeight.Bold) }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextMain)
                    val limitTxt = if (t.postLimit == Int.MAX_VALUE) "Tanpa batas" else "${t.postLimit}x/hari"
                    Text("$limitTxt · ${t.price} poin", fontSize = 12.sp, color = TextMuted)
                }
                when {
                    isCurrent -> Text("Aktif", color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    isOwned -> Text("Gunakan", color = com.altomedia.beruang.ui.theme.BrandYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onSwitch(t.name) })
                    canBuy -> Text("Naik Kelas", color = com.altomedia.beruang.ui.theme.BrandYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onBuy(t.name) })
                    else -> Text("Poin kurang", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun Long.toColor(): Color = Color(this.toInt())
