package com.altomedia.beruang.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altomedia.beruang.data.RealtimeRepository
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.long
import com.altomedia.beruang.data.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class ChatMessage(
    val key: String,
    val uid: String,
    val user: String,
    val text: String,
    val timestamp: Long,
)

/**
 * Single private chat window. Subscribes to `private_chats/{chatId}` and
 * exposes the last 50 messages in order. Mirrors the web `openChat` listener.
 */
class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var sub: com.altomedia.beruang.data.NodeSubscription? = null

    fun open(chatId: String) {
        sub?.cancel()
        sub = RealtimeRepository.watch("private_chats/$chatId", viewModelScope).also { s ->
            viewModelScope.launch {
                s.flow.collect { rebuild(it?.asObject()) }
            }
        }
    }

    fun close() {
        sub?.cancel(); sub = null; _messages.value = emptyList()
    }

    private fun rebuild(raw: JsonObject?) {
        val list = raw?.entries?.map { (key, m) ->
            val o = m.asObject()
            ChatMessage(
                key = key,
                uid = o.str("uid") ?: "",
                user = o.str("user") ?: "Pengguna",
                text = o.str("text") ?: "",
                timestamp = o.long("timestamp") ?: 0L,
            )
        }?.sortedBy { it.timestamp } ?: emptyList()
        _messages.value = list
    }

    override fun onCleared() { sub?.cancel() }
}
