package com.altomedia.beruang.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altomedia.beruang.data.NodesRepository
import com.altomedia.beruang.data.Paths
import com.altomedia.beruang.data.PostRepository
import com.altomedia.beruang.data.RealtimeRepository
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.long
import com.altomedia.beruang.data.str
import com.altomedia.beruang.ui.auth.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the home feed state: posts, stories, users cache, current wallet
 * snapshot. Subscribes to realtime changes on `posts` and `stories` so the feed
 * updates live — mirrors the web `listenFeed` + `listenStories` + `listenMyWallet`.
 */
class FeedViewModel : ViewModel() {

    private var me: AuthUser? = null
    private var usersCache = mutableMapOf<String, UserBrief>()

    private val _posts = MutableStateFlow<List<PostItem>>(emptyList())
    val posts: StateFlow<List<PostItem>> = _posts.asStateFlow()

    private val _stories = MutableStateFlow<List<StoryItem>>(emptyList())
    val stories: StateFlow<List<StoryItem>> = _stories.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var postsSub: com.altomedia.beruang.data.NodeSubscription? = null
    private var storiesSub: com.altomedia.beruang.data.NodeSubscription? = null

    /** Begin (or re-begin) listening for the signed-in user. */
    fun start(me: AuthUser) {
        if (this.me?.uid == me.uid && postsSub != null) return
        this.me = me
        postsSub?.cancel()
        storiesSub?.cancel()
        postsSub = RealtimeRepository.watch(Paths.posts(), viewModelScope).also { sub ->
            viewModelScope.launch {
                sub.stateFlow.collect { rebuildPosts(it) }
            }
        }
        storiesSub = RealtimeRepository.watch(Paths.stories(), viewModelScope).also { sub ->
            viewModelScope.launch {
                sub.stateFlow.collect { rebuildStories(it) }
            }
        }
    }

    override fun onCleared() {
        postsSub?.cancel()
        storiesSub?.cancel()
    }

    /** Pull-to-refresh: force a one-shot re-read (mirrors `_refreshFeed`). */
    fun refresh() {
        _refreshing.value = true
        postsSub?.refreshNow()
        storiesSub?.refreshNow()
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            _refreshing.value = false
        }
    }

    private suspend fun rebuildPosts(raw: kotlinx.serialization.json.JsonElement?) {
        val myUid = me?.uid ?: return
        val obj = raw?.asObject() ?: run { _posts.value = emptyList(); return }
        // Collect unique author uids and lazy-load missing users.
        val authorUids = obj.values.mapNotNull { it.asObject().str("uid") }.distinct()
        val missing = authorUids.filter { it !in usersCache }
        if (missing.isNotEmpty()) {
            PostRepository.loadUsers(missing).forEach { (uid, u) ->
                usersCache[uid] = parseUser(uid, u)
            }
        }
        val sorted = obj.entries.map { (pid, p) ->
            val o = p.asObject()
            val authorUid = o.str("uid") ?: ""
            parsePost(pid, o, usersCache[authorUid], myUid)
        }.sortedWith(
            compareByDescending<PostItem> { it.pinned }
                .thenByDescending { it.timestamp },
        )
        _posts.value = sorted
    }

    private fun rebuildStories(raw: kotlinx.serialization.json.JsonElement?) {
        val obj = raw?.asObject() ?: run { _stories.value = emptyList(); return }
        _stories.value = obj.entries
            .map { (id, s) ->
                val o = s.asObject()
                StoryItem(
                    id = id,
                    authorUid = o.str("uid") ?: "",
                    imageUrl = o.str("image") ?: "",
                    timestamp = o.long("timestamp") ?: 0L,
                )
            }
            .sortedByDescending { it.timestamp }
            .take(20)
    }
}
