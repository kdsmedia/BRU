package com.altomedia.beruang.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altomedia.beruang.data.AppConstants

/**
 * Wallet card — port of the web `.wallet-card` (dark gradient, balance,
 * account id, tier pill, QR/scan/history buttons).
 */
@Composable
fun WalletCard(
    state: WalletState,
    myName: String,
    onShowMyQr: () -> Unit,
    onScanQr: () -> Unit,
    onHistory: () -> Unit,
    onUpgrade: () -> Unit,
) {
    val tier = AppConstants.tier(state.tier)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF334155))),
                RoundedCornerShape(20.dp),
            )
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "BERUANG Wallet",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                // Tier pill
                Row(
                    modifier = Modifier
                        .background(tier.colorHex.toComposeColor().copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = tier.colorHex.toComposeColor(), modifier = Modifier.size(14.dp))
                    Text(tier.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                }
            }
            Text(
                "${state.balance} poin",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                "ID Akun: ${state.acctId}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            Text(myName, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WalletBtn("QR Saya", Icons.Filled.QrCode, onShowMyQr, Modifier.weight(1f))
                WalletBtn("Scan QR", Icons.Filled.QrCodeScanner, onScanQr, Modifier.weight(1f))
                WalletBtn("Riwayat", Icons.Filled.Receipt, onHistory, Modifier.weight(1f))
                WalletBtn("Kelas", Icons.Filled.Star, onUpgrade, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WalletBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun Long.toComposeColor(): Color = Color(this.toInt())
