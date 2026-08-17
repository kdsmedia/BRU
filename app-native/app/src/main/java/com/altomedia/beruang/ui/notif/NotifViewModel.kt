package com.altomedia.beruang.ui.notif

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altomedia.beruang.data.Paths
import com.altomedia.beruang.data.RealtimeRepository
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.long
import com.altomedia.beruang.data.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotifItem(val key: String, val text: String, val timestamp: Long)

/**
 * Notifications — port of the web `listenNotifications`. Live-subscribes
 * `notifications/{uid}` and exposes the count + list (newest first).
 */
class NotifViewModel : ViewModel() {

    private val _items = MutableStateFlow<List<NotifItem>>(emptyList())
    val items: StateFlow<List<NotifItem>> = _items.asStateFlow()

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private var sub: com.altomedia.beruang.data.NodeSubscription? = null

    fun start(uid: String) {
        sub?.cancel()
        sub = RealtimeRepository.watch(Paths.notifications(uid), viewModelScope).also { s ->
            viewModelScope.launch {
                s.flow.collect { raw ->
                    val o = raw?.asObject()
                    val list = o?.entries?.map { (k, v) ->
                        val n = v.asObject()
                        NotifItem(k, n.str("text") ?: "", n.long("timestamp") ?: 0L)
                    }?.sortedByDescending { it.timestamp } ?: emptyList()
                    _items.value = list
                    _count.value = list.size
                }
            }
        }
    }

    override fun onCleared() { sub?.cancel() }
}
