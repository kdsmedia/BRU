package com.altomedia.beruang.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.altomedia.beruang.ui.theme.TextMuted

/**
 * Extracts the first YouTube video id from any of the common URL shapes:
 * youtu.be/<id>, youtube.com/watch?v=<id>, youtube.com/embed/<id>,
 * youtube.com/shorts/<id>. Returns null if none found.
 */
fun extractYouTubeId(text: String): String? {
    val patterns = listOf(
        Regex("""(?:https?://)?(?:www\.)?youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""(?:https?://)?(?:www\.)?youtube\.com/watch\?[^ ]*v=([A-Za-z0-9_-]{11})"""),
        Regex("""(?:https?://)?(?:www\.)?youtube\.com/embed/([A-Za-z0-9_-]{11})"""),
        Regex("""(?:https?://)?(?:www\.)?youtube\.com/shorts/([A-Za-z0-9_-]{11})"""),
    )
    for (p in patterns) {
        val m = p.find(text) ?: continue
        return m.groupValues.getOrNull(1)
    }
    return null
}

/**
 * YouTube auto-embed preview shown inline in a post when the caption contains
 * a YouTube link. Renders the video thumbnail with a play button; tapping it
 * opens the video in the YouTube app / browser (no in-app player dependency).
 */
@Composable
fun YouTubeEmbed(videoId: String) {
    val context = LocalContext.current
    val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    val watchUrl = "https://www.youtube.com/watch?v=$videoId"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl)))
            },
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(
                model = thumb,
                contentDescription = "Video YouTube",
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = "Putar video",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Text(
            "youtube.com",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color(0x80000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
    Text("Tonton di YouTube", color = TextMuted, fontSize = 11.sp)
}
