package com.example

import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * Secure JavaScript Interface exposed to the WebView under the name "AndroidBridge".
 */
class WebAppInterface(private val activity: MainActivity) {

    @JavascriptInterface
    fun showRewardedAd() {
        activity.runOnUiThread {
            activity.showRewardedAd()
        }
    }

    @JavascriptInterface
    fun showInterstitialAd() {
        activity.runOnUiThread {
            activity.showInterstitialAd()
        }
    }

    @JavascriptInterface
    fun showAppOpenAd() {
        activity.runOnUiThread {
            activity.showAppOpenAd()
        }
    }

    @JavascriptInterface
    fun isRewardedAdLoaded(): Boolean {
        return activity.isRewardedAdReady()
    }

    @JavascriptInterface
    fun isInterstitialAdLoaded(): Boolean {
        return activity.isInterstitialAdReady()
    }

    @JavascriptInterface
    fun isAppOpenAdLoaded(): Boolean {
        return activity.isAppOpenAdReady()
    }

    @JavascriptInterface
    fun loadRewardedAd() {
        activity.runOnUiThread {
            activity.loadRewardedAd()
        }
    }

    @JavascriptInterface
    fun loadInterstitialAd() {
        activity.runOnUiThread {
            activity.loadInterstitialAd()
        }
    }

    @JavascriptInterface
    fun setBannerVisibility(visible: Boolean) {
        activity.runOnUiThread {
            activity.setBannerVisibility(visible)
        }
    }

    @JavascriptInterface
    fun loginWithEmail(email: String, pass: String) {
        activity.loginWithEmail(email, pass)
    }

    @JavascriptInterface
    fun signUpWithEmail(email: String, pass: String, displayName: String) {
        activity.signUpWithEmail(email, pass, displayName)
    }

    @JavascriptInterface
    fun logout() {
        activity.logout()
    }

    @JavascriptInterface
    fun claimDailyBonus(userId: String, bonusAmount: Long) {
        activity.claimDailyBonus(userId, bonusAmount)
    }

    @JavascriptInterface
    fun fetchUserData(userId: String) {
        activity.fetchUserData(userId)
    }

    @JavascriptInterface
    fun showToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun syncBalance(userId: String, balance: Long, adsWatched: Int, todayEarnings: Long) {
        activity.runOnUiThread {
            activity.syncBalanceWithFirebase(userId, balance, adsWatched, todayEarnings)
        }
    }

    @JavascriptInterface
    fun recordTransaction(userId: String, txType: String, amount: Long, description: String) {
        activity.runOnUiThread {
            activity.recordTransaction(userId, txType, amount, description)
        }
    }

    @JavascriptInterface
    fun getAdConfigJson(): String {
        return """
            {
                "useTestAds": ${AdConfig.USE_TEST_ADS},
                "rewardCoins": ${AdConfig.REWARD_COIN_AMOUNT},
                "appOpenId": "${AdConfig.appOpenAdUnitId}",
                "rewardedId": "${AdConfig.rewardedAdUnitId}",
                "interstitialId": "${AdConfig.interstitialAdUnitId}",
                "bannerId": "${AdConfig.bannerAdUnitId}"
            }
        """.trimIndent()
    }
}
