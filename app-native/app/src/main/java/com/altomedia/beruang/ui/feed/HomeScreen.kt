package com.altomedia.beruang.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.altomedia.beruang.data.PostRepository
import com.altomedia.beruang.data.asObject
import com.altomedia.beruang.data.str
import com.altomedia.beruang.ui.auth.AuthUser
import com.altomedia.beruang.ui.components.showToast
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * Home view — port of the web `#view-home`: stories row + post feed, live.
 * [onAddStory] launches the image picker for story upload.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    me: AuthUser,
    isAdmin: Boolean,
    onAddStory: () -> Unit,
    onVisitProfile: (String) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenNotif: () -> Unit,
    modifier: Modifier = Modifier,
    vm: FeedViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val posts by vm.posts.collectAsState()
    val stories by vm.stories.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val followingMap = remember { mutableStateMapOf<String, Boolean>() }
    val followsMeMap = remember { mutableStateMapOf<String, Boolean>() }
    val activity = rememberActivity()
    val quota = com.altomedia.beruang.ads.rememberRewardedQuotaPrompt(activity)

    LaunchedEffect(me.uid) { vm.start(me) }
    // Resolve follow state for all visible authors (both directions, so a
    // mutual follow can be shown as "Teman").
    LaunchedEffect(posts) {
        posts.map { it.authorUid }.distinct().filter { it != me.uid }.forEach { uid ->
            if (uid !in followingMap) {
                scope.launch { followingMap[uid] = PostRepository.isFollowing(me.uid, uid) }
            }
            if (uid !in followsMeMap) {
                scope.launch { followsMeMap[uid] = PostRepository.isFollowing(uid, me.uid) }
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.refresh() },
        modifier = modifier.fillMaxSize().background(BgBody),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { HomeHeader(onOpenMessages = onOpenMessages, onOpenNotif = onOpenNotif) }
            item { StoriesRow(stories = stories, onAdd = onAddStory) }
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    com.altomedia.beruang.ads.BannerAdBlock()
                }
            }
            if (posts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(50.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(color = BrandYellow, modifier = Modifier.size(28.dp))
                        } else {
                            Text("Belum ada postingan.", color = TextMuted, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                    PostCard(
                        post = post,
                        myUid = me.uid,
                        isFollowing = followingMap[post.authorUid] == true,
                        isFriend = followingMap[post.authorUid] == true && followsMeMap[post.authorUid] == true,
                        isAdmin = isAdmin,
                        onLike = {
                            scope.launch {
                                PostRepository.toggleLike(me, post.id, post.authorUid)
                                vm.refresh()
                            }
                        },
                        onComment = { text, replyToUid, replyToName ->
                            scope.launch {
                                // Enforce tier daily comment limit (web: checkLimit('comments')).
                                val usage = com.altomedia.beruang.data.WalletRepository.loadUsage(me.uid)
                                val tierName = com.altomedia.beruang.data.NodesRepository
                                    .readValue(com.altomedia.beruang.data.Paths.wallet(me.uid))?.asObject()
                                    ?.str("tier") ?: "Star"
                                val c = com.altomedia.beruang.data.WalletRepository.checkLimit(tierName, usage, "comments")
                                if (!c.ok) {
                                    com.altomedia.beruang.ui.components.showToast(context, "Batas komentar harian tercapai (${c.limit}x untuk $tierName).")
                                    quota.request("comments") { granted ->
                                        if (granted) scope.launch {
                                            PostRepository.postComment(me, post.id, post.authorUid, text, replyToUid, replyToName)
                                            showToast(context, "+${com.altomedia.beruang.data.AppConstants.POINTS_COMMENT} poin (berkomentar)")
                                            vm.refresh()
                                        }
                                    }
                                    return@launch
                                }
                                PostRepository.postComment(me, post.id, post.authorUid, text, replyToUid, replyToName)
                                com.altomedia.beruang.data.WalletRepository.recordUsage(me.uid, usage, "comments")
                                showToast(context, "+${com.altomedia.beruang.data.AppConstants.POINTS_COMMENT} poin (berkomentar)")
                                vm.refresh()
                            }
                        },
                        onToggleFollow = {
                            scope.launch {
                                val now = runCatching {
                                    PostRepository.toggleFollow(me, post.authorUid, post.authorName)
                                }.onFailure {
                                    android.util.Log.e("HomeScreen", "toggleFollow failed", it)
                                    showToast(context, "Gagal memproses. Periksa koneksi lalu coba lagi.")
                                }.getOrNull() ?: return@launch
                                followingMap[post.authorUid] = now
                                if (now) {
                                    showToast(context, "+${com.altomedia.beruang.data.AppConstants.POINTS_FOLLOW} poin (menambah teman)")
                                }
                            }
                        },
                        onVisitProfile = { onVisitProfile(post.authorUid) },
                        onDelete = {
                            scope.launch {
                                PostRepository.deletePost(post.id)
                                showToast(context, "Postingan dihapus")
                                vm.refresh()
                            }
                        },
                        onTogglePin = {
                            scope.launch {
                                PostRepository.adminTogglePin(post.id, post.pinned)
                                vm.refresh()
                            }
                        },
                    )
                    // Inline banner ad every 5 posts (web: ad block between posts).
                    if ((index + 1) % 5 == 0 && index + 1 < posts.size) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            com.altomedia.beruang.ads.BannerAdBlock()
                        }
                    }
                }
            }
        }
    }
    quota.Render(me.uid)
}

/**
 * Home top bar: app wordmark on the left, quick-access message + notification
 * icons on the right. Tapping the icons opens the Pesan / Aktivitas screens
 * as overlays (since those tabs are now Bonus & Game).
 */
@Composable
private fun HomeHeader(onOpenMessages: () -> Unit, onOpenNotif: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        Text(
            "BERUANG",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = BrandYellow,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenMessages) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Pesan", tint = TextMain)
            }
            IconButton(onClick = onOpenNotif) {
                Icon(Icons.Filled.Notifications, contentDescription = "Aktivitas", tint = TextMain)
            }
        }
    }
}

/** Resolve the hosting Activity from the current Compose context (for AdMob). */
@Composable
fun rememberActivity(): android.app.Activity {
    var ctx: android.content.Context = LocalContext.current
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    error("No Activity in context chain")
}

/** Horizontal stories row — port of `.stories-wrapper` (add button + thumbnails). */
@Composable
fun StoriesRow(stories: List<StoryItem>, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Add-story tile (dashed circle).
        Box(
            modifier = Modifier
                .size(65.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { onAdd() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(65.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFFBEB)),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Tambah story",
                    tint = BrandYellow,
                    modifier = Modifier.size(24.dp).align(Alignment.Center),
                )
            }
        }
        stories.forEach { s ->
            AsyncImage(
                model = s.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(65.dp)
                    .clip(CircleShape)
                    .clickable { /* view story — full-screen viewer added later */ },
            )
        }
    }
}
