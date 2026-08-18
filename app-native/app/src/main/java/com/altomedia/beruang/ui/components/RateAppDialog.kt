package com.altomedia.beruang.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PREF_RATE = "rate_app"
private const val KEY_LAST_SHOWN = "last_shown_yyyymmdd"

/** Today's date as YYYYMMDD (device local). Used for the once-per-day gate. */
private fun todayStamp(): String {
    val c = java.util.Calendar.getInstance()
    return "%04d%02d%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}

/**
 * Shows a "Rate us on Play Store" popup at most ONCE PER DAY. Tapping "Beri
 * Rating" opens the app's Play Store listing; "Nanti saja" defers until the
 * next day. Day boundaries are device-local (YYYYMMDD stamp in prefs).
 */
@Composable
fun RateAppDialog() {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(PREF_RATE, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_SHOWN, null) != todayStamp()) {
            visible = true
        }
    }

    if (visible) {
        AlertDialog(
            onDismissRequest = { dismiss(context) { visible = false } },
            title = { Text("Berikan Rating", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        repeat(5) {
                            Icon(Icons.Filled.Star, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(28.dp))
                        }
                    }
                    Text(
                        "Kamu menyukai BERUANG? Ratingmu sangat membantu kami berkembang. Beri nilai 5 bintang di Play Store, ya!",
                        fontSize = 13.sp,
                        color = Color(0xFF444444),
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { dismiss(context) { visible = false } }) {
                        Text("Nanti saja", color = Color(0xFF888888))
                    }
                    Surface(
                        color = Color(0xFFF5C518),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                    ) {
                        TextButton(onClick = {
                            dismiss(context) { visible = false }
                            openPlayStore(context)
                        }) { Text("Beri Rating", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }
                }
            },
        )
    }
}

private fun dismiss(context: Context, hide: () -> Unit) {
    context.getSharedPreferences(PREF_RATE, Context.MODE_PRIVATE)
        .edit().putString(KEY_LAST_SHOWN, todayStamp()).apply()
    hide()
}

/** Opens the app's Play Store page (falls back to browser if Play is absent). */
private fun openPlayStore(context: Context) {
    val pkg = context.packageName
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
