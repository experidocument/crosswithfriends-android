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

        // Domains required for seamless in-app Google Sign-In and OAuth
        private val AUTH_HOSTS = listOf(
            "accounts.google.com",
            "accounts.youtube.com",
            "apis.google.com",
            "ssl.gstatic.com",
            "appleid.apple.com",
            "github.com"
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentUrl(intent)
    }

    private fun handleIntentUrl(incomingIntent: Intent?) {
        val appLinkData: Uri? = incomingIntent?.data
        if (appLinkData != null && (isAllowedHost(appLinkData.host) || isAuthHost(appLinkData.host))) {
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

    private fun isAuthHost(host: String?): Boolean {
        if (host == null) return false
        return AUTH_HOSTS.any { auth -> host.equals(auth, ignoreCase = true) || host.endsWith(".$auth") }
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

        // Enable popup windows & multi-window support for Google Sign-In
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        // Viewport and smooth touch settings
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // Modern Mobile Chrome User-Agent (strips WebView tags to avoid OAuth rejection)
        val defaultUserAgent = settings.userAgentString
        val sanitizedUserAgent = defaultUserAgent
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
        settings.userAgentString = sanitizedUserAgent

        // Enable third party cookies for cross-origin JWT authentication
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

            // Route OAuth popups directly inside this WebView so cookies are shared
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
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

                // If internal URL OR an OAuth sign-in domain, keep it inside the WebView!
                if (isAllowedHost(host) || isAuthHost(host)) {
                    return false
                }

                // External websites (e.g. donations, external links) open in Chrome Custom Tab
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

                // Smooth Parallax Transition: start slight slide and fade
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

                // Flush auth cookies to disk
                CookieManager.getInstance().flush()

                // Save game room URL for session recovery
                if (url != null && isAllowedHost(Uri.parse(url).host)) {
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(KEY_LAST_URL, url).apply()
                }

                // Smooth Parallax Transition: ease in
                binding.webView.animate()
                    .alpha(1.0f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                // Inject enhanced fonts, clue expansion, and minimalist UX styling
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
     * Injects CSS & JS to fix clue overflow (Issue 6), apply modern fonts (Issue 4),
     * and smooth parallax transitions (Issue 3).
     */
    private fun injectMinimalistStyling(view: WebView?) {
        val js = """
            (function() {
                var styleId = 'zen-app-styling-v2';
                var existing = document.getElementById(styleId);
                if (existing) existing.remove();

                var style = document.createElement('style');
                style.id = styleId;
                style.innerHTML = `
                    /* Modern System Typography */
                    html, body, * {
                        font-family: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif !important;
                    }

                    html, body {
                        margin: 0 !important;
                        padding: 0 !important;
                        -webkit-tap-highlight-color: transparent !important;
                        overscroll-behavior-y: none !important;
                    }

                    /* Parallax Transition for dynamic room loads */
                    .game--container, .game, main, #root {
                        animation: zenParallax 0.3s cubic-bezier(0.2, 0.8, 0.2, 1) !important;
                    }
                    @keyframes zenParallax {
                        0% { opacity: 0.3; transform: translateY(14px); }
                        100% { opacity: 1; transform: translateY(0); }
                    }

                    /* Issue 6 Fix: Expand and enable scroll for the Clue Bar */
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

                    /* Crossword board width optimization */
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

    /**
     * Issue 2 Fix: In React SPAs, if the user is in a room or game URL,
     * back should navigate out of the game room or back in web history,
     * instead of immediately prompting to exit!
     */
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
                    // Navigate back to the main puzzles list / homepage
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
