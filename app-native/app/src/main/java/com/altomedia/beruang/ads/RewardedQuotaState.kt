package com.altomedia.beruang.ads

import android.app.Activity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.altomedia.beruang.data.WalletRepository
import com.altomedia.beruang.ui.components.showToast
import kotlinx.coroutines.launch

/**
 * Rewarded-ad quota controller — port of the web `promptRewardedForQuota` +
 * `grantAdQuota`. Holds the dialog state and the pending action; call
 * [request] when a daily limit is reached.
 *
 * Usage: `val quota = rememberRewardedQuotaPrompt(activity)` then render
 * `quota.Render(uid)`. Call `quota.request("comments") { granted -> ... }`.
 */
class RewardedQuotaState(
    private val activity: Activity,
) {
    var action by mutableStateOf<String?>(null)
        private set
    private var onResult by mutableStateOf<((Boolean) -> Unit)?>(null)

    fun request(action: String, result: (Boolean) -> Unit) {
        this.action = action
        this.onResult = result
    }

    private fun dismiss(result: Boolean) {
        onResult?.invoke(result)
        onResult = null
        action = null
    }

    @Composable
    fun Render(uid: String) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val act = action
        if (act != null) {
            val label = if (act == "posts") "postingan" else "komentar"
            AlertDialog(
                onDismissRequest = { dismiss(false) },
                title = { Text("Batas $label harian tercapai") },
                text = { Text("Tonton iklan singkat untuk mendapatkan +1 kesempatan $label hari ini.") },
                confirmButton = {
                    TextButton(onClick = {
                        val cb = onResult
                        action = null
                        onResult = null
                        scope.launch {
                            showToast(context, "Memuat iklan...")
                            val rewarded = AdMobManager.showRewarded(activity)
                            if (!rewarded) {
                                showToast(context, "Iklan tidak selesai. Coba lagi.")
                                cb?.invoke(false)
                            } else {
                                val usage = WalletRepository.loadUsage(uid)
                                WalletRepository.grantAdQuota(uid, usage, act)
                                showToast(context, "+1 kesempatan $label ditambahkan!")
                                cb?.invoke(true)
                            }
                        }
                    }) { Text("Tonton Iklan") }
                },
                dismissButton = {
                    TextButton(onClick = { dismiss(false) }) {
                        Text("Nanti saja", color = Color(0xFF64748B))
                    }
                },
            )
        }
    }
}

@Composable
fun rememberRewardedQuotaPrompt(activity: Activity): RewardedQuotaState =
    remember { RewardedQuotaState(activity) }
