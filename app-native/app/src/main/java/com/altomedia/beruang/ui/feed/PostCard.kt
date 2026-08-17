package com.altomedia.beruang.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
    onComment: (String) -> Unit,
    onToggleFollow: () -> Unit,
    onVisitProfile: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    var showComments by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }

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
                    .clickable { onLike() }, // double-tap→like; single tap used for parity
            )
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
                    Text(
                        buildAnnotatedCaption(c.username, c.text),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .clickable { },
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Tambahkan komentar...", fontSize = 13.sp) },
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
                                onComment(commentText.trim())
                                commentText = ""
                            }
                        }.padding(8.dp),
                    )
                }
            }
        }
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

// Caption with bold author prefix (mirrors `<b>name</b> caption`).
private fun buildAnnotatedCaption(name: String, text: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
    append(" ")
    append(text)
}
