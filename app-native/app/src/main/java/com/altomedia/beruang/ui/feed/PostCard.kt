package com.altomedia.beruang.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.altomedia.beruang.ui.theme.BgCard
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.ErrorRed
import com.altomedia.beruang.ui.theme.LinkBlue
import com.altomedia.beruang.ui.theme.TextMuted

/**
 * A feed post card — direct port of the web `.post-card`:
 *   header (avatar, name + badges, pin/delete/follow) → image (optional) →
 *   actions (like/comment) → like count → caption/body → comment section.
 */
@Composable
fun PostCard(
    post: PostItem,
    myUid: String,
    isFollowing: Boolean,
    isAdmin: Boolean,
    onLike: () -> Unit,
    onComment: (text: String, replyToUid: String?, replyToName: String?) -> Unit,
    onToggleFollow: () -> Unit,
    onVisitProfile: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    var showComments by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var previewImage by remember { mutableStateOf<String?>(null) }
    // Active reply target (uid + username) — when set, the comment box shows a
    // "replying to @name" hint and the mention is sent with the comment.
    var replyTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(BgCard, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = post.authorPhoto,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onVisitProfile() },
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.clickable { onVisitProfile() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF333333))
                    if (post.isAdmin) Text(" 🛡️", fontSize = 12.sp)
                    if (post.isAi) Text(" AI", fontSize = 10.sp, color = Color(0xFF6D28D9), fontWeight = FontWeight.Bold)
                    if (post.pinned) Text(" 📌 Disematkan", fontSize = 10.sp, color = BrandYellow, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            if (isAdmin) {
                Icon(
                    androidx.compose.material.icons.Icons.Filled.PushPin,
                    contentDescription = if (post.pinned) "Lepas sematan" else "Sematkan",
                    tint = if (post.pinned) BrandYellow else TextMuted,
                    modifier = Modifier.clickable { onTogglePin() }.padding(4.dp).size(18.dp),
                )
            }
            if (isAdmin || post.authorUid == myUid) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Hapus",
                    tint = ErrorRed,
                    modifier = Modifier.clickable { onDelete() }.padding(4.dp).size(18.dp),
                )
            }
            if (post.authorUid != myUid) {
                FollowButton(following = isFollowing, onClick = onToggleFollow)
            }
        }

        // Image (optional)
        if (!post.imageUrl.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(post.id) {
                        detectTapGestures(
                            onDoubleTap = { onLike() },
                            onTap = { previewImage = post.imageUrl },
                        )
                    },
            )
        }

        // Auto-embed YouTube when the caption contains a YouTube link.
        val ytId = remember(post.caption) { extractYouTubeId(post.caption) }
        if (ytId != null) {
            YouTubeEmbed(videoId = ytId)
        }

        // Actions
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (post.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Suka",
                tint = if (post.likedByMe) ErrorRed else Color(0xFF555555),
                modifier = Modifier.clickable { onLike() }.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = "Komentar",
                tint = Color(0xFF555555),
                modifier = Modifier.clickable { showComments = !showComments }.size(24.dp),
            )
        }

        if (post.likeCount > 0) {
            Text("${post.likeCount} suka", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
        }

        // Body / caption
        if (post.caption.isNotBlank()) {
            if (post.imageUrl.isNullOrBlank()) {
                Text(
                    post.caption,
                    fontSize = 14.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            } else {
                Text(
                    buildAnnotatedCaption(post.authorName, post.caption),
                    fontSize = 13.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        // Comment toggle
        Spacer(Modifier.height(4.dp))
        Text(
            if (post.comments.isEmpty()) "Tambahkan komentar..."
            else "Lihat semua ${post.comments.size} komentar",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.clickable { showComments = !showComments }.padding(2.dp),
        )
        if (showComments) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                post.comments.forEach { c ->
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        // Reply-to mention line ("membalas @name") for replies.
                        if (!c.replyTo.isNullOrBlank()) {
                            Text(
                                "↳ membalas @${c.replyTo}",
                                fontSize = 11.sp,
                                color = LinkBlue,
                                modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                            )
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                buildAnnotatedCaption(c.username, c.text, highlight = c.replyTo),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        // Tap a comment to start a reply to its author.
                                        replyTarget = c.uid to c.username
                                        commentText = "@${c.username} "
                                    },
                            )
                            Text(
                                "Balas",
                                color = LinkBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        replyTarget = c.uid to c.username
                                        commentText = "@${c.username} "
                                    }
                                    .padding(start = 6.dp, top = 2.dp),
                            )
                        }
                    }
                }

                // Reply-to hint banner above the input when replying.
                replyTarget?.let { (ruId, ruName) ->
                    Row(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(BrandYellow.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Membalas @${ruName}",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Batal",
                            color = LinkBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                replyTarget = null
                                commentText = ""
                            }.padding(start = 6.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = {
                            Text(
                                if (replyTarget != null) "Balas @${replyTarget!!.second}..."
                                else "Tambahkan komentar...",
                                fontSize = 13.sp,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Kirim",
                        color = BrandYellow,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            if (commentText.isNotBlank()) {
                                val (ruId, ruName) = replyTarget ?: (null to null)
                                onComment(commentText.trim(), ruId, ruName)
                                commentText = ""
                                replyTarget = null
                            }
                        }.padding(8.dp),
                    )
                }
            }
        }
    }

    // Full-screen image preview overlay.
    previewImage?.let { url ->
        ImagePreview(url = url, onDismiss = { previewImage = null })
    }
}

@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit) {
    Text(
        if (following) "Mengikuti" else "Ikuti",
        color = if (following) TextMuted else BrandYellow,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

// Caption with bold author prefix (mirrors `<b>name</b> caption`). When
// [highlight] is non-null, the `@highlight` mention in the text is styled as
// a blue link so replies stand out.
private fun buildAnnotatedCaption(name: String, text: String, highlight: String? = null): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
    append(" ")
    if (highlight.isNullOrBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    val mention = "@$highlight"
    var idx = 0
    while (idx <= text.length - mention.length) {
        val found = text.regionMatches(idx, mention, 0, ignoreCase = false)
        if (found) {
            withStyle(SpanStyle(color = LinkBlue, fontWeight = FontWeight.Bold)) { append(mention) }
            idx += mention.length
        } else {
            append(text[idx])
            idx++
        }
    }
    if (idx < text.length) append(text.substring(idx))
}
