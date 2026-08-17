package com.altomedia.beruang.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altomedia.beruang.data.AppConstants
import com.altomedia.beruang.data.NodesRepository
import com.altomedia.beruang.data.Paths
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
 * The chat list tab — port of the web `renderChatList`: all users in the global
 * usersCache minus the signed-in user. Live-subscribes the `users` node.
 */
class ChatListViewModel : ViewModel() {

    private val _users = MutableStateFlow<List<ChatListUser>>(emptyList())
    val users: StateFlow<List<ChatListUser>> = _users.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = NodesRepository.readValue(Paths.users())?.asObject() ?: JsonObject(emptyMap())
            _users.value = raw.entries.mapNotNull { (uid, u) ->
                val o = u.asObject()
                ChatListUser(
                    uid = uid,
                    username = o.str("username") ?: "Pengguna",
                    photo = o.str("photo") ?: AppConstants.DEFAULT_AVATAR,
                    isAdmin = o.str("role") == "admin",
                    isAi = o["is_ai"]?.asBooleanSafe() == true,
                )
            }.filter { it.uid.isNotEmpty() }
        }
    }

    override fun onCleared() {}
}

// Local helper to avoid leaking asBoolean import noise.
private fun kotlinx.serialization.json.JsonElement.asBooleanSafe(): Boolean =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() == true
