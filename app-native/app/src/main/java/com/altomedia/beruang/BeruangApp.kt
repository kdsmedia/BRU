package com.altomedia.beruang

import android.app.Application
import com.google.android.gms.ads.MobileAds

/**
 * Application bootstrap. Initializes the Google Mobile Ads SDK once on a
 * background thread so the first banner/interstitial load is fast.
 */
class BeruangApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // MobileAds init is safe off the main thread; do it on a background
        // dispatcher to avoid blocking app start.
        MobileAds.initialize(this) {}
    }

    companion object {
        private lateinit var instance: BeruangApp
        /** App-wide application context (safe to use from anywhere). */
        fun applicationContext(): android.content.Context = instance.applicationContext
    }
}
