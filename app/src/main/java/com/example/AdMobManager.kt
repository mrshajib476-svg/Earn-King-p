package com.example

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

import java.util.concurrent.Executors

class AdMobManager(private val context: Context) {

    private val tag = "AdMobManager"
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private var rewardedAd: RewardedAd? = null
    var isRewardedLoading: Boolean = false
        private set

    private var interstitialAd: InterstitialAd? = null
    var isInterstitialLoading: Boolean = false
        private set

    private var appOpenAd: AppOpenAd? = null
    var isAppOpenLoading: Boolean = false
        private set

    // Status listeners for UI synchronization
    var onAdStatusChangedListener: ((adType: String, isReady: Boolean) -> Unit)? = null

    init {
        initializeSdk()
    }

    private fun initializeSdk() {
        backgroundExecutor.execute {
            try {
                // Configure test device identifiers for emulators
                val testDeviceIds = listOf(AdRequest.DEVICE_ID_EMULATOR)
                val configuration = RequestConfiguration.Builder()
                    .setTestDeviceIds(testDeviceIds)
                    .build()
                MobileAds.setRequestConfiguration(configuration)

                MobileAds.initialize(context) { initializationStatus ->
                    Log.d(tag, "MobileAds initialized successfully: $initializationStatus")
                    // Start preloading all ad formats on main looper
                    handler.post {
                        loadAppOpenAd()
                        loadRewardedAd()
                        loadInterstitialAd()
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error initializing MobileAds SDK", e)
            }
        }
    }

    // ==========================================
    // APP OPEN AD IMPLEMENTATION (Shown on Login/Enter)
    // ==========================================

    fun isAppOpenAdReady(): Boolean = appOpenAd != null

    fun loadAppOpenAd() {
        if (isAppOpenLoading || appOpenAd != null) {
            return
        }

        isAppOpenLoading = true
        Log.d(tag, "Loading App Open Ad using Unit ID: ${AdConfig.appOpenAdUnitId}")
        val adRequest = AdRequest.Builder().build()

        AppOpenAd.load(
            context,
            AdConfig.appOpenAdUnitId,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(tag, "App Open Ad loaded successfully.")
                    appOpenAd = ad
                    isAppOpenLoading = false
                    onAdStatusChangedListener?.invoke("app_open", true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(tag, "App Open Ad failed to load: ${loadAdError.message}")
                    appOpenAd = null
                    isAppOpenLoading = false
                    onAdStatusChangedListener?.invoke("app_open", false)

                    handler.postDelayed({
                        if (appOpenAd == null && !isAppOpenLoading) {
                            loadAppOpenAd()
                        }
                    }, 5000)
                }
            }
        )
    }

    /**
     * Shows the App Open Ad immediately (e.g. when user logs in and enters app).
     */
    fun showAppOpenAd(activity: Activity, onAdDismissedOrUnavailable: () -> Unit = {}) {
        val currentAd = appOpenAd
        if (currentAd == null) {
            Log.w(tag, "App Open Ad not ready. Triggering load and continuing.")
            loadAppOpenAd()
            onAdDismissedOrUnavailable()
            return
        }

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(tag, "App Open Ad showed full screen.")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(tag, "App Open Ad failed to show: ${adError.message}")
                appOpenAd = null
                onAdStatusChangedListener?.invoke("app_open", false)
                loadAppOpenAd()
                onAdDismissedOrUnavailable()
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(tag, "App Open Ad dismissed.")
                appOpenAd = null
                onAdStatusChangedListener?.invoke("app_open", false)
                loadAppOpenAd()
                onAdDismissedOrUnavailable()
            }
        }

        currentAd.show(activity)
    }

    // ==========================================
    // REWARDED AD IMPLEMENTATION
    // ==========================================

    fun isRewardedAdReady(): Boolean = rewardedAd != null

    fun loadRewardedAd() {
        if (isRewardedLoading || rewardedAd != null) {
            Log.d(tag, "Rewarded ad is already loaded or currently loading.")
            return
        }

        isRewardedLoading = true
        Log.d(tag, "Loading Rewarded Ad using Unit ID: ${AdConfig.rewardedAdUnitId}")
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            AdConfig.rewardedAdUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(tag, "Rewarded Ad loaded successfully.")
                    rewardedAd = ad
                    isRewardedLoading = false
                    onAdStatusChangedListener?.invoke("rewarded", true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(tag, "Rewarded Ad failed to load: ${loadAdError.message} (code ${loadAdError.code})")
                    rewardedAd = null
                    isRewardedLoading = false
                    onAdStatusChangedListener?.invoke("rewarded", false)

                    // Auto-retry loading after 5 seconds
                    handler.postDelayed({
                        if (rewardedAd == null && !isRewardedLoading) {
                            loadRewardedAd()
                        }
                    }, 5000)
                }
            }
        )
    }

    /**
     * Shows the Rewarded Ad strictly verifying that it is loaded.
     * Rewards are ONLY dispatched from within Google's OnUserEarnedRewardListener.
     */
    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: (coins: Int) -> Unit,
        onAdClosed: (earnedReward: Boolean) -> Unit,
        onAdUnavailable: () -> Unit
    ) {
        val currentAd = rewardedAd

        if (currentAd == null) {
            Log.w(tag, "showRewardedAd called but ad is not available.")
            onAdUnavailable()
            loadRewardedAd()
            return
        }

        var rewardGranted = false

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(tag, "Rewarded Ad showed full screen content.")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(tag, "Rewarded Ad failed to show: ${adError.message}")
                rewardedAd = null
                onAdStatusChangedListener?.invoke("rewarded", false)
                onAdUnavailable()
                loadRewardedAd()
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(tag, "Rewarded Ad was dismissed by user. Reward granted: $rewardGranted")
                rewardedAd = null
                onAdStatusChangedListener?.invoke("rewarded", false)
                onAdClosed(rewardGranted)
                loadRewardedAd()
            }
        }

        currentAd.show(activity) { rewardItem ->
            Log.d(tag, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            rewardGranted = true
            val coinsEarned = if (rewardItem.amount > 0) rewardItem.amount else AdConfig.REWARD_COIN_AMOUNT
            onRewardEarned(coinsEarned)
        }
    }

    // ==========================================
    // INTERSTITIAL AD IMPLEMENTATION
    // ==========================================

    fun isInterstitialAdReady(): Boolean = interstitialAd != null

    fun loadInterstitialAd() {
        if (isInterstitialLoading || interstitialAd != null) {
            Log.d(tag, "Interstitial ad is already loaded or currently loading.")
            return
        }

        isInterstitialLoading = true
        Log.d(tag, "Loading Interstitial Ad using Unit ID: ${AdConfig.interstitialAdUnitId}")
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            AdConfig.interstitialAdUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(tag, "Interstitial Ad loaded successfully.")
                    interstitialAd = ad
                    isInterstitialLoading = false
                    onAdStatusChangedListener?.invoke("interstitial", true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(tag, "Interstitial Ad failed to load: ${loadAdError.message}")
                    interstitialAd = null
                    isInterstitialLoading = false
                    onAdStatusChangedListener?.invoke("interstitial", false)

                    // Auto-retry loading after 5 seconds
                    handler.postDelayed({
                        if (interstitialAd == null && !isInterstitialLoading) {
                            loadInterstitialAd()
                        }
                    }, 5000)
                }
            }
        )
    }

    /**
     * Shows Interstitial Ad at natural transition points.
     * NOTE: Per AdMob policy and project requirements, Interstitial Ads must NEVER award coins.
     */
    fun showInterstitialAd(
        activity: Activity,
        onAdClosed: () -> Unit,
        onAdUnavailable: () -> Unit
    ) {
        val currentAd = interstitialAd

        if (currentAd == null) {
            Log.w(tag, "showInterstitialAd called but ad is not available.")
            onAdUnavailable()
            loadInterstitialAd()
            return
        }

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(tag, "Interstitial Ad showed full screen.")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(tag, "Interstitial Ad failed to show: ${adError.message}")
                interstitialAd = null
                onAdStatusChangedListener?.invoke("interstitial", false)
                onAdClosed()
                loadInterstitialAd()
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(tag, "Interstitial Ad was dismissed.")
                interstitialAd = null
                onAdStatusChangedListener?.invoke("interstitial", false)
                onAdClosed()
                loadInterstitialAd()
            }
        }

        currentAd.show(activity)
    }
}
