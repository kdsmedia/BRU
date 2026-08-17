package com.altomedia.beruang.ui.wallet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altomedia.beruang.data.WalletRepository
import com.altomedia.beruang.ui.components.PrimaryButton
import com.altomedia.beruang.ui.components.showToast
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMuted
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * QR scanner + manual lookup — port of the web `openQRScanner` / `manualLookup`
 * / `startScanner`. Launches the ZXing camera scanner; on a successful scan
 * (or manual entry) resolves the recipient by acctId and opens the amount
 * sheet, then the PIN sheet, then performs the transfer.
 */
@Composable
fun QrScannerSheet(
    meUid: String,
    myName: String,
    myAcctId: String,
    myBalance: Long = 0,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manualAcctId by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf<WalletRepository.Recipient?>(null) }
    var amount by remember { mutableStateOf<Long?>(null) }

    // ZXing scanner launcher.
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val contents = result.contents
        if (!contents.isNullOrEmpty()) {
            // Payload may be JSON {acctId, uid} or a plain acctId.
            val acctId = try {
                val obj = Json.decodeFromString< kotlinx.serialization.json.JsonObject>(contents)
                obj["acctId"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    ?: obj["uid"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    ?: contents
            } catch (_: Exception) {
                contents // plain acctId string
            }
            scope.launch { resolveRecipient(acctId, context) { recipient = it } }
        }
    }

    // Scan result → amount sheet → PIN sheet → transfer.
    val rcpt = recipient
    if (rcpt != null && amount == null) {
        AlertDialog(
            onDismissRequest = { recipient = null },
            confirmButton = {},
            dismissButton = {},
            text = {
                AmountSheet(
                    recipient = rcpt,
                    balance = myBalance,
                    onContinue = { amt -> amount = amt },
                )
            },
        )
    }
    val amt = amount
    if (amt != null && rcpt != null) {
        AlertDialog(
            onDismissRequest = { amount = null },
            confirmButton = {},
            dismissButton = {},
            text = {
                PinPad(
                    title = "Konfirmasi PIN",
                    subtitle = "Masukkan PIN transaksi",
                    error = "",
                    onSubmit = { pin ->
                        scope.launch {
                            val err = WalletRepository.transfer(
                                senderUid = meUid,
                                senderName = myName,
                                senderAcctId = myAcctId,
                                recipient = rcpt,
                                amount = amt,
                                enteredPin = pin,
                            )
                            if (err == null) {
                                showToast(context, "Transfer ${"%,d".format(amt)} poin berhasil")
                                amount = null
                                recipient = null
                                onDismiss()
                            } else {
                                showToast(context, err)
                            }
                        }
                    },
                )
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan QR Penerima", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Arahkan kamera ke QR akun tujuan untuk transfer poin.", fontSize = 12.sp, color = TextMuted)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PrimaryButton(
                        "Buka Kamera",
                        onClick = {
                            val opts = ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Arahkan ke QR akun tujuan")
                                .setBeepEnabled(true)
                                .setOrientationLocked(false)
                            scanner.launch(opts)
                        },
                        icon = Icons.Filled.CameraAlt,
                    )
                }
                Text("Tidak bisa scan? Masukkan ID akun manual.", fontSize = 12.sp, color = TextMuted)
                OutlinedTextField(
                    value = manualAcctId,
                    onValueChange = { manualAcctId = it.filter { c -> c.isDigit() }.take(6) },
                    placeholder = { Text("6-digit ID Akun") },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (manualAcctId.length == 6) {
                    scope.launch { resolveRecipient(manualAcctId, context) { recipient = it } }
                } else {
                    showToast(context, "Masukkan 6-digit ID Akun")
                }
            }) { Text("Lanjut", color = BrandYellow, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = TextMuted) } },
    )
}

private suspend fun resolveRecipient(
    acctId: String,
    context: android.content.Context,
    onResult: (WalletRepository.Recipient?) -> Unit,
) {
    val r = WalletRepository.findUserByAcctId(acctId)
    if (r == null) showToast(context, "Penerima tidak ditemukan")
    onResult(r)
}
