/**
 * @file MainActivity.kt
 * @description Main activity hosting the WebView that loads Twitter/X in read-only mode.
 */
package com.example.xreadonly

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "XReadOnly"
        private const val TWITTER_URL = "https://x.com"
        private const val CHROME_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupWebView()
        setupCookies()

        if (savedInstanceState == null) {
            webView.loadUrl(TWITTER_URL)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Loading $TWITTER_URL")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            // Required for Twitter to function
            javaScriptEnabled = true
            domStorageEnabled = true

            // Proper rendering
            useWideViewPort = true
            loadWithOverviewMode = true

            // Cache for performance
            cacheMode = WebSettings.LOAD_DEFAULT

            // User-Agent: Chrome mobile so Twitter serves full mobile web
            userAgentString = CHROME_MOBILE_UA

            // Media
            mediaPlaybackRequiresUserGesture = true

            // Mixed content: block for security
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Database storage
            databaseEnabled = true
        }

        // Basic WebViewClient — prevents opening links in external browser
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // For now, load everything in WebView (Stage 3 will add external link handling)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Navigation: $url")
                }
                return false
            }
        }

        // Basic WebChromeClient for progress/title
        webView.webChromeClient = WebChromeClient()
    }

    private fun setupCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Cookies configured: accept=true, thirdParty=true")
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        CookieManager.getInstance().flush()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    @Deprecated("Use OnBackPressedCallback instead", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
