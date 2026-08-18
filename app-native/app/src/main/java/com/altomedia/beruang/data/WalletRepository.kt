package com.altomedia.beruang.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Wallet / points / tier / daily-usage logic — direct port of the web app's
 * wallet helpers (ensureWalletExists, awardPoints, checkLimit, recordUsage,
 * grantAdQuota, transfer, buyTier).
 */
object WalletRepository {

    private val repo get() = NodesRepository
    private fun todayStr(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())

    // ---- account id --------------------------------------------------
    private fun genAcctId(): String {
        val n = (100000..999999).random()
        return n.toString()
    }

    suspend fun isAcctIdTaken(id: String): Boolean {
        val idx = repo.readValue(Paths.accountIndex())?.asObject() ?: return false
        return idx.any { it.value.asString() == id }
    }

    suspend fun genUniqueAcctId(): String {
        repeat(20) {
            val id = genAcctId()
            if (!isAcctIdTaken(id)) return id
        }
        return genAcctId()
    }

    /** Create the wallet node for a freshly registered user if absent. */
    suspend fun ensureWalletExists(uid: String, displayName: String) {
        val existing = repo.readValue(Paths.wallet(uid))?.asObject()
        if (existing != null && existing.str("acctId") != null) {
            // Backfill tier for legacy wallets.
            if (existing.str("tier") == null) {
                repo.set(repo.ref(Paths.walletTier(uid)), JsonPrimitive("Star"))
            }
            return
        }
        val acctId = genUniqueAcctId()
        val wallet = buildJsonObject {
            put("balance", 0L)
            put("acctId", acctId)
            put("tier", "Star")
            put("usage", buildJsonObject {
                put("date", todayStr())
                put("posts", 0)
                put("comments", 0)
            })
        }
        repo.set(repo.ref(Paths.wallet(uid)), wallet)
        repo.set(repo.ref(Paths.accountIndex(uid)), JsonPrimitive(acctId))
    }

    // ---- daily usage limits -----------------------------------------
    data class UsageState(val date: String, val posts: Int, val comments: Int, val adGrants: JsonObject)

    data class LimitCheck(val ok: Boolean, val used: Int, val limit: Int, val effective: Int, val grants: Int)

    fun isUnlimited(limit: Int) = limit == Int.MAX_VALUE

    /** Load the persisted daily usage state for [uid] (empty if none). */
    suspend fun loadUsage(uid: String): UsageState {
        val o = repo.readValue(Paths.walletUsage(uid))?.asObject()
        return UsageState(
            date = o?.str("date") ?: "",
            posts = o?.int("posts") ?: 0,
            comments = o?.int("comments") ?: 0,
            adGrants = (o?.get("adGrants") as? JsonObject) ?: JsonObject(emptyMap()),
        )
    }

    fun checkLimit(tierName: String, usage: UsageState, action: String): LimitCheck {
        val tier = AppConstants.tier(tierName)
        val limit = if (action == "posts") tier.postLimit else tier.commentLimit
        val used = if (action == "posts") usage.posts else usage.comments
        if (isUnlimited(limit)) return LimitCheck(true, used, Int.MAX_VALUE, Int.MAX_VALUE, 0)
        val todayGrants = if (usage.date != todayStr()) 0 else
            (usage.adGrants[action]?.asInt() ?: 0)
        val effective = limit + todayGrants
        return LimitCheck(used < effective, used, limit, effective, todayGrants)
    }

    suspend fun recordUsage(uid: String, usage: UsageState, action: String): UsageState {
        val u = if (usage.date != todayStr())
            UsageState(todayStr(), 0, 0, buildJsonObject {})
        else usage
        val newU = if (action == "posts") u.copy(posts = u.posts + 1) else u.copy(comments = u.comments + 1)
        repo.update(repo.ref(Paths.walletUsage(uid)), buildJsonObject {
            put("date", newU.date)
            put("posts", newU.posts)
            put("comments", newU.comments)
        })
        return newU
    }

    suspend fun grantAdQuota(uid: String, usage: UsageState, action: String): UsageState {
        val u = if (usage.date != todayStr())
            UsageState(todayStr(), 0, 0, buildJsonObject {})
        else usage
        val grants = u.adGrants.toMutableMap()
        grants[action] = JsonPrimitive((grants[action]?.asInt() ?: 0) + 1)
        val newU = u.copy(adGrants = JsonObject(grants))
        repo.update(repo.ref(Paths.walletUsage(uid)), buildJsonObject {
            put("date", newU.date)
            put("adGrants", newU.adGrants)
        })
        return newU
    }

    // ---- points ------------------------------------------------------
    /** Atomically add points to a wallet balance + push a history entry. */
    suspend fun awardPoints(uid: String, amount: Long, reason: String) {
        if (uid.isEmpty() || amount <= 0) return
        repo.runTransaction(repo.ref(Paths.walletBalance(uid))) { cur ->
            JsonPrimitive((cur?.asLong() ?: 0L) + amount)
        }
        repo.push(repo.ref(Paths.walletHistory(uid)), buildJsonObject {
            put("type", "reward")
            put("amount", amount)
            put("reason", reason)
            put("timestamp", System.currentTimeMillis())
        })
    }

    // ---- transfer (PIN-protected, atomic debit/credit) ---------------
    data class Recipient(val uid: String, val username: String, val photo: String, val acctId: String)

    suspend fun readStoredPin(uid: String): String? =
        repo.readValue(Paths.walletPin(uid))?.asString()

    suspend fun setPin(uid: String, pin: String) {
        repo.set(repo.ref(Paths.walletPin(uid)), JsonPrimitive(pin))
    }

    suspend fun readBalance(uid: String): Long =
        repo.readValue(Paths.walletBalance(uid))?.asLong() ?: 0L

    /** Read this user's own referral/account id (null if not yet assigned). */
    suspend fun readAcctId(uid: String): String? =
        repo.readValue(Paths.accountIndex(uid))?.asString()

    /**
     * Look up a recipient by acctId — port of the web `findUserByAcctId`.
     * Scans `account_index` for the matching acctId, then reads the user.
     */
    suspend fun findUserByAcctId(acctId: String): Recipient? {
        val idx = repo.readValue(Paths.accountIndex())?.asObject() ?: return null
        val uid = idx.entries.firstOrNull { it.value.asString() == acctId }?.key ?: return null
        val u = repo.readValue(Paths.user(uid))?.asObject() ?: return null
        return Recipient(
            uid = uid,
            username = u.str("username") ?: "Pengguna",
            photo = u.str("photo") ?: AppConstants.DEFAULT_AVATAR,
            acctId = acctId,
        )
    }

    /**
     * Transfer [amount] from [senderUid] to [recipient]. Verifies the PIN and
     * uses an atomic compare-and-swap so the sender can never go negative.
     * Returns null on success, an error message otherwise.
     */
    suspend fun transfer(
        senderUid: String,
        senderName: String,
        senderAcctId: String,
        recipient: Recipient,
        amount: Long,
        enteredPin: String,
    ): String? {
        if (!enteredPin.matches(Regex("\\d{4}"))) return "PIN belum lengkap"
        val storedPin = readStoredPin(senderUid)
            ?: return "Belum punya PIN. Buat dulu."
        if (enteredPin != storedPin) return "PIN salah"
        if (amount > readBalance(senderUid)) return "Saldo tidak cukup"

        val debit = repo.runTransaction(repo.ref(Paths.walletBalance(senderUid))) { cur ->
            val v = (cur?.asLong() ?: 0L) - amount
            if (v < 0) null else JsonPrimitive(v)
        }
        if (!debit.committed) return "Saldo tidak cukup"

        repo.runTransaction(repo.ref(Paths.walletBalance(recipient.uid))) { cur ->
            JsonPrimitive((cur?.asLong() ?: 0L) + amount)
        }
        val ts = System.currentTimeMillis()
        repo.push(repo.ref(Paths.walletHistory(senderUid)), buildJsonObject {
            put("type", "transfer_out")
            put("amount", amount)
            put("to", recipient.uid)
            put("toName", recipient.username)
            put("toAcctId", recipient.acctId)
            put("timestamp", ts)
        })
        repo.push(repo.ref(Paths.walletHistory(recipient.uid)), buildJsonObject {
            put("type", "transfer_in")
            put("amount", amount)
            put("from", senderUid)
            put("fromName", senderName)
            put("fromAcctId", senderAcctId)
            put("timestamp", ts)
        })
        return null
    }

    // ---- tier upgrade ------------------------------------------------
    suspend fun buyTier(uid: String, currentTier: String, targetTier: String): String? {
        val t = AppConstants.tier(targetTier)
        if (AppConstants.tierIndex(targetTier) <= AppConstants.tierIndex(currentTier))
            return "Tier sudah dimiliki"
        val bal = readBalance(uid)
        if (bal < t.price) return "Poin tidak cukup"
        val debit = repo.runTransaction(repo.ref(Paths.walletBalance(uid))) { cur ->
            val v = (cur?.asLong() ?: 0L) - t.price
            if (v < 0) null else JsonPrimitive(v)
        }
        if (!debit.committed) return "Poin tidak cukup"
        repo.update(repo.ref(Paths.wallet(uid)), buildJsonObject { put("tier", t.name) })
        repo.push(repo.ref(Paths.walletHistory(uid)), buildJsonObject {
            put("type", "upgrade")
            put("amount", t.price)
            put("tier", t.name)
            put("timestamp", System.currentTimeMillis())
        })
        return null
    }

    /**
     * Switch the active tier to an already-owned [tierName] — port of the web
     * `switchTier`. Only allows switching to a tier at or below the current one.
     */
    suspend fun switchTier(uid: String, tierName: String) {
        repo.update(repo.ref(Paths.wallet(uid)), buildJsonObject { put("tier", tierName) })
    }
}
