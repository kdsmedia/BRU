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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.altomedia.beruang.ui.auth.AuthUser
import com.altomedia.beruang.ui.components.showToast
import com.altomedia.beruang.ui.theme.BgBody
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * Home view — port of the web `#view-home`: stories row + post feed, live.
 * [onAddStory] launches the image picker for story upload.
 */
@Composable
fun HomeScreen(
    me: AuthUser,
    isAdmin: Boolean,
    onAddStory: () -> Unit,
    onVisitProfile: (String) -> Unit,
    vm: FeedViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val posts by vm.posts.collectAsState()
    val stories by vm.stories.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val followingMap = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(me.uid) { vm.start(me) }
    // Resolve follow state for all visible authors.
    LaunchedEffect(posts) {
        posts.map { it.authorUid }.distinct().filter { it != me.uid && it !in followingMap }.forEach { uid ->
            scope.launch { followingMap[uid] = PostRepository.isFollowing(me.uid, uid) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgBody),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item { StoriesRow(stories = stories, onAdd = onAddStory) }
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
            items(posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    myUid = me.uid,
                    isFollowing = followingMap[post.authorUid] == true,
                    isAdmin = isAdmin,
                    onLike = {
                        scope.launch {
                            PostRepository.toggleLike(me, post.id, post.authorUid)
                            vm.refresh()
                        }
                    },
                    onComment = { text ->
                        scope.launch {
                            PostRepository.postComment(me, post.id, post.authorUid, text)
                            showToast(context, "+${com.altomedia.beruang.data.AppConstants.POINTS_COMMENT} poin (berkomentar)")
                            vm.refresh()
                        }
                    },
                    onToggleFollow = {
                        scope.launch {
                            val now = PostRepository.toggleFollow(me, post.authorUid, post.authorName)
                            followingMap[post.authorUid] = now
                            if (now) showToast(context, "+${com.altomedia.beruang.data.AppConstants.POINTS_FOLLOW} poin (menambah teman)")
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
            }
        }
    }
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
