package com.altomedia.beruang.ui.feed

import com.altomedia.beruang.data.asBoolean
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.bool
import com.altomedia.beruang.data.long
import com.altomedia.beruang.data.str
import kotlinx.serialization.json.JsonObject

/** A feed post + its author info, ready for rendering. */
data class PostItem(
    val id: String,
    val authorUid: String,
    val authorName: String,
    val authorPhoto: String,
    val isAdmin: Boolean = false,
    val isAi: Boolean = false,
    val pinned: Boolean = false,
    val caption: String,
    val imageUrl: String?,
    val timestamp: Long,
    val likeCount: Int,
    val likedByMe: Boolean,
    val comments: List<CommentItem>,
)

data class CommentItem(
    val uid: String,
    val username: String,
    val text: String,
    val timestamp: Long,
)

/** Public profile of a user shown on feed avatars / cards. */
data class UserBrief(
    val uid: String,
    val username: String,
    val photo: String,
    val isAdmin: Boolean = false,
    val isAi: Boolean = false,
    val role: String? = null,
    val tier: String? = null,
)

/** Story thumbnail in the stories row. */
data class StoryItem(val id: String, val authorUid: String, val imageUrl: String, val timestamp: Long)

/** Normalize a raw `posts/{pid}` node into a [PostItem]. */
fun parsePost(
    pid: String,
    raw: JsonObject,
    author: UserBrief?,
    myUid: String,
): PostItem {
    val likes = raw["likes"] as? JsonObject
    val comments = raw["comments"] as? JsonObject
    val commentList = comments?.entries?.map { (_, c) ->
        val o = c.asObject()
        CommentItem(
            uid = o.str("uid") ?: "",
            username = o.str("username") ?: "Pengguna",
            text = o.str("text") ?: "",
            timestamp = o.long("timestamp") ?: 0L,
        )
    }?.sortedBy { it.timestamp } ?: emptyList()
    return PostItem(
        id = pid,
        authorUid = raw.str("uid") ?: "",
        authorName = author?.username ?: "Memuat...",
        authorPhoto = author?.photo ?: com.altomedia.beruang.data.AppConstants.DEFAULT_AVATAR,
        isAdmin = author?.isAdmin == true,
        isAi = author?.isAi == true,
        pinned = raw.bool("pinned") == true,
        caption = raw.str("caption") ?: "",
        imageUrl = raw.str("image"),
        timestamp = raw.long("timestamp") ?: 0L,
        likeCount = likes?.size ?: 0,
        likedByMe = likes?.get(myUid)?.asBoolean() == true,
        comments = commentList,
    )
}

/** Normalize a raw `users/{uid}` node into a [UserBrief]. */
fun parseUser(uid: String, raw: JsonObject): UserBrief = UserBrief(
    uid = uid,
    username = raw.str("username") ?: "Pengguna",
    photo = raw.str("photo") ?: com.altomedia.beruang.data.AppConstants.DEFAULT_AVATAR,
    isAdmin = raw.str("role") == "admin",
    isAi = raw.bool("is_ai") == true,
    role = raw.str("role"),
    tier = raw.str("tier"),
)
