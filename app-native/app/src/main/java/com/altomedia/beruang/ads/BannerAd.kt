package com.altomedia.beruang.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    Box(
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        AndroidView(
            factory = {
                adView.apply { loadAd(AdRequest.Builder().build()) }
            },
            update = { it.loadAd(AdRequest.Builder().build()) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
