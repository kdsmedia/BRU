package com.altomedia.beruang.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altomedia.beruang.data.Paths
import com.altomedia.beruang.data.RealtimeRepository
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.asString
import com.altomedia.beruang.data.long
import com.altomedia.beruang.data.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class WalletState(
    val balance: Long = 0,
    val acctId: String = "000000",
    val tier: String = "Star",
    val role: String = "user",
    val pinSet: Boolean = false,
    val history: List<TxnEntry> = emptyList(),
)

data class TxnEntry(
    val type: String,
    val amount: Long,
    val label: String,
    val sub: String,
    val sign: String, // "+" or "-"
    val timestamp: Long,
)

/**
 * Live wallet state — port of the web `listenMyWallet`. Exposes balance, acctId,
 * tier, role, PIN-set flag, and transaction history.
 */
class WalletViewModel : ViewModel() {

    private val _state = MutableStateFlow(WalletState())
    val state: StateFlow<WalletState> = _state.asStateFlow()

    private var sub: com.altomedia.beruang.data.NodeSubscription? = null
    private var pinSub: com.altomedia.beruang.data.NodeSubscription? = null

    fun start(uid: String) {
        sub?.cancel(); pinSub?.cancel()
        sub = RealtimeRepository.watch(Paths.wallet(uid), viewModelScope).also { s ->
            viewModelScope.launch { s.stateFlow.collect { rebuild(it?.asObject()) } }
        }
        pinSub = RealtimeRepository.watch(Paths.walletPin(uid), viewModelScope).also { s ->
            viewModelScope.launch { s.stateFlow.collect { v -> _state.value = _state.value.copy(pinSet = !v.asString().isNullOrBlank()) } }
        }
    }

    private fun rebuild(w: JsonObject?) {
        if (w == null) return
        val hist = (w["history"] as? JsonObject)?.entries?.map { (_, t) ->
            val o = t.asObject()
            val type = o.str("type") ?: "reward"
            val amount = o.long("amount") ?: 0
            val (label, sub, sign) = when (type) {
                "reward" -> Triple("Hadiah: ${o.str("reason") ?: "aktivitas"}", "", "+")
                "transfer_in" -> Triple("Diterima dari ${o.str("fromName") ?: "Pengguna"}", "ID: ${o.str("fromAcctId") ?: ""}", "+")
                "transfer_out" -> Triple("Dikirim ke ${o.str("toName") ?: "Pengguna"}", "ID: ${o.str("toAcctId") ?: ""}", "-")
                "upgrade" -> Triple("Naik kelas ke ${o.str("tier") ?: "kelas"}", "", "-")
                else -> Triple(type, "", "+")
            }
            TxnEntry(type, amount, label, sub, sign, o.long("timestamp") ?: 0)
        }?.sortedByDescending { it.timestamp } ?: emptyList()
        _state.value = WalletState(
            balance = w.long("balance") ?: 0,
            acctId = w.str("acctId") ?: "000000",
            tier = w.str("tier") ?: "Star",
            role = w.str("role") ?: "user",
            pinSet = _state.value.pinSet,
            history = hist,
        )
    }

    override fun onCleared() {
        sub?.cancel(); pinSub?.cancel()
    }
}
