package com.crosswithfriends.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.crosswithfriends.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private var backPressedTime: Long = 0
    private var returnedFromExternalAuth = false

    companion object {
        const val BASE_URL = "https://www.crosswithfriends.com/"
        private const val PREFS_NAME = "CrossWithFriendsPrefs"
        private const val KEY_LAST_URL = "last_visited_url"

        private val ALLOWED_HOSTS = listOf(
            "crosswithfriends.com",
            "www.crosswithfriends.com",
            "downforacross.com",
            "www.downforacross.com"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WebView.setWebContentsDebuggingEnabled(true)

        setupFileChooser()
        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()
        setupErrorHandling()
        setupFab()

        handleIntentUrl(intent)
    }

    override fun onResume() {
        super.onResume()
        // If returning from external browser OAuth, refresh to apply updated sessions
        if (returnedFromExternalAuth) {
            returnedFromExternalAuth = false
            CookieManager.getInstance().flush()
            binding.webView.reload()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentUrl(intent)
    }

    private fun handleIntentUrl(incomingIntent: Intent?) {
        val appLinkData: Uri? = incomingIntent?.data
        if (appLinkData != null && isAllowedHost(appLinkData.host)) {
            binding.webView.loadUrl(appLinkData.toString())
        } else {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedUrl = prefs.getString(KEY_LAST_URL, null)
            if (!savedUrl.isNullOrEmpty() && isAllowedHost(Uri.parse(savedUrl).host)) {
                binding.webView.loadUrl(savedUrl)
            } else if (binding.webView.url.isNullOrEmpty()) {
                binding.webView.loadUrl(BASE_URL)
            }
        }
    }

    private fun isAllowedHost(host: String?): Boolean {
        if (host == null) return false
        return ALLOWED_HOSTS.any { allowed -> host.equals(allowed, ignoreCase = true) || host.endsWith(".$allowed") }
    }

    private fun isGoogleAuthUrl(url: Uri): Boolean {
        val host = url.host ?: return false
        val path = url.path ?: ""
        return host.contains("accounts.google.com") ||
               path.contains("/auth/google") ||
               path.contains("/api/auth/google") ||
               url.toString().contains("oauth") && host.contains("google")
    }

    private fun setupFab() {
        binding.fabHint.setOnClickListener {
            val dialog = HintBottomSheetDialog()
            dialog.show(supportFragmentManager, HintBottomSheetDialog.TAG)
        }
    }

    private fun setupFileChooser() {
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (fileUploadCallback == null) return@registerForActivityResult

            val results: Array<Uri>? = when {
                result.resultCode == RESULT_OK && result.data != null -> {
                    val data = result.data
                    if (data?.clipData != null) {
                        val count = data.clipData!!.itemCount
                        Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                    } else if (data?.data != null) {
                        arrayOf(data.data!!)
                    } else null
                }
                else -> null
            }

            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        val defaultUserAgent = settings.userAgentString
        val sanitizedUserAgent = defaultUserAgent
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
        settings.userAgentString = sanitizedUserAgent

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(WebAppInterface(this), "AndroidApp")

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    addRequestHeader("User-Agent", userAgent)
                    val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                    setTitle(filename)
                    setDescription("Downloading puzzle file...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Downloading file...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        })

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                if (newProgress in 1..99) {
                    binding.progressBar.visibility = View.VISIBLE
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.initialSpinner.visibility = View.GONE
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val hrefMsg = view?.handler?.obtainMessage()
                view?.requestFocusNodeHref(hrefMsg)
                val targetUrl = hrefMsg?.data?.getString("url")

                if (targetUrl != null && isGoogleAuthUrl(Uri.parse(targetUrl))) {
                    launchBrowserForGoogleAuth(Uri.parse(targetUrl))
                    return true
                }

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = view
                resultMsg?.sendToTarget()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    fileUploadCallback = null
                    return false
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val host = url.host

                // If Google Sign-In is triggered, redirect to external browser per user request
                if (isGoogleAuthUrl(url)) {
                    launchBrowserForGoogleAuth(url)
                    return true
                }

                // If internal URL, load within the WebView
                if (isAllowedHost(host)) {
                    return false
                }

                // External websites open in Chrome Custom Tab
                if (url.scheme == "https" || url.scheme == "http") {
                    try {
                        val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
                        customTabsIntent.launchUrl(this@MainActivity, url)
                        return true
                    } catch (e: Exception) {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                        return true
                    }
                }

                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    return true
                } catch (e: Exception) {
                    return true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.errorView.visibility = View.GONE

                binding.webView.animate()
                    .alpha(0.35f)
                    .translationY(16f)
                    .setDuration(120)
                    .start()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefreshLayout.isRefreshing = false
                binding.initialSpinner.visibility = View.GONE

                CookieManager.getInstance().flush()

                if (url != null && isAllowedHost(Uri.parse(url).host)) {
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(KEY_LAST_URL, url).apply()
                }

                binding.webView.animate()
                    .alpha(1.0f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                injectMinimalistStyling(view)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true && !isNetworkAvailable()) {
                    binding.errorView.visibility = View.VISIBLE
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    /**
     * Launches external Chrome / System Browser for Google OAuth so the user
     * can select their existing device Google account without typing passwords.
     */
    private fun launchBrowserForGoogleAuth(url: Uri) {
        returnedFromExternalAuth = true
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(this, url)
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, url)
            startActivity(browserIntent)
        }
    }

    private fun injectMinimalistStyling(view: WebView?) {
        val js = """
            (function() {
                var styleId = 'zen-app-styling-v3';
                var existing = document.getElementById(styleId);
                if (existing) existing.remove();

                var style = document.createElement('style');
                style.id = styleId;
                style.innerHTML = `
                    html, body, * {
                        font-family: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif !important;
                    }

                    html, body {
                        margin: 0 !important;
                        padding: 0 !important;
                        -webkit-tap-highlight-color: transparent !important;
                        overscroll-behavior-y: none !important;
                    }

                    .game--container, .game, main, #root {
                        animation: zenParallax 0.3s cubic-bezier(0.2, 0.8, 0.2, 1) !important;
                    }
                    @keyframes zenParallax {
                        0% { opacity: 0.3; transform: translateY(14px); }
                        100% { opacity: 1; transform: translateY(0); }
                    }

                    .clue-bar, [class*="clueBar"], [class*="clue-bar"], [class*="ClueBar"], [class*="current-clue"] {
                        min-height: 52px !important;
                        max-height: 110px !important;
                        height: auto !important;
                        overflow-y: auto !important;
                        -webkit-overflow-scrolling: touch !important;
                        padding: 6px 10px !important;
                        display: flex !important;
                        align-items: center !important;
                    }

                    .clue-bar *, [class*="clueBar"] *, [class*="clue-bar"] *, [class*="clue-text"] {
                        white-space: normal !important;
                        text-overflow: unset !important;
                        overflow: visible !important;
                        line-height: 1.35 !important;
                        font-size: 14.5px !important;
                    }

                    .board-container, .cw-board {
                        padding: 2px !important;
                        margin: 0 auto !important;
                        max-width: 100vw !important;
                    }
                `;
                document.head.appendChild(style);
            })();
        """.trimIndent()

        view?.evaluateJavascript(js, null)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.accent,
            R.color.primary_dark
        )

        binding.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            binding.webView.scrollY > 0
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            if (isNetworkAvailable()) {
                binding.webView.reload()
            } else {
                binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this, getString(R.string.error_message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentUrl = binding.webView.url ?: ""
                val isAtRoot = currentUrl.isEmpty() ||
                               currentUrl == BASE_URL ||
                               currentUrl == "https://crosswithfriends.com/" ||
                               currentUrl == "https://www.crosswithfriends.com"

                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else if (!isAtRoot) {
                    binding.webView.evaluateJavascript(
                        "if (window.history.length > 1) { window.history.back(); } else { window.location.href = '/'; }",
                        null
                    )
                } else {
                    if (System.currentTimeMillis() - backPressedTime < 2000) {
                        finish()
                    } else {
                        backPressedTime = System.currentTimeMillis()
                        Toast.makeText(this@MainActivity, getString(R.string.exit_prompt), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun setupErrorHandling() {
        binding.btnRetry.setOnClickListener {
            binding.errorView.visibility = View.GONE
            binding.initialSpinner.visibility = View.VISIBLE
            binding.webView.reload()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onDestroy() {
        binding.webView.stopLoading()
        binding.webView.destroy()
        super.onDestroy()
    }
}
