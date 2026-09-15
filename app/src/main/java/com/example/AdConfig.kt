package com.example

/**
 * AdMob Configuration for Watch & Earn Coins Android App.
 *
 * During development, [USE_TEST_ADS] is set to true to ensure compliance with
 * Google AdMob policies and prevent invalid traffic flags.
 *
 * Production Ad Unit IDs provided by user:
 * - ADMOB_APP_ID: ca-app-pub-1989230444855050~4627341416
 * - ADMOB_BANNER_ID: ca-app-pub-1989230444855050/8603099072
 * - ADMOB_INTERSTITIAL_ID: ca-app-pub-1989230444855050/5484832362
 * - ADMOB_REWARDED_ID: ca-app-pub-1989230444855050/6747196158
 * - ADMOB_APP_OPEN_ID: ca-app-pub-1989230444855050/5490132410
 * - NATIVE_ADVANCED_ID: ca-app-pub-1989230444855050/6843294847
 */
object AdConfig {
    // Set to true during development to use Google official test IDs.
    // Set to false for release production build.
    var USE_TEST_ADS: Boolean = false

    // Official Google AdMob Test Ad Unit IDs
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921"
    const val TEST_NATIVE_ADVANCED_ID = "ca-app-pub-3940256099942544/2247696110"

    // Production IDs from user configuration
    const val PROD_APP_ID = "ca-app-pub-1989230444855050~4627341416"
    const val PROD_BANNER_ID = "ca-app-pub-1989230444855050/8603099072"
    const val PROD_INTERSTITIAL_ID = "ca-app-pub-1989230444855050/5484832362"
    const val PROD_REWARDED_ID = "ca-app-pub-1989230444855050/6747196158"
    const val PROD_APP_OPEN_ID = "ca-app-pub-1989230444855050/5490132410"
    const val PROD_NATIVE_ADVANCED_ID = "ca-app-pub-1989230444855050/6843294847"

    const val REWARD_COIN_AMOUNT = 200

    val bannerAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_BANNER_ID else PROD_BANNER_ID

    val interstitialAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_INTERSTITIAL_ID else PROD_INTERSTITIAL_ID

    val rewardedAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_REWARDED_ID else PROD_REWARDED_ID

    val appOpenAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_APP_OPEN_ID else PROD_APP_OPEN_ID

    val nativeAdvancedAdUnitId: String
        get() = if (USE_TEST_ADS) TEST_NATIVE_ADVANCED_ID else PROD_NATIVE_ADVANCED_ID
}
