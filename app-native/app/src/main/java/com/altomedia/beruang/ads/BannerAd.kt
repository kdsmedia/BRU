package com.altomedia.beruang.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.altomedia.beruang.data.AppConstants
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Native AdMob banner — port of the web `ADMOB.showBanner` (adaptive banner,
 * inline below the stories row on home). Renders an [AdView] via [AndroidView].
 *
 * Test mode uses Google's official sample banner ID; production uses the real
 * ID from [AppConstants.AdMob].
 */
@Composable
fun BannerAd(testMode: Boolean = false) {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = if (testMode) AppConstants.AdMob.TEST_BANNER_ID else AppConstants.AdMob.BANNER_ID
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    Box(
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        AndroidView(
            factory = {
                adView.apply { loadAd(AdRequest.Builder().build()) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Banner ad wrapped in a post-like card block, so inline banner placements in
 * the feed / notif / profile look like a regular content card (matching the
 * original web "ad block" treatment).
 */
@Composable
fun BannerAdBlock(testMode: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            BannerAd(testMode = testMode)
        }
    }
}
