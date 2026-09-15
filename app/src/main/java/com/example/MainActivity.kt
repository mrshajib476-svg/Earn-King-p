package com.example

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

class MainActivity : ComponentActivity() {

    private val tag = "MainActivity"

    private lateinit var webView: WebView
    private lateinit var adMobManager: AdMobManager
    private lateinit var firebaseHelper: FirebaseHelper
    private var bannerAdView: AdView? = null
    private lateinit var bannerContainer: FrameLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure WebView cache directories exist to prevent chromium simple_file_enumerator error
        prepareWebViewDirectories()

        // Initialize helper systems
        adMobManager = AdMobManager(this)
        firebaseHelper = FirebaseHelper(this)

        // Build root layout programmatically
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0F172A")) // Modern dark blue canvas
        }

        // Setup WebView
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
        setupWebView()

        // Banner Ad Container (at bottom of screen, responsive)
        bannerContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#0F172A"))
            visibility = View.VISIBLE
        }
        setupBannerAd()

        rootLayout.addView(webView)
        rootLayout.addView(bannerContainer)
        setContentView(rootLayout)

        // Apply Window Insets for edge-to-edge support
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            bannerContainer.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        // Setup Ad status listener
        adMobManager.onAdStatusChangedListener = { adType, isReady ->
            runOnUiThread {
                notifyAdStatusToWeb(adType, isReady)
            }
        }

        // Back navigation handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        // Load the HTML web app from assets
        webView.loadUrl("file:///android_asset/index.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Register secure JS interface
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("WebViewConsole", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Sync initial AdMob state to web layer once page is fully loaded
                notifyAdStatusToWeb("app_open", adMobManager.isAppOpenAdReady())
                notifyAdStatusToWeb("rewarded", adMobManager.isRewardedAdReady())
                notifyAdStatusToWeb("interstitial", adMobManager.isInterstitialAdReady())

                // Check if user already logged in via Firebase Auth
                val currentUser = firebaseHelper.getCurrentUser()
                if (currentUser != null) {
                    val uid = currentUser.uid
                    val email = currentUser.email ?: ""
                    val name = currentUser.displayName ?: email.substringBefore("@")
                    val script = "javascript:if(window.onFirebaseAutoAuth){window.onFirebaseAutoAuth('$uid', '$name', '$email');}"
                    webView.evaluateJavascript(script, null)
                }
            }
        }
    }

    private fun prepareWebViewDirectories() {
        try {
            val cacheSubDirs = listOf(
                java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js"),
                java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm"),
                java.io.File(cacheDir, "WebView/Default/HTTP Cache")
            )
            for (dir in cacheSubDirs) {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Cache directory initialization: ${e.message}")
        }
    }

    private fun setupBannerAd() {
        try {
            val adView = AdView(this)
            adView.setAdSize(AdSize.BANNER)
            adView.adUnitId = AdConfig.bannerAdUnitId

            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(tag, "AdMob Banner Ad loaded successfully.")
                    notifyAdStatusToWeb("banner", true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(tag, "AdMob Banner Ad failed to load: ${error.message}")
                    notifyAdStatusToWeb("banner", false)
                }
            }

            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)

            bannerAdView = adView
            bannerContainer.addView(adView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            ))
        } catch (e: Exception) {
            Log.e(tag, "Error setting up Banner Ad", e)
        }
    }

    // ====================================================
    // Bridge invocations from WebAppInterface
    // ====================================================

    fun showAppOpenAd(onDismissed: () -> Unit = {}) {
        runOnUiThread {
            adMobManager.showAppOpenAd(this, onDismissed)
        }
    }

    fun isAppOpenAdReady(): Boolean = adMobManager.isAppOpenAdReady()

    fun showRewardedAd() {
        adMobManager.showRewardedAd(
            activity = this,
            onRewardEarned = { amount ->
                runOnUiThread {
                    Log.d(tag, "Delivering official reward to web layer: $amount Coins")
                    val script = "javascript:window.onAdMobRewardEarned($amount, 'COINS');"
                    webView.evaluateJavascript(script, null)
                }
            },
            onAdClosed = { earnedReward ->
                runOnUiThread {
                    Log.d(tag, "Rewarded Ad closed. Earned: $earnedReward")
                    val script = "javascript:window.onAdMobRewardedClosed($earnedReward);"
                    webView.evaluateJavascript(script, null)
                }
            },
            onAdUnavailable = {
                runOnUiThread {
                    Log.w(tag, "Rewarded Ad is unavailable.")
                    val script = "javascript:window.onAdMobAdUnavailable('rewarded');"
                    webView.evaluateJavascript(script, null)
                }
            }
        )
    }

    fun showInterstitialAd() {
        adMobManager.showInterstitialAd(
            activity = this,
            onAdClosed = {
                runOnUiThread {
                    val script = "javascript:window.onAdMobInterstitialClosed();"
                    webView.evaluateJavascript(script, null)
                }
            },
            onAdUnavailable = {
                runOnUiThread {
                    val script = "javascript:window.onAdMobAdUnavailable('interstitial');"
                    webView.evaluateJavascript(script, null)
                }
            }
        )
    }

    fun isRewardedAdReady(): Boolean = adMobManager.isRewardedAdReady()

    fun isInterstitialAdReady(): Boolean = adMobManager.isInterstitialAdReady()

    fun loadRewardedAd() {
        adMobManager.loadRewardedAd()
    }

    fun loadInterstitialAd() {
        adMobManager.loadInterstitialAd()
    }

    fun setBannerVisibility(visible: Boolean) {
        bannerContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    // Firebase Authentication Bridge
    fun loginWithEmail(email: String, pass: String) {
        firebaseHelper.loginWithEmail(email, pass) { success, uid, name, error ->
            runOnUiThread {
                val escapedError = error?.replace("'", "\\'") ?: ""
                val escapedUid = uid?.replace("'", "\\'") ?: ""
                val escapedName = name?.replace("'", "\\'") ?: ""
                val script = "javascript:if(window.onFirebaseLoginResult){window.onFirebaseLoginResult($success, '$escapedUid', '$escapedName', '$escapedError');}"
                webView.evaluateJavascript(script, null)

                if (success) {
                    // "app যখন কেউ Login করে ডুকবে সাথে সাথে appoper ads show হবে"
                    showAppOpenAd()
                }
            }
        }
    }

    fun signUpWithEmail(email: String, pass: String, displayName: String) {
        firebaseHelper.signUpWithEmail(email, pass, displayName) { success, uid, error ->
            runOnUiThread {
                val escapedError = error?.replace("'", "\\'") ?: ""
                val escapedUid = uid?.replace("'", "\\'") ?: ""
                val escapedName = displayName.replace("'", "\\'")
                val script = "javascript:if(window.onFirebaseSignUpResult){window.onFirebaseSignUpResult($success, '$escapedUid', '$escapedName', '$escapedError');}"
                webView.evaluateJavascript(script, null)

                if (success) {
                    showAppOpenAd()
                }
            }
        }
    }

    fun logout() {
        firebaseHelper.logout()
        runOnUiThread {
            webView.evaluateJavascript("javascript:if(window.onFirebaseLogoutResult){window.onFirebaseLogoutResult();}", null)
        }
    }

    fun claimDailyBonus(userId: String, bonusAmount: Long) {
        firebaseHelper.claimDailyLoginBonus(userId, bonusAmount) { success, newBalance, error ->
            runOnUiThread {
                val escapedError = error?.replace("'", "\\'") ?: ""
                val script = "javascript:if(window.onDailyBonusClaimResult){window.onDailyBonusClaimResult($success, $newBalance, '$escapedError');}"
                webView.evaluateJavascript(script, null)
            }
        }
    }

    fun fetchUserData(userId: String) {
        firebaseHelper.fetchUserData(userId) { balance, todayEarned, adsWatched, lastBonus ->
            runOnUiThread {
                val script = "javascript:if(window.onUserDataFetched){window.onUserDataFetched($balance, $todayEarned, $adsWatched, '$lastBonus');}"
                webView.evaluateJavascript(script, null)
            }
        }
    }

    fun syncBalanceWithFirebase(userId: String, balance: Long, adsWatched: Int, todayEarnings: Long) {
        firebaseHelper.syncUserData(userId, balance, adsWatched, todayEarnings) { success ->
            runOnUiThread {
                val script = "javascript:if(window.onFirebaseSyncCompleted){window.onFirebaseSyncCompleted($success);}"
                webView.evaluateJavascript(script, null)
            }
        }
    }

    fun recordTransaction(userId: String, txType: String, amount: Long, description: String) {
        firebaseHelper.recordTransaction(userId, txType, amount, description) { success ->
            Log.d(tag, "Transaction logged to Firebase: $success")
        }
    }

    private fun notifyAdStatusToWeb(adType: String, isReady: Boolean) {
        val script = "javascript:if(window.onAdMobAdStatusChanged){window.onAdMobAdStatusChanged('$adType', $isReady);}"
        webView.evaluateJavascript(script, null)
    }

    // ====================================================
    // Lifecycle Management
    // ====================================================

    override fun onResume() {
        super.onResume()
        webView.onResume()
        bannerAdView?.resume()
    }

    override fun onPause() {
        bannerAdView?.pause()
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        bannerAdView?.destroy()
        webView.destroy()
        super.onDestroy()
    }
}
