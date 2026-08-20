package com.altomedia.beruang.data

import com.altomedia.beruang.ui.auth.AuthUser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Posts / likes / comments / follow / stories — direct port of the web app's
 * feed helpers (toggleLike, postComment, toggleFollow, sendNotif, deletePost,
 * adminTogglePin, uploadStory, listenFeed).
 */
object PostRepository {

    private val repo get() = NodesRepository

    // ---- notifications ------------------------------------------------
    suspend fun sendNotif(targetUid: String, actorName: String, text: String) {
        repo.push(repo.ref(Paths.notifications(targetUid)), buildJsonObject {
            put("text", "$actorName $text")
            put("timestamp", System.currentTimeMillis())
        })
    }

    // ---- follow -------------------------------------------------------
    suspend fun isFollowing(myUid: String, targetUid: String): Boolean =
        repo.readValue("${Paths.following(myUid)}/$targetUid")?.asBoolean() == true

    suspend fun toggleFollow(me: AuthUser, targetUid: String, targetName: String): Boolean {
        if (targetUid.isEmpty() || targetUid == me.uid) return false
        val following = isFollowing(me.uid, targetUid)
        if (following) {
            repo.remove(repo.ref("${Paths.following(me.uid)}/$targetUid"))
            repo.remove(repo.ref("${Paths.followers(targetUid)}/$me.uid"))
        } else {
            repo.set(repo.ref("${Paths.following(me.uid)}/$targetUid"), JsonPrimitive(true))
            repo.set(repo.ref("${Paths.followers(targetUid)}/$me.uid"), JsonPrimitive(true))
            // Side effects (notif, poin, bonus) must never crash the follow
            // action itself — a failure here previously killed the coroutine
            // on the Main dispatcher and force-closed the app.
            runCatching { sendNotif(targetUid, me.displayName ?: "Seseorang", "mulai mengikuti Anda") }
                .onFailure { android.util.Log.e("PostRepository", "follow notif failed", it) }
            runCatching { WalletRepository.awardPoints(me.uid, AppConstants.POINTS_FOLLOW, "follow $targetUid") }
                .onFailure { android.util.Log.e("PostRepository", "follow points failed", it) }
            runCatching { BonusRepository.recordFriend(me.uid) }
                .onFailure { android.util.Log.e("PostRepository", "follow bonus failed", it) }
        }
        return !following // new state
    }

    // ---- likes --------------------------------------------------------
    suspend fun toggleLike(me: AuthUser, pid: String, authorUid: String): Boolean {
        val path = "posts/$pid/likes/${me.uid}"
        val liked = repo.readValue(path)?.asBoolean() == true
        if (liked) {
            repo.remove(repo.ref(path))
            return false
        } else {
            repo.set(repo.ref(path), JsonPrimitive(true))
            if (authorUid != me.uid) {
                sendNotif(authorUid, me.displayName ?: "Seseorang", "menyukai postingan Anda")
                val rewarded = repo.readValue("posts/$pid/likeRewards/${me.uid}")?.asBoolean() == true
                if (!rewarded) {
                    repo.set(repo.ref("posts/$pid/likeRewards/${me.uid}"), JsonPrimitive(true))
                    WalletRepository.awardPoints(me.uid, AppConstants.POINTS_LIKE, "like $pid")
                }
            }
            return true
        }
    }

    // ---- comments -----------------------------------------------------
    suspend fun postComment(
        me: AuthUser,
        pid: String,
        ownerUid: String,
        text: String,
        replyToUid: String? = null,
        replyToName: String? = null,
    ) {
        repo.push(repo.ref("posts/$pid/comments"), buildJsonObject {
            put("text", text)
            put("uid", me.uid)
            put("username", me.displayName ?: "Pengguna")
            put("timestamp", System.currentTimeMillis())
            if (!replyToName.isNullOrBlank()) put("replyTo", replyToName)
        })
        // Notify the post owner (unless the commenter is the owner).
        if (ownerUid != me.uid) {
            sendNotif(ownerUid, me.displayName ?: "Seseorang", "berkomentar: $text")
        }
        // Notify the user being replied to (mention), if any and not self.
        if (replyToUid != null && replyToUid != me.uid && replyToUid != ownerUid) {
            sendNotif(replyToUid, me.displayName ?: "Seseorang", "membalas komentar Anda: $text")
        }
        WalletRepository.awardPoints(me.uid, AppConstants.POINTS_COMMENT, "comment $pid")
        BonusRepository.recordComment(me.uid)
    }

    // ---- create post --------------------------------------------------
    suspend fun createPost(me: AuthUser, caption: String, imageUrl: String?) {
        val payload = buildJsonObject {
            put("uid", me.uid)
            put("caption", caption)
            put("timestamp", System.currentTimeMillis())
            if (!imageUrl.isNullOrBlank()) put("image", imageUrl)
        }
        repo.push(repo.ref(Paths.posts()), payload)
    }

    // ---- admin: pin / delete -----------------------------------------
    suspend fun adminTogglePin(pid: String, currentlyPinned: Boolean) {
        repo.set(repo.ref("posts/$pid/pinned"), JsonPrimitive(!currentlyPinned))
    }

    suspend fun deletePost(pid: String) {
        repo.remove(repo.ref(Paths.post(pid)))
    }

    // ---- stories ------------------------------------------------------
    suspend fun createStory(me: AuthUser, imageUrl: String) {
        repo.push(repo.ref(Paths.stories()), buildJsonObject {
            put("uid", me.uid)
            put("image", imageUrl)
            put("timestamp", System.currentTimeMillis())
        })
    }

    // ---- user lookup --------------------------------------------------
    suspend fun loadUser(uid: String): JsonObject? =
        repo.readValue(Paths.user(uid))?.asObject()

    suspend fun loadUsers(uids: Collection<String>): Map<String, JsonObject> {
        val out = LinkedHashMap<String, JsonObject>()
        uids.forEach { uid -> loadUser(uid)?.let { out[uid] = it } }
        return out
    }
}
