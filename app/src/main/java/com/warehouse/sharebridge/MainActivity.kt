package com.warehouse.sharebridge

import android.content.Intent
import android.net.Uri
import android.os.Base64
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    // TODO: проверьте, что это точный адрес вашего сайта на GitHub Pages
    private val siteUrl = "https://as-web-hh.github.io/warehouse.html"

    private lateinit var webView: WebView
    private var pendingIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pendingIntent?.let { handleShareIntent(it) }
                pendingIntent = null
            }
        }

        webView.loadUrl(siteUrl)

        if (isShareIntent(intent)) {
            pendingIntent = intent
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isShareIntent(intent)) {
            // Страница уже загружена — можно передавать файлы сразу.
            handleShareIntent(intent)
        }
    }

    private fun isShareIntent(intent: Intent?): Boolean {
        val action = intent?.action
        return action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
    }

    private fun handleShareIntent(intent: Intent) {
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) uris.add(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (list != null) uris.addAll(list)
            }
        }
        if (uris.isEmpty()) return

        val jsonFiles = JSONArray()
        for (uri in uris) {
            try {
                val name = queryFileName(uri) ?: "shared-file"
                val type = contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val obj = JSONObject()
                    obj.put("name", name)
                    obj.put("type", type)
                    obj.put("base64", base64)
                    jsonFiles.put(obj)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (jsonFiles.length() == 0) return

        val js = "window.receiveNativeSharedFiles && window.receiveNativeSharedFiles($jsonFiles);"
        webView.evaluateJavascript(js, null)
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = it.getString(idx)
            }
        }
        return name
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
