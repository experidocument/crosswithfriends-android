package com.crosswithfriends.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.View
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

        setSupportActionBar(binding.toolbar)

        setupFileChooser()
        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()
        setupErrorHandling()

        handleIntentUrl(intent)
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
        } else if (binding.webView.url.isNullOrEmpty()) {
            binding.webView.loadUrl(BASE_URL)
        }
    }

    private fun isAllowedHost(host: String?): Boolean {
        if (host == null) return false
        return ALLOWED_HOSTS.any { allowed -> host.equals(allowed, ignoreCase = true) || host.endsWith(".$allowed") }
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

        // Enable JavaScript and HTML5 local storage (essential for game state & Socket.IO)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Responsive viewport settings
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // Media and audio (for puzzle victory sounds/chimes)
        settings.mediaPlaybackRequiresUserGesture = false

        // Security settings
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // Modify User-Agent: strip WebView tags (; wv) so Google OAuth and responsive layouts work seamlessly
        val defaultUserAgent = settings.userAgentString
        val sanitizedUserAgent = defaultUserAgent
            .replace("; wv", "")
            .replace(Regex("Version/\\d+\\.\\d+\\s?"), "")
        settings.userAgentString = sanitizedUserAgent

        // Enable Cookies (including 3rd party for auth sessions)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // JavaScript interface bridge
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidApp")

        // Download listener for puzzle export (.puz, .cpf)
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
                // Fallback: open in browser
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        })

        // WebChromeClient for loading progress, file chooser, and title
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

        // WebViewClient to intercept URLs, handle errors, and manage internal vs external routing
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val host = url.host

                // If internal URL, load within the WebView
                if (isAllowedHost(host)) {
                    return false
                }

                // If authentication flow (e.g., Google OAuth) or external link, open in Chrome Custom Tab
                if (url.scheme == "https" || url.scheme == "http") {
                    try {
                        val customTabsIntent = CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
                        customTabsIntent.launchUrl(this@MainActivity, url)
                        return true
                    } catch (e: Exception) {
                        // Fallback to standard browser intent
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                        return true
                    }
                }

                // Custom schemes (mailto, tel, intent, etc.)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, url)
                    startActivity(intent)
                    return true
                } catch (e: Exception) {
                    return true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.errorView.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefreshLayout.isRefreshing = false
                binding.initialSpinner.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Only show offline screen if the main page failed to load
                if (request?.isForMainFrame == true && !isNetworkAvailable()) {
                    binding.errorView.visibility = View.VISIBLE
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.accent,
            R.color.primary_dark
        )

        // Only allow swipe-to-refresh if the user is at the top of the webpage
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
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                shareCurrentUrl()
                true
            }
            R.id.action_refresh -> {
                binding.webView.reload()
                true
            }
            R.id.action_home -> {
                binding.webView.loadUrl(BASE_URL)
                true
            }
            R.id.action_open_browser -> {
                val currentUrl = binding.webView.url ?: BASE_URL
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                true
            }
            R.id.action_clear_cache -> {
                binding.webView.clearCache(true)
                Toast.makeText(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                binding.webView.reload()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareCurrentUrl() {
        val currentUrl = binding.webView.url ?: BASE_URL
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Join my crossword puzzle room: $currentUrl")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.share_chooser_title))
        startActivity(shareIntent)
    }

    override fun onDestroy() {
        binding.webView.stopLoading()
        binding.webView.destroy()
        super.onDestroy()
    }
}
