package com.altomedia.beruang.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altomedia.beruang.data.AppConstants
import com.altomedia.beruang.data.Paths
import com.altomedia.beruang.data.RealtimeRepository
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class ChatListUser(
    val uid: String,
    val username: String,
    val photo: String,
    val isAdmin: Boolean = false,
    val isAi: Boolean = false,
)

/**
 * The chat list — live-subscribes the `users` node so the roster updates when
 * someone joins. Mirrors the web `renderChatList` behaviour (all users minus
 * the signed-in user) but keeps the list fresh via realtime.
 */
class ChatListViewModel : ViewModel() {

    private val _users = MutableStateFlow<List<ChatListUser>>(emptyList())
    val users: StateFlow<List<ChatListUser>> = _users.asStateFlow()

    /** Pull-to-refresh spinner state. */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var sub: com.altomedia.beruang.data.NodeSubscription? = null

    init {
        viewModelScope.launch {
            sub = RealtimeRepository.watch(Paths.users(), viewModelScope)
            // Snapshot immediately + on every change.
            launch {
                sub!!.stateFlow.collect { el ->
                    val raw = el?.asObject() ?: JsonObject(emptyMap())
                    _users.value = raw.entries.mapNotNull { (uid, u) ->
                        val o = u.asObject() ?: return@mapNotNull null
                        ChatListUser(
                            uid = uid,
                            username = o.str("username") ?: "Pengguna",
                            photo = o.str("photo") ?: AppConstants.DEFAULT_AVATAR,
                            isAdmin = o.str("role") == "admin",
                            isAi = o["is_ai"]?.asBooleanSafe() == true,
                        )
                    }.filter { it.uid.isNotEmpty() }
                        .sortedWith(
                            compareByDescending<ChatListUser> { it.isAdmin }
                                .thenByDescending { it.isAi }
                                .thenBy { it.username.lowercase() },
                        )
                }
            }
        }
    }

    /** Force a one-shot re-read of the roster (pull-to-refresh). */
    fun refresh() {
        val s = sub ?: return
        _refreshing.value = true
        s.refreshNow()
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _refreshing.value = false
        }
    }

    override fun onCleared() {}
}

private fun kotlinx.serialization.json.JsonElement.asBooleanSafe(): Boolean =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() == true
