/**
 * @file MainActivity.kt
 * @description Main activity hosting the WebView that loads Twitter/X in read-only mode.
 */
package com.example.xreadonly

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "XReadOnly"
        private const val TWITTER_URL = "https://x.com"
        private const val FIREFOX_FOCUS_PACKAGE = "org.mozilla.focus"
        private const val CHROME_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    private lateinit var webView: WebView
    private lateinit var errorView: LinearLayout
    private lateinit var errorMessage: TextView
    private var cssInjectionScript: String = ""
    private var jsInjectionScript: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadInjectionScripts()

        webView = findViewById(R.id.webView)
        errorView = findViewById(R.id.errorView)
        errorMessage = findViewById(R.id.errorMessage)

        findViewById<Button>(R.id.retryButton).setOnClickListener {
            hideError()
            webView.reload()
        }

        setupWebView()
        setupCookies()

        if (savedInstanceState == null) {
            val url = getDeepLinkUrl(intent) ?: TWITTER_URL
            webView.loadUrl(url)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Loading $url")
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val url = getDeepLinkUrl(intent)
        if (url != null) {
            hideError()
            webView.loadUrl(url)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Deep link received: $url")
            }
        }
    }

    /**
     * Extracts a Twitter/X URL from an incoming deep link intent.
     * Returns null if the intent doesn't contain a valid Twitter URL.
     */
    private fun getDeepLinkUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host == "x.com" || host.endsWith(".x.com") ||
            host == "twitter.com" || host.endsWith(".twitter.com")) {
            return uri.toString()
        }
        return null
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
            @Suppress("DEPRECATION")
            databaseEnabled = true
        }

        // WebViewClient with request blocking + CSS/JS injection
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false
                val host = url.host?.lowercase() ?: ""

                // Allow Twitter/X domains to load in WebView
                if (host == "x.com" || host.endsWith(".x.com") ||
                    host == "twitter.com" || host.endsWith(".twitter.com")) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Navigation (internal): $url")
                    }
                    return false
                }

                // External link → open in Chrome incognito, fallback to default browser
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "External link intercepted: $url")
                }
                openExternalLink(url)
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (request != null && RequestBlocker.shouldBlock(request)) {
                    // Return empty response to silently block the request
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        204,
                        "Blocked",
                        emptyMap(),
                        null
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Only show error view for main frame failures
                if (request?.isForMainFrame == true) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Page load error: ${error?.description} (code: ${error?.errorCode})")
                    }
                    showError()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectReadOnlyScripts(view)
            }
        }

        // Basic WebChromeClient for progress/title
        webView.webChromeClient = WebChromeClient()
    }

    private fun loadInjectionScripts() {
        val css = InjectionScripts.loadAsset(this, "inject.css")
        cssInjectionScript = InjectionScripts.buildCssInjectionScript(css)

        val js = InjectionScripts.loadAsset(this, "inject.js")
        jsInjectionScript = InjectionScripts.buildJsInjectionScript(js)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Injection scripts loaded (CSS: ${css.length} chars, JS: ${js.length} chars)")
        }
    }

    private fun injectReadOnlyScripts(view: WebView?) {
        view ?: return
        if (cssInjectionScript.isNotEmpty()) {
            view.evaluateJavascript(cssInjectionScript, null)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "CSS injected into page")
            }
        }
        if (jsInjectionScript.isNotEmpty()) {
            view.evaluateJavascript(jsInjectionScript, null)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "JS injected into page (MutationObserver + click interception)")
            }
        }
    }

    /**
     * Opens an external URL in Firefox Focus (private by default).
     * Falls back to the system default browser if Firefox Focus is unavailable.
     */
    private fun openExternalLink(uri: Uri) {
        // Try Firefox Focus first (always private browsing)
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(FIREFOX_FOCUS_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Opened in Firefox Focus: $uri")
            }
            return
        } catch (e: ActivityNotFoundException) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Firefox Focus not installed, falling back to default browser")
            }
        }

        // Fallback: system default browser
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Opened in default browser: $uri")
            }
        } catch (e: ActivityNotFoundException) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "No browser available to open: $uri")
            }
        }
    }

    private fun showError() {
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorView.visibility = View.GONE
        webView.visibility = View.VISIBLE
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
