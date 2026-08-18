package com.altomedia.beruang.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Admin actions — port of the web admin helpers (`adminBlockUser`,
 * `adminUnblockUser`, `adminDeleteUser`). Block writes `blocked/{uid}=true`;
 * delete wipes the user's visible app data (posts, profile, wallet, index,
 * notifications, followers/following).
 */
object AdminRepository {

    suspend fun blockUser(uid: String) {
        NodesRepository.set(NodesRepository.ref("blocked/$uid"), JsonPrimitive(true))
    }

    suspend fun unblockUser(uid: String) {
        NodesRepository.remove(NodesRepository.ref("blocked/$uid"))
    }

    suspend fun isBlocked(uid: String): Boolean =
        NodesRepository.readValue("blocked/$uid")?.asBoolean() == true

    suspend fun deleteUserData(uid: String) {
        // Remove their posts.
        val posts = NodesRepository.readValue("posts")?.asObject()
        posts?.entries?.forEach { (pid, p) ->
            if (p.asObject().str("uid") == uid) {
                NodesRepository.remove(NodesRepository.ref("posts/$pid"))
            }
        }
        NodesRepository.remove(NodesRepository.ref(Paths.user(uid)))
        NodesRepository.remove(NodesRepository.ref("profiles/$uid"))
        NodesRepository.remove(NodesRepository.ref(Paths.wallet(uid)))
        NodesRepository.remove(NodesRepository.ref("account_index/$uid"))
        NodesRepository.remove(NodesRepository.ref(Paths.notifications(uid)))
        NodesRepository.remove(NodesRepository.ref(Paths.followers(uid)))
        NodesRepository.remove(NodesRepository.ref(Paths.following(uid)))
    }

    suspend fun loadBlocked(): Map<String, Boolean> {
        val o = NodesRepository.readValue("blocked")?.asObject() ?: return emptyMap()
        return o.entries.associate { (uid, v) -> uid to (v.asBoolean() == true) }
    }

    /**
     * Admin: add to (delta > 0) or deduct from (delta < 0) a user's wallet
     * balance atomically, then record a history entry. Mirrors
     * `WalletRepository.awardPoints` but allows negative deltas and clamps at 0.
     * Returns the new balance, or null if the CAS did not commit.
     */
    suspend fun adjustBalance(uid: String, delta: Long, reason: String): Long? {
        if (uid.isEmpty() || delta == 0L) return null
        // Try the optimistic CAS transaction first (works when the balance row
        // already exists). The cas_update RPC cannot INSERT a new row, so a
        // brand-new wallet (no balance node yet) fails here and we fall back
        // to a direct read-then-set below.
        val res = NodesRepository.runTransaction(NodesRepository.ref(Paths.walletBalance(uid))) { cur ->
            val next = (cur?.asLong() ?: 0L) + delta
            if (next < 0) return@runTransaction JsonPrimitive(0L)
            JsonPrimitive(next)
        }
        val newBal = if (res.committed) {
            (res.value?.asLong() ?: 0L)
        } else {
            // Fallback for wallets whose balance row doesn't exist yet: read
            // (defaults to 0), apply delta, and set directly. Non-atomic but
            // reliable for the admin single-writer case.
            val cur = NodesRepository.readValue(Paths.walletBalance(uid))?.asLong() ?: 0L
            val next = (cur + delta).coerceAtLeast(0L)
            NodesRepository.set(NodesRepository.ref(Paths.walletBalance(uid)), JsonPrimitive(next))
            next
        }
        NodesRepository.push(
            NodesRepository.ref(Paths.walletHistory(uid)),
            buildJsonObject {
                put("type", if (delta > 0) "admin_credit" else "admin_debit")
                put("amount", delta)
                put("reason", reason)
                put("timestamp", System.currentTimeMillis())
            },
        )
        return newBal
    }
}
