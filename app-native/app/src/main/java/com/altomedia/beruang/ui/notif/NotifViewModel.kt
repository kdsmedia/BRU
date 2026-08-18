package com.altomedia.beruang.ui.notif

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altomedia.beruang.data.NodesRepository
import com.altomedia.beruang.data.Paths
import com.altomedia.beruang.data.RealtimeRepository
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.bool
import com.altomedia.beruang.data.long
import com.altomedia.beruang.data.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class NotifItem(val key: String, val text: String, val timestamp: Long, val read: Boolean = false)

/**
 * Notifications — port of the web `listenNotifications`. Live-subscribes
 * `notifications/{uid}` and exposes the count + list (newest first).
 */
class NotifViewModel : ViewModel() {

    private val _items = MutableStateFlow<List<NotifItem>>(emptyList())
    val items: StateFlow<List<NotifItem>> = _items.asStateFlow()

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    /** Unread count — drives the green badge on the nav tab. */
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private var sub: com.altomedia.beruang.data.NodeSubscription? = null

    fun start(uid: String) {
        sub?.cancel()
        sub = RealtimeRepository.watch(Paths.notifications(uid), viewModelScope).also { s ->
            viewModelScope.launch {
                s.stateFlow.collect { raw ->
                    val o = raw?.asObject()
                    val list = o?.entries?.map { (k, v) ->
                        val n = v.asObject()
                        NotifItem(k, n.str("text") ?: "", n.long("timestamp") ?: 0L, n.bool("read") ?: false)
                    }?.sortedByDescending { it.timestamp } ?: emptyList()
                    _items.value = list
                    _count.value = list.size
                    _unread.value = list.count { !it.read }
                }
            }
        }
    }

    /** Mark a single activity as read (clears its unread/green state). */
    fun markRead(uid: String, key: String) {
        viewModelScope.launch {
            NodesRepository.update(
                NodesRepository.ref("${Paths.notifications(uid)}/$key"),
                JsonObject(mapOf("read" to JsonPrimitive(true))),
            )
        }
    }

    /** Delete a single activity so the list doesn't grow unbounded. */
    fun deleteNotif(uid: String, key: String) {
        viewModelScope.launch {
            NodesRepository.remove(NodesRepository.ref("${Paths.notifications(uid)}/$key"))
        }
    }

    override fun onCleared() { sub?.cancel() }
}
