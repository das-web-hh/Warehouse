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
import android.provider.DocumentsContract
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

private data class DocEntry(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val modified: Long
) {
    val isDir: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
}

class MainActivity : AppCompatActivity() {

    private val localUrl = "file:///android_asset/index.html"
    private lateinit var webView: WebView
    private var pendingIntent: Intent? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingFolderRequestId: String? = null
    private var printWebView: WebView? = null

    companion object {
        private const val FILE_CHOOSER_REQUEST_CODE = 51426
        private const val FOLDER_PICKER_REQUEST_CODE = 51427
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
        if (requestCode == FOLDER_PICKER_REQUEST_CODE) {
            val requestId = pendingFolderRequestId
            pendingFolderRequestId = null
            val uri = if (resultCode == RESULT_OK) data?.data else null
            var name = ""
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
                name = try {
                    rootEntry(uri).name
                } catch (e: Exception) {
                    uri.lastPathSegment?.substringAfterLast(':') ?: "folder"
                }
            }
            if (requestId != null) {
                val js = "window.__onNativeFolderPicked && window.__onNativeFolderPicked(" +
                    "${JSONObject.quote(requestId)}," +
                    "${JSONObject.quote(uri?.toString() ?: "")}," +
                    "${JSONObject.quote(name)});"
                webView.evaluateJavascript(js, null)
            }
            return
        }
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

    // ───── Работа с папками через Storage Access Framework ─────
    private fun splitPath(path: String): List<String> =
        path.split('/').filter { it.isNotEmpty() }

    private fun docUri(tree: Uri, id: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun rootEntry(tree: Uri): DocEntry {
        val id = DocumentsContract.getTreeDocumentId(tree)
        var name = id.substringAfterLast(':').ifBlank { "folder" }
        contentResolver.query(
            docUri(tree, id),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        return DocEntry(id, name, DocumentsContract.Document.MIME_TYPE_DIR, 0, 0)
    }

    private fun listChildren(tree: Uri, parentId: String): List<DocEntry> {
        val out = ArrayList<DocEntry>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                out.add(
                    DocEntry(
                        id,
                        c.getString(1) ?: "",
                        c.getString(2) ?: "",
                        if (c.isNull(3)) 0L else c.getLong(3),
                        if (c.isNull(4)) 0L else c.getLong(4)
                    )
                )
            }
        }
        return out
    }

    private fun resolveEntry(tree: Uri, path: String): DocEntry? {
        val segments = splitPath(path)
        if (segments.isEmpty()) return rootEntry(tree)
        var currentId = DocumentsContract.getTreeDocumentId(tree)
        var entry: DocEntry? = null
        for (segment in segments) {
            entry = listChildren(tree, currentId).firstOrNull { it.name == segment } ?: return null
            currentId = entry.id
        }
        return entry
    }

    private fun ensureDirs(tree: Uri, segments: List<String>): String? {
        var parentId = DocumentsContract.getTreeDocumentId(tree)
        for (segment in segments) {
            val existing = listChildren(tree, parentId).firstOrNull { it.name == segment }
            parentId = when {
                existing == null -> {
                    val created = DocumentsContract.createDocument(
                        contentResolver,
                        docUri(tree, parentId),
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        segment
                    ) ?: return null
                    DocumentsContract.getDocumentId(created)
                }
                existing.isDir -> existing.id
                else -> return null
            }
        }
        return parentId
    }

    private fun ensureFile(tree: Uri, path: String, mime: String): Uri? {
        val segments = splitPath(path)
        if (segments.isEmpty()) return null
        val parentId = ensureDirs(tree, segments.dropLast(1)) ?: return null
        val name = segments.last()
        val existing = listChildren(tree, parentId).firstOrNull { it.name == name }
        if (existing != null) return if (existing.isDir) null else docUri(tree, existing.id)
        val created = DocumentsContract.createDocument(
            contentResolver, docUri(tree, parentId), mime, name
        ) ?: return null
        return docUri(tree, DocumentsContract.getDocumentId(created))
    }

    private fun entryJson(entry: DocEntry): JSONObject = JSONObject().apply {
        put("name", entry.name)
        put("isDir", entry.isDir)
        put("size", entry.size)
        put("modified", entry.modified)
        put("mime", if (entry.isDir) "" else entry.mime)
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

        // ───── Выбор папки и файловые операции для warehouse.html ─────
        @JavascriptInterface
        fun pickFolder(requestId: String) {
            runOnUiThread {
                pendingFolderRequestId = requestId
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    )
                }
                try {
                    startActivityForResult(intent, FOLDER_PICKER_REQUEST_CODE)
                } catch (e: Exception) {
                    pendingFolderRequestId = null
                    evaluateJavascript(
                        "window.__onNativeFolderPicked && window.__onNativeFolderPicked(" +
                            "${JSONObject.quote(requestId)},'','');"
                    )
                }
            }
        }

        @JavascriptInterface
        fun hasFolderPermission(treeUri: String, write: Boolean): Boolean {
            return try {
                val uri = Uri.parse(treeUri)
                context.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission && (!write || it.isWritePermission)
                }
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun listDir(treeUri: String, path: String): String? {
            return try {
                val tree = Uri.parse(treeUri)
                val dir = resolveEntry(tree, path)
                if (dir == null || !dir.isDir) return null
                val array = JSONArray()
                listChildren(tree, dir.id).forEach { array.put(entryJson(it)) }
                array.toString()
            } catch (e: Exception) {
                null
            }
        }

        @JavascriptInterface
        fun statEntry(treeUri: String, path: String): String? {
            return try {
                resolveEntry(Uri.parse(treeUri), path)?.let { entryJson(it).toString() }
            } catch (e: Exception) {
                null
            }
        }

        @JavascriptInterface
        fun readChunk(treeUri: String, path: String, offset: Double, length: Int): String? {
            return try {
                val tree = Uri.parse(treeUri)
                val entry = resolveEntry(tree, path)
                if (entry == null || entry.isDir) return null
                context.contentResolver.openInputStream(docUri(tree, entry.id))?.use { input ->
                    val target = offset.toLong()
                    var skipped = 0L
                    while (skipped < target) {
                        val n = input.skip(target - skipped)
                        if (n <= 0) break
                        skipped += n
                    }
                    val buffer = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = input.read(buffer, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                }
            } catch (e: Exception) {
                null
            }
        }

        @JavascriptInterface
        fun writeChunk(
            treeUri: String,
            path: String,
            base64: String,
            append: Boolean,
            mime: String
        ): Boolean {
            return try {
                val tree = Uri.parse(treeUri)
                val uri = ensureFile(tree, path, mime.ifBlank { "application/octet-stream" })
                    ?: return false
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val stream = context.contentResolver.openOutputStream(uri, if (append) "wa" else "wt")
                    ?: return false
                stream.use { it.write(bytes) }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        @JavascriptInterface
        fun createFile(treeUri: String, path: String, mime: String): Boolean {
            return try {
                ensureFile(Uri.parse(treeUri), path, mime.ifBlank { "application/octet-stream" }) != null
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun mkdirs(treeUri: String, path: String): Boolean {
            return try {
                ensureDirs(Uri.parse(treeUri), splitPath(path)) != null
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun deleteEntry(treeUri: String, path: String): Boolean {
            return try {
                val tree = Uri.parse(treeUri)
                val entry = resolveEntry(tree, path) ?: return false
                DocumentsContract.deleteDocument(context.contentResolver, docUri(tree, entry.id))
            } catch (e: Exception) {
                false
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
                val wv = WebView(this@MainActivity)
                printWebView = wv // держим ссылку, иначе WebView может быть собран GC до конца печати
                wv.settings.javaScriptEnabled = false
                wv.webViewClient = object : WebViewClient() {
                    private var started = false
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (started || view == null) return
                        started = true
                        val printManager =
                            getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val adapter = view.createPrintDocumentAdapter("Warehouse")
                        printManager.print(
                            "Warehouse Document",
                            adapter,
                            PrintAttributes.Builder().build()
                        )
                    }
                }
                wv.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
            }
        }
    }
}
