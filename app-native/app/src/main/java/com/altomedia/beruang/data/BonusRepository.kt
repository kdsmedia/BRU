package com.altomedia.beruang.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Tugas Bonus daily progress tracking — checkin, ad-watch count, comments,
 * posts and add-friend counts for the Bonus screen. Per-user, per-day state
 * stored under `users/{uid}/bonus`.
 */
object BonusRepository {

    private val repo get() = NodesRepository
    private fun todayStr(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())

    data class BonusState(
        val date: String,
        val checkin: Boolean,
        val ads: Int,
        val comments: Int,
        val posts: Int,
        val friends: Int,
    )

    private fun emptyState(): BonusState = BonusState(todayStr(), false, 0, 0, 0, 0)

    /** Load today's bonus progress (resets to empty if the day changed). */
    suspend fun load(uid: String): BonusState {
        val o = repo.readValue("users/$uid/bonus")?.asObject()
        if (o == null) return emptyState()
        val d = o.str("date") ?: return emptyState()
        if (d != todayStr()) return emptyState()
        return BonusState(
            date = d,
            checkin = o.boolOr("checkin", false),
            ads = o.intOr("ads", 0),
            comments = o.intOr("comments", 0),
            posts = o.intOr("posts", 0),
            friends = o.intOr("friends", 0),
        )
    }

    private suspend fun save(uid: String, s: BonusState) {
        repo.set(repo.ref("users/$uid/bonus"), buildJsonObject {
            put("date", s.date)
            put("checkin", s.checkin)
            put("ads", s.ads)
            put("comments", s.comments)
            put("posts", s.posts)
            put("friends", s.friends)
        })
    }

    /** Daily checkin — awards [AppConstants.Bonus.POINTS_CHECKIN] once per day. */
    suspend fun doCheckin(uid: String): Boolean {
        val s = load(uid)
        if (s.checkin) return false
        val ns = s.copy(checkin = true)
        save(uid, ns)
        WalletRepository.awardPoints(uid, AppConstants.Bonus.POINTS_CHECKIN, "checkin")
        return true
    }

    /** Record a completed (valid, no-skip) ad watch. Awards 20 pts, max 20/day. */
    suspend fun recordAdWatch(uid: String): Boolean {
        val s = load(uid)
        if (s.ads >= AppConstants.Bonus.AD_DAILY_LIMIT) return false
        val ns = s.copy(ads = s.ads + 1)
        save(uid, ns)
        WalletRepository.awardPoints(uid, AppConstants.Bonus.POINTS_AD_VALID, "ad_watch")
        return true
    }

    /** Increment the daily comment task counter (called from comment flow). */
    suspend fun recordComment(uid: String) {
        val s = load(uid)
        if (s.comments >= AppConstants.Bonus.COMMENT_DAILY_TARGET) return
        save(uid, s.copy(comments = s.comments + 1))
    }

    /** Increment the daily post task counter (called from post flow). */
    suspend fun recordPost(uid: String) {
        val s = load(uid)
        if (s.posts >= AppConstants.Bonus.POST_DAILY_TARGET) return
        save(uid, s.copy(posts = s.posts + 1))
    }

    /** Increment the daily add-friend task counter (called from follow flow). */
    suspend fun recordFriend(uid: String) {
        val s = load(uid)
        if (s.friends >= AppConstants.Bonus.FRIEND_DAILY_TARGET) return
        save(uid, s.copy(friends = s.friends + 1))
    }
}
