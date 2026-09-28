package com.warehouse.sharebridge

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class MainActivity : AppCompatActivity() {

    private val localUrl = "file:///android_asset/index.html"
    private lateinit var webView: WebView
    private var pendingIntent: Intent? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        private const val FILE_CHOOSER_REQUEST_CODE = 51426
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val LOGIN_KEY_ALIAS = "warehouse_login_key_v1"
        private const val LOGIN_PREFS = "warehouse_login_credentials"
        private const val USERNAME_CIPHER = "username"
        private const val PASSWORD_CIPHER = "password"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        // ВАЖНО: Разрешаем доступ к камере внутри WebView
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            // Без этого клик по <input type="file"> в WebView ничего не делает —
            // системный выбор файла/галереи не открывается вообще.
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val chooserIntent = fileChooserParams?.createIntent()
                    ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                return try {
                    startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST_CODE)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Если APK стартует со страницы входа, не теряем файл,
                // которым поделились: warehouse.html загрузится после авторизации.
                if (pendingIntent != null && url?.endsWith("/warehouse.html") == true) {
                    pendingIntent?.let { handleShareIntent(it) }
                    pendingIntent = null
                }
            }
        }

        webView.addJavascriptInterface(WebAppInterface(this), "Android")
        webView.loadUrl(localUrl)

        startPythonServer()

        if (isShareIntent(intent)) {
            pendingIntent = intent
        }
    }

    override fun onDestroy() {
        stopPythonServer()
        super.onDestroy()
    }

    private fun sendTermuxCommand(scriptPath: String) {
        try {
            val intent = Intent()
            intent.setClassName("com.termux", "com.termux.app.RunCommandService")
            intent.action = "com.termux.RUN_COMMAND"
            intent.putExtra("com.termux.RUN_COMMAND_PATH", scriptPath)
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPythonServer() {
        sendTermuxCommand("/data/data/com.termux/files/home/start-server.sh")
    }

    private fun stopPythonServer() {
        sendTermuxCommand("/data/data/com.termux/files/home/stop-server.sh")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isShareIntent(intent)) {
            pendingIntent = intent
            if (webView.url?.endsWith("/warehouse.html") == true) {
                handleShareIntent(intent)
                pendingIntent = null
            }
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
                    val obj = JSONObject().apply {
                        put("name", name)
                        put("type", type)
                        put("base64", base64)
                    }
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

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val results = if (resultCode == RESULT_OK && data != null) {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            } else null
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // Возвращаем фокус на WebView при возврате в приложение — иначе
    // Bluetooth/HID-сканер может перестать присылать нажатия клавиш.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) webView.requestFocus(View.FOCUS_DOWN)
    }

    // Мост для warehouse.html: window.Android.saveFile(...) и
    // window.Android.printHtml(...), которые сейчас никуда не вызываются
    // без этого объекта.
    private inner class WebAppInterface(private val context: Context) {
        private val loginPrefs by lazy {
            context.getSharedPreferences(LOGIN_PREFS, Context.MODE_PRIVATE)
        }

        private fun loginKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val existing = keyStore.getKey(LOGIN_KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    LOGIN_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            return generator.generateKey()
        }

        private fun encrypt(value: String): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, loginKey())
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(cipher.iv.size + encrypted.size)
            cipher.iv.copyInto(combined, 0)
            encrypted.copyInto(combined, cipher.iv.size)
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }

        private fun decrypt(value: String): String {
            val combined = Base64.decode(value, Base64.NO_WRAP)
            require(combined.size > 12) { "Некорректные данные входа" }
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, loginKey(), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }

        private fun readCredentials(): Pair<String, String>? {
            val encryptedUsername = loginPrefs.getString(USERNAME_CIPHER, null)
            val encryptedPassword = loginPrefs.getString(PASSWORD_CIPHER, null)
            if (encryptedUsername.isNullOrBlank() || encryptedPassword.isNullOrBlank()) return null
            return try {
                val username = decrypt(encryptedUsername)
                val password = decrypt(encryptedPassword)
                if (username.isBlank() || password.isBlank()) null else username to password
            } catch (error: Exception) {
                loginPrefs.edit().clear().apply()
                null
            }
        }

        private fun evaluateJavascript(script: String) {
            runOnUiThread { webView.evaluateJavascript(script, null) }
        }

        private fun notifyBiometricError(message: String) {
            evaluateJavascript(
                "window.onNativeBiometricError && " +
                    "window.onNativeBiometricError(${JSONObject.quote(message)});"
            )
        }

        @JavascriptInterface
        fun hasSavedCredentials(): Boolean = readCredentials() != null

        @JavascriptInterface
        fun isBiometricAvailable(): Boolean {
            return BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }

        @JavascriptInterface
        fun saveCredentials(username: String?, password: String?) {
            val cleanUsername = username?.trim().orEmpty()
            val cleanPassword = password.orEmpty()
            if (cleanUsername.isBlank() || cleanPassword.isBlank()) return
            try {
                loginPrefs.edit()
                    .putString(USERNAME_CIPHER, encrypt(cleanUsername))
                    .putString(PASSWORD_CIPHER, encrypt(cleanPassword))
                    .apply()
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }

        @JavascriptInterface
        fun clearCredentials() {
            loginPrefs.edit().clear().apply()
        }

        @JavascriptInterface
        fun loginWithBiometric() {
            runOnUiThread {
                val credentials = readCredentials()
                if (credentials == null) {
                    notifyBiometricError("Сохранённые данные входа не найдены.")
                    return@runOnUiThread
                }
                val biometricManager = BiometricManager.from(context)
                val availability = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
                    notifyBiometricError(
                        "На устройстве не настроен отпечаток или Face Unlock."
                    )
                    return@runOnUiThread
                }

                val executor = ContextCompat.getMainExecutor(context)
                val prompt = BiometricPrompt(
                    this@MainActivity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            super.onAuthenticationSucceeded(result)
                            val username = JSONObject.quote(credentials.first)
                            val password = JSONObject.quote(credentials.second)
                            evaluateJavascript(
                                "window.onNativeBiometricCredentials && " +
                                    "window.onNativeBiometricCredentials($username,$password);"
                            )
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence
                        ) {
                            super.onAuthenticationError(errorCode, errString)
                            notifyBiometricError(errString.toString())
                        }
                    }
                )
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Вход в Warehouse")
                    .setSubtitle("Подтвердите личность отпечатком или лицом")
                    .setNegativeButtonText("Отмена")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )
                    .build()
                try {
                    prompt.authenticate(promptInfo)
                } catch (error: Exception) {
                    notifyBiometricError("Не удалось запустить биометрическую проверку.")
                }
            }
        }

        @JavascriptInterface
        fun saveFile(base64: String, fileName: String, mimeType: String) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    File(dir, fileName).writeBytes(bytes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JavascriptInterface
        fun printHtml(html: String) {
            runOnUiThread {
                val printWebView = WebView(context)
                printWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val adapter = view?.createPrintDocumentAdapter("Warehouse") ?: return
                        printManager.print("Warehouse Document", adapter, PrintAttributes.Builder().build())
                    }
                }
                printWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    }
}
