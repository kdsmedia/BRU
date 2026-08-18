package com.altomedia.beruang.ads

import android.app.Activity
import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
 *
 * Robustness fixes so the banner reliably renders:
 *  - the SDK is initialized up front (idempotent),
 *  - the [AdView] is created with the hosting **Activity** context (a plain
 *    application/ContextWrapper context prevents the ad from inflating),
 *  - an **adaptive** anchored banner size is computed from the real screen
 *    width (a fixed `AdSize.BANNER` can no-fill on many devices),
 *  - the [AdView] is paused/resumed/destroyed in lock-step with the Compose
 *    lifecycle, avoiding "AdView leak" and blank-after-return issues.
 */
@Composable
fun BannerAd(testMode: Boolean = false) {
    // Resolve the Activity context — AdView needs it to inflate its content.
    val activity = rememberActivityContext(LocalContext.current) ?: return
    val adUnitId = if (testMode) AppConstants.AdMob.TEST_BANNER_ID else AppConstants.AdMob.BANNER_ID
    val adSize = remember { adaptiveBannerSize(activity) }

    // Ensure the Mobile Ads SDK is initialized before the first load.
    remember { AdMobManager.init() }

    val adView = remember(activity) {
        AdView(activity).apply {
            setAdSize(adSize)
            this.adUnitId = adUnitId
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(adView, lifecycle) {
        adView.loadAd(AdRequest.Builder().build())
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            adView.destroy()
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        AndroidView(
            factory = { adView },
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

/** Walk the ContextWrapper chain to find the Activity; null if none. */
private fun rememberActivityContext(ctx: Context): Activity? {
    var c: Context = ctx
    while (c is android.content.ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Compute an adaptive anchored banner size for the current screen width. */
private fun adaptiveBannerSize(activity: Activity): AdSize {
    val out = DisplayMetrics()
    @Suppress("DEPRECATION")
    (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(out)
    val widthDp = out.widthPixels / out.density
    val widthPx = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp.toInt().coerceAtLeast(320)).width
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthPx)
}
