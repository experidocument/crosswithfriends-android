package com.crosswithfriends.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.crosswithfriends.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private var backPressedTime: Long = 0

    private var isOnHomePage = true
    private var isKeyboardActive = false
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    companion object {
        const val BASE_URL = "https://www.crosswithfriends.com/"
        private const val PREFS_NAME = "CrossWithFriendsPrefs"
        private const val KEY_LAST_URL = "last_visited_url"
        private const val KEY_FIRST_LAUNCH = "is_first_launch_v14"

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
        setupFabButtons()
        setupHomeButton()
        setupKeyboardDetection()

        handleIntentUrl(intent)
        checkFirstLaunchOnboarding()
    }

    private fun checkFirstLaunchOnboarding() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FIRST_LAUNCH, true)) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    showHelpDialog()
                }
            }, 800)
        }
    }

    private fun showHelpDialog() {
        HelpBottomSheetDialog().show(supportFragmentManager, HelpBottomSheetDialog.TAG)
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
               (url.toString().contains("oauth") && host.contains("google"))
    }

    private fun setupFabButtons() {
        // Bottom-Right Hint & Learning Companion Button
        binding.fabHint.setOnClickListener {
            val dialog = HintBottomSheetDialog()
            dialog.show(supportFragmentManager, HintBottomSheetDialog.TAG)
        }

        // Bottom-Left Mini Help & Onboarding Button
        binding.fabHelp.setOnClickListener {
            showHelpDialog()
        }
    }

    private fun setupHomeButton() {
        binding.btnHome.setOnClickListener {
            // Dismiss keyboard if open
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)

            // Return directly to the home page
            binding.webView.loadUrl(BASE_URL)
        }
    }

    private fun setupKeyboardDetection() {
        val rootView = binding.root
        val visibleRect = Rect()

        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            rootView.getWindowVisibleDisplayFrame(visibleRect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - visibleRect.bottom

            // Threshold: if keyboard takes more than 15% of screen height, it is active
            val isKeyboardNowOpen = keypadHeight > screenHeight * 0.15

            if (isKeyboardNowOpen != isKeyboardActive) {
                isKeyboardActive = isKeyboardNowOpen
                if (isKeyboardActive) {
                    wipeButtonsDown()
                } else {
                    wipeButtonsUp()
                }
            }
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    private fun wipeButtonsDown() {
        // Wipe down Hint FAB
        if (binding.fabHint.visibility == View.VISIBLE) {
            binding.fabHint.animate().cancel()
            binding.fabHint.animate()
                .translationY(binding.fabHint.height.toFloat() + 250f)
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    binding.fabHint.visibility = View.GONE
                }
                .start()
        }

        // Wipe down Help FAB
        if (binding.fabHelp.visibility == View.VISIBLE) {
            binding.fabHelp.animate().cancel()
            binding.fabHelp.animate()
                .translationY(binding.fabHelp.height.toFloat() + 250f)
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    binding.fabHelp.visibility = View.GONE
                }
                .start()
        }
    }

    private fun wipeButtonsUp() {
        // Wipe up Hint FAB (available on home and puzzle pages)
        binding.fabHint.animate().cancel()
        binding.fabHint.visibility = View.VISIBLE
        binding.fabHint.translationY = binding.fabHint.height.toFloat() + 250f
        binding.fabHint.alpha = 0f
        binding.fabHint.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(260)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Wipe up Help FAB ONLY if on home page
        if (isOnHomePage) {
            binding.fabHelp.animate().cancel()
            binding.fabHelp.visibility = View.VISIBLE
            binding.fabHelp.translationY = binding.fabHelp.height.toFloat() + 250f
            binding.fabHelp.alpha = 0f
            binding.fabHelp.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            binding.fabHelp.visibility = View.GONE
        }
    }

    private fun updateNavigationState(isHome: Boolean, url: String?) {
        isOnHomePage = isHome

        // Home button: Only visible when inside a puzzle or other subpages, hidden on home page
        binding.btnHome.visibility = if (isHome) View.GONE else View.VISIBLE

        // Help button: Only visible on home page and when keyboard is NOT active
        if (isHome && !isKeyboardActive) {
            binding.fabHelp.visibility = View.VISIBLE
            binding.fabHelp.alpha = 1f
            binding.fabHelp.translationY = 0f
        } else {
            binding.fabHelp.visibility = View.GONE
        }
    }

    private fun checkIfHomeUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return true
        val uri = Uri.parse(url)
        val path = uri.path ?: ""
        return path.isEmpty() || path == "/" || path == "/index.html" || path == "/beta" || path == "/beta/"
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

        webView.addJavascriptInterface(
            WebAppInterface(this) { isHome, newUrl ->
                runOnUiThread {
                    updateNavigationState(isHome, newUrl)
                }
            },
            "AndroidApp"
        )

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
                    showGoogleAuthNotice(Uri.parse(targetUrl))
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

                // If Google Sign-In is triggered, show notice explaining Card #2
                if (isGoogleAuthUrl(url)) {
                    showGoogleAuthNotice(url)
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
                updateNavigationState(checkIfHomeUrl(url), url)
                binding.errorView.visibility = View.GONE

                binding.webView.animate()
                    .alpha(0.35f)
                    .translationY(16f)
                    .setDuration(120)
                    .start()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateNavigationState(checkIfHomeUrl(url), url)
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

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                updateNavigationState(checkIfHomeUrl(url), url)
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
     * In-app Google Sign-In notice explaining why direct Google login is blocked in web wrappers
     * and offering to view Help Cards or open browser.
     */
    private fun showGoogleAuthNotice(url: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Sign-In with Google")
            .setMessage("Direct 1-tap Google Sign-In is blocked inside mobile web wrappers by Google's security policy.\n\nRecommended: Log in or sign up using your Email & Password directly to stay permanently signed in on this app!")
            .setPositiveButton("How to Log In") { _, _ -> showHelpDialog() }
            .setNegativeButton("Open Browser Anyway") { _, _ ->
                try {
                    val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
                    customTabsIntent.launchUrl(this, url)
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                }
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun injectMinimalistStyling(view: WebView?) {
        val js = """
            (function() {
                var styleId = 'zen-app-styling-v4';
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

                function checkAndNotifyNav() {
                    var path = window.location.pathname || '';
                    var isHome = (path === '/' || path === '' || path === '/index.html' || path === '/beta' || path === '/beta/');
                    if (window.AndroidApp && window.AndroidApp.onNavigationStateChanged) {
                        window.AndroidApp.onNavigationStateChanged(isHome, window.location.href);
                    }
                }

                if (!window._navMonitorInstalled) {
                    window._navMonitorInstalled = true;
                    var origPush = history.pushState;
                    history.pushState = function() {
                        var res = origPush.apply(this, arguments);
                        checkAndNotifyNav();
                        return res;
                    };

                    var origReplace = history.replaceState;
                    history.replaceState = function() {
                        var res = origReplace.apply(this, arguments);
                        checkAndNotifyNav();
                        return res;
                    };

                    window.addEventListener('popstate', checkAndNotifyNav);
                    window.addEventListener('hashchange', checkAndNotifyNav);
                    setInterval(checkAndNotifyNav, 1000);
                }
                checkAndNotifyNav();
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
        globalLayoutListener?.let {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        binding.webView.stopLoading()
        binding.webView.destroy()
        super.onDestroy()
    }
}
