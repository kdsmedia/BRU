package com.altomedia.beruang.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altomedia.beruang.R
import com.altomedia.beruang.ui.theme.BrandYellow
import com.altomedia.beruang.ui.theme.TextMain
import com.altomedia.beruang.ui.theme.TextMuted
import kotlinx.coroutines.delay

/** Total duration of the loading animation, in milliseconds. */
private const val SPLASH_DURATION_MS = 20_000L
/** How often the progress advances, in milliseconds. */
private const val STEP_MS = 200L

/**
 * Full-screen data-loading splash. Shows the app icon plus a progress bar that
 * rises naturally from 0% to exactly 100% over 20 seconds while the app
 * "loads data", then invokes [onComplete]. Progress advances in small steps
 * every [STEP_MS] with an ease-out feel (decelerating as it nears 100%).
 */
@Composable
fun SplashLoadingScreen(onComplete: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val steps = (SPLASH_DURATION_MS / STEP_MS).toInt()
        repeat(steps) { i ->
            // Ease-out curve so the bar decelerates approaching 100%.
            val t = (i + 1).toFloat() / steps
            progress = 1f - (1f - t) * (1f - t)
            delay(STEP_MS)
        }
        progress = 1f
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "BERUANG",
                modifier = Modifier.size(132.dp),
            )
            Text(
                "BERUANG",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                color = TextMain,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "Memuat data…",
                fontSize = 13.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp),
            )

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = BrandYellow,
                trackColor = BrandYellow.copy(alpha = 0.18f),
            )
            Text(
                "${(progress * 100).toInt()}%",
                fontSize = 12.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
