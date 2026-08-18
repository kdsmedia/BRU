package com.altomedia.beruang.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
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
/** How often the progress target advances, in milliseconds. */
private const val STEP_MS = 40L

/**
 * Full-screen data-loading splash. Shows the app icon plus a progress bar that
 * rises naturally (ease-out) from 0% to exactly 100% over 20 seconds while the
 * app "loads data", then invokes [onComplete]. The progress is driven by a
 * target that advances in small increments and is smoothed by an
 * [animateFloatAsState] with an ease-out curve, so the bar decelerates as it
 * approaches 100% — mimicking the feel of real data loading.
 */
@Composable
fun SplashLoadingScreen(onComplete: () -> Unit) {
    var target by remember { mutableFloatStateOf(0f) }

    // Drive the progress target from 0→1 across the full duration.
    LaunchedEffect(Unit) {
        val steps = (SPLASH_DURATION_MS / STEP_MS).toInt()
        repeat(steps + 1) { i ->
            target = i.toFloat() / steps
            delay(STEP_MS)
        }
        target = 1f
        onComplete()
    }

    // Smooth the discrete target with an ease-out tween for a natural feel.
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = STEP_MS.toInt(), easing = LinearOutSlowInEasing),
        label = "splashProgress",
    )

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
                painter = painterResource(R.mipmap.ic_launcher),
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

            // Progress track + animated fill.
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
