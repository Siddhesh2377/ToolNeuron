// File: MicroBrowserView.kt
package com.dark.neuroverse.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dark.neuroverse.ui.theme.rDP
import java.io.File
import java.io.FileOutputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MicroBrowserView(
    modifier: Modifier = Modifier, htmlContent: String? = null, url: String? = null
) {
    val context = LocalContext.current

    var webView: WebView? by remember { mutableStateOf(null) }
    var loadingProgress by remember { mutableStateOf(0) }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webView = this

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true

                    // Add these critical settings:
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true

                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT

                    // Add these for better compatibility:
                    mediaPlaybackRequiresUserGesture = false
                    javaScriptCanOpenWindowsAutomatically = true
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        // Log all requests to see what's failing
                        Log.d("WebViewRequest", "URL: ${request?.url}")
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        super.onReceivedError(view, request, error)
                        Log.e("WebViewError", "Error CODE: ${error.errorCode}")
                        Log.e("WebViewError", "Failed URL: ${request.url}")
                        Log.e("WebViewError", "Is main frame: ${request.isForMainFrame}")

                        if (request.isForMainFrame) {
                            view.loadDataWithBaseURL(
                                "https://appassets.androidplatform.net/assets/",
                                getWebViewErrorPage(error.errorCode, error.description.toString()),
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean = false
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        loadingProgress = newProgress
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("WebViewConsole", consoleMessage?.message() ?: "")
                        return true
                    }

                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }

                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        result?.confirm()
                        return true
                    }

                    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                        result?.confirm(defaultValue)
                        return true
                    }
                }

                when {
                    url != null -> loadUrl(url)
                    htmlContent != null -> {
                        loadDataWithBaseURL(
                            "https://appassets.androidplatform.net/assets/", // Special Android base URL
                            htmlContent,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                }

                setDownloadListener { downloadUrl, _, _, _, _ ->
                    Log.d("WebViewDownload", "Download requested: $downloadUrl")
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                RoundedCornerShape(rDP(12.dp))
            )
    )

    if (loadingProgress in 1..99) {
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
        )
    }
}


fun getWebViewErrorPage(errorCode: Int, description: String): String {
    return """
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { 
                    display: flex; 
                    flex-direction: column; 
                    justify-content: center; 
                    align-items: center; 
                    height: 100vh; 
                    margin: 0; 
                    font-family: sans-serif; 
                    background-color: #f2f2f2; 
                    color: #333; 
                }
                h1 { font-size: 24px; margin-bottom: 10px; }
                p { font-size: 16px; margin-bottom: 20px; text-align: center; }
                button { 
                    padding: 10px 20px; 
                    font-size: 16px; 
                    background-color: #6200ee; 
                    color: #fff; 
                    border: none; 
                    border-radius: 5px; 
                }
                button:hover { background-color: #3700b3; cursor: pointer; }
            </style>
        </head>
        <body>
            <h1>Oops! Something went wrong.</h1>
            <p>Page failed to load.<br>Error code: $errorCode<br>$description</p>
            <button onclick="window.location.reload();">Retry</button>
        </body>
        </html>
    """.trimIndent()
}
