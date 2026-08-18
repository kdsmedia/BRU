package com.altomedia.beruang.data

import kotlinx.serialization.json.JsonPrimitive

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
}
