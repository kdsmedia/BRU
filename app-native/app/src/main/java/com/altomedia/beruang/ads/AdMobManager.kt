package com.altomedia.beruang.ads

import android.app.Activity
import android.util.Log
import com.altomedia.beruang.BeruangApp
import com.altomedia.beruang.data.AppConstants
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CompletableDeferred

/**
 * AdMob manager — native Android port of the web `ADMOB` object.
 * Handles interstitial (preload + cooldown-gated show) and rewarded video
 * (await completion). The banner is rendered in Compose via [BannerAd].
 *
 * Test mode uses Google's official sample ad-unit IDs; production uses the
 * real unit IDs from [AppConstants.AdMob]. Mirrors `ADMOB.isTest()` / `unit()`.
 */
object AdMobManager {

    private const val TAG = "AdMobManager"
    private var initialized = false

    /** Test mode flag — mirrors web `ADMOB.isTest()`. Flip to true for test ads. */
    var testMode: Boolean = false

    private fun interstitialId() =
        if (testMode) AppConstants.AdMob.TEST_INTERSTITIAL_ID else AppConstants.AdMob.INTERSTITIAL_ID

    private fun rewardedId() =
        if (testMode) AppConstants.AdMob.TEST_REWARDED_ID else AppConstants.AdMob.REWARDED_ID

    /** One-time MobileAds SDK initialization (idempotent). */
    fun init() {
        if (initialized) return
        initialized = true
        try {
            MobileAds.initialize(BeruangApp.applicationContext()) {}
        } catch (e: Exception) {
            Log.e(TAG, "init failed", e)
        }
    }

    // ---- interstitial ------------------------------------------------
    private var interstitialAd: InterstitialAd? = null
    private var interstitialLoading = false
    private var lastInterstitialTs: Long = 0L

    /** Preload a full-screen interstitial (idempotent). Mirrors `prepareInterstitial`. */
    fun prepareInterstitial() {
        if (interstitialAd != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            BeruangApp.applicationContext(),
            interstitialId(),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    interstitialLoading = false
                }

                override fun onAdFailedToLoad(err: LoadAdError) {
                    interstitialAd = null
                    interstitialLoading = false
                    Log.w(TAG, "interstitial load failed: ${err.code}")
                }
            },
        )
    }

    /**
     * Show a full-screen interstitial only if (a) an ad is loaded and (b) at
     * least [AppConstants.AdMob.INTERSTITIAL_COOLDOWN_MS] have passed since
     * the last impression. Returns true when an ad was actually shown.
     * Mirrors web `ADMOB.maybeShowInterstitial`.
     */
    fun maybeShowInterstitial(activity: Activity): Boolean {
        val ad = interstitialAd
        if (ad == null) {
            prepareInterstitial()
            return false
        }
        val now = System.currentTimeMillis()
        if (now - lastInterstitialTs < AppConstants.AdMob.INTERSTITIAL_COOLDOWN_MS) return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                prepareInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                interstitialAd = null
                prepareInterstitial()
            }
        }
        ad.show(activity)
        lastInterstitialTs = System.currentTimeMillis()
        interstitialAd = null // consumed; dismissed callback will reload
        return true
    }

    // ---- rewarded video ----------------------------------------------
    /**
     * Show a rewarded video. Resolves true only when the user completed the
     * video (earned the reward). Mirrors web `ADMOB.showRewarded`.
     */
    suspend fun showRewarded(activity: Activity): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        RewardedAd.load(
            BeruangApp.applicationContext(),
            rewardedId(),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            if (!deferred.isCompleted) deferred.complete(false)
                        }

                        override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                            if (!deferred.isCompleted) deferred.complete(false)
                        }
                    }
                    ad.show(activity) { _ ->
                        if (!deferred.isCompleted) deferred.complete(true)
                    }
                }

                override fun onAdFailedToLoad(err: LoadAdError) {
                    if (!deferred.isCompleted) deferred.complete(false)
                }
            },
        )
        return deferred.await()
    }
}
