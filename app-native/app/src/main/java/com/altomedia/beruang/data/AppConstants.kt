package com.altomedia.beruang.data

/**
 * App-wide constants — exact copies of the web app config so behaviour is
 * unchanged after the native migration.
 */
object AppConstants {
    const val DEFAULT_AVATAR = "https://cdn-icons-png.flaticon.com/512/847/847969.png"

    // Points awarded per activity (web: const POINTS).
    const val POINTS_COMMENT = 50L
    const val POINTS_POST = 20L
    const val POINTS_FOLLOW = 10L
    const val POINTS_LIKE = 2L

    // Referral bonus (both referrer and new user).
    const val REFERRAL_BONUS_REFERRER = 500L
    const val REFERRAL_BONUS_NEW_USER = 500L

    // Admin config (web: const ADMIN).
    object Admin {
        val PHONES = listOf("085813899649")
        const val ACCT_ID = "140693"
        const val NAME = "BERUANG"
    }

    // Account tiers (web: const TIERS). Master = unlimited limits.
    data class Tier(
        val name: String,
        val price: Long,
        val postLimit: Int,       // Int.MAX_VALUE = unlimited
        val commentLimit: Int,
        val icon: String,         // material icon name hint
        val colorHex: Long,
    )

    val TIERS = listOf(
        Tier("Star", 0L, 5, 5, "star", 0xFF64748B),
        Tier("Bronze", 5000L, 10, 10, "workspace_premium", 0xFF9A3412),
        Tier("Silver", 9000L, 20, 20, "military_tech", 0xFF475569),
        Tier("Gold", 250000L, 50, 50, "emoji_events", 0xFFB45309),
        Tier("Master", 1000000L, Int.MAX_VALUE, Int.MAX_VALUE, "workspace_premium", 0xFF6D28D9),
    )

    fun tier(name: String?): Tier = TIERS.firstOrNull { it.name == name } ?: TIERS[0]
    fun tierIndex(name: String?): Int = TIERS.indexOfFirst { it.name == name }.let { if (it < 0) 0 else it }
}

/** Data path helpers — single source of truth for the `nodes` tree layout. */
object Paths {
    fun user(uid: String) = "users/$uid"
    fun users() = "users"
    fun post(pid: String) = "posts/$pid"
    fun posts() = "posts"
    fun wallet(uid: String) = "wallets/$uid"
    fun walletBalance(uid: String) = "wallets/$uid/balance"
    fun walletUsage(uid: String) = "wallets/$uid/usage"
    fun walletPin(uid: String) = "wallets/$uid/pin"
    fun walletHistory(uid: String) = "wallets/$uid/history"
    fun walletTier(uid: String) = "wallets/$uid/tier"
    fun followers(uid: String) = "followers/$uid"
    fun following(uid: String) = "following/$uid"
    fun notifications(uid: String) = "notifications/$uid"
    fun stories() = "stories"
    fun blocked(uid: String) = "blocked/$uid"
    fun accountIndex() = "account_index"
    fun accountIndex(uid: String) = "account_index/$uid"
    fun privateChat(chatId: String) = "private_chats/$chatId"

    /** Chat id is the sorted concatenation of the two uids, prefixed with
     *  `private_` — matches the web app exactly. */
    fun chatId(a: String, b: String): String {
        val (x, y) = if (a <= b) a to b else b to a
        return "private_${x}_$y"
    }
}
