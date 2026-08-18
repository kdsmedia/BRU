package com.altomedia.beruang.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altomedia.beruang.data.AppConstants
import com.altomedia.beruang.data.Paths
import com.altomedia.beruang.data.RealtimeRepository
import com.altomedia.beruang.data.asBoolean
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Snapshot of the profile being viewed (self or visited). */
data class ProfileState(
    val uid: String = "",
    val username: String = "Pengguna",
    val photo: String = AppConstants.DEFAULT_AVATAR,
    val isAdmin: Boolean = false,
    val isAi: Boolean = false,
    val tier: String = "Star",
    val postCount: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val isFollowing: Boolean = false,
    val isSelf: Boolean = false,
    val gridImages: List<String> = emptyList(),
)

/**
 * Loads + live-subscribes a profile (own or visited). Mirrors the web
 * `loadProfile(uid)` + the profile stats listeners.
 */
class ProfileViewModel : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private var myUid: String = ""
    private var userSub: com.altomedia.beruang.data.NodeSubscription? = null
    private var postsSub: com.altomedia.beruang.data.NodeSubscription? = null
    private var followersSub: com.altomedia.beruang.data.NodeSubscription? = null
    private var followingSub: com.altomedia.beruang.data.NodeSubscription? = null
    private var walletSub: com.altomedia.beruang.data.NodeSubscription? = null
    private var followingMeSub: com.altomedia.beruang.data.NodeSubscription? = null

    fun load(targetUid: String, myUid: String) {
        if (this.myUid != myUid || _state.value.uid != targetUid) {
            cancelAll()
            this.myUid = myUid
            _state.value = ProfileState(uid = targetUid, isSelf = targetUid == myUid)
            subscribe(targetUid, myUid)
        }
    }

    private fun subscribe(targetUid: String, myUid: String) {
        userSub = RealtimeRepository.watch(Paths.user(targetUid), viewModelScope).also { sub ->
            viewModelScope.launch {
                sub.stateFlow.collect { rebuildUser(targetUid, myUid, it?.asObject()) }
            }
        }
        postsSub = RealtimeRepository.watch(Paths.posts(), viewModelScope).also { sub ->
            viewModelScope.launch {
                sub.stateFlow.collect { rebuildPosts(targetUid, it?.asObject()) }
            }
        }
        followersSub = RealtimeRepository.watch(Paths.followers(targetUid), viewModelScope).also { sub ->
            viewModelScope.launch {
                sub.stateFlow.collect { v ->
                    val c = v?.asObject()?.size ?: 0
                    _state.value = _state.value.copy(followersCount = c)
                }
            }
        }
        followingSub = RealtimeRepository.watch(Paths.following(targetUid), viewModelScope).also { sub ->
            viewModelScope.launch {
                sub.stateFlow.collect { v ->
                    val c = v?.asObject()?.size ?: 0
                    _state.value = _state.value.copy(followingCount = c)
                }
            }
        }
        walletSub = RealtimeRepository.watch(Paths.wallet(targetUid), viewModelScope).also { sub ->
            viewModelScope.launch {
                sub.stateFlow.collect { v ->
                    val w = v?.asObject()
                    _state.value = _state.value.copy(tier = w?.str("tier") ?: "Star")
                }
            }
        }
        followingMeSub = RealtimeRepository.watch(Paths.following(myUid), viewModelScope).also { sub ->
            viewModelScope.launch {
                sub.stateFlow.collect { v ->
                    val following = v?.asObject()?.get(targetUid)?.asBoolean() == true
                    _state.value = _state.value.copy(isFollowing = following)
                }
            }
        }
    }

    private fun rebuildUser(uid: String, myUid: String, raw: kotlinx.serialization.json.JsonObject?) {
        _state.value = _state.value.copy(
            username = raw?.str("username") ?: "Pengguna",
            photo = raw?.str("photo") ?: AppConstants.DEFAULT_AVATAR,
            isAdmin = raw?.str("role") == "admin",
            isAi = raw?.get("is_ai")?.asBoolean() == true,
            isSelf = uid == myUid,
        )
    }

    private fun rebuildPosts(uid: String, raw: kotlinx.serialization.json.JsonObject?) {
        val mine = raw?.entries?.mapNotNull { (_, p) ->
            val o = p.asObject()
            if (o.str("uid") == uid) o.str("image") else null
        } ?: emptyList()
        _state.value = _state.value.copy(postCount = raw?.entries?.count {
            it.value.asObject().str("uid") == uid
        } ?: 0, gridImages = mine)
    }

    private fun cancelAll() {
        userSub?.cancel(); postsSub?.cancel(); followersSub?.cancel()
        followingSub?.cancel(); walletSub?.cancel(); followingMeSub?.cancel()
    }

    override fun onCleared() = cancelAll()
}
