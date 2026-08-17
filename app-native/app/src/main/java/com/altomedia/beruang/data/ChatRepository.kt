package com.altomedia.beruang.data

import com.altomedia.beruang.ui.auth.AuthUser
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Chat helpers — port of the web chat logic (`openChat` / `sendMsg` /
 * `deleteChatMsg` / `clearChat`). Chat id is `private_{uid0}_{uid1}` with
 * uids sorted — matches [Paths.privateChat].
 */
object ChatRepository {

    /** Build the bidirectional private chat id. */
    fun chatId(a: String, b: String): String =
        Paths.privateChat(a, b)

    suspend fun send(me: AuthUser, chatId: String, text: String) {
        if (text.isBlank()) return
        NodesRepository.push(
            NodesRepository.ref("private_chats/$chatId"),
            buildJsonObject {
                put("uid", me.uid)
                put("user", me.displayName ?: "Pengguna")
                put("text", text)
                put("timestamp", System.currentTimeMillis())
            },
        )
    }

    suspend fun deleteMessage(chatId: String, msgKey: String) {
        NodesRepository.remove(NodesRepository.ref("private_chats/$chatId/$msgKey"))
    }

    suspend fun clearChat(chatId: String) {
        NodesRepository.remove(NodesRepository.ref("private_chats/$chatId"))
    }
}
