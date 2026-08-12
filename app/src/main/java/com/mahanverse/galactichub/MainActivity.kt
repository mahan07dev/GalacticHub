package com.mahanverse.galactichub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipFile
import androidx.core.view.isVisible
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "galactic_prefs"
        private const val KEY_PASSKEY = "passkey"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PLAYER = "player_type"
        private const val KEY_LAST_PLAYED = "last_played_id"
        private const val KEY_USE_EXTERNAL_OBB = "use_external_obb"
        private const val KEY_EXTERNAL_OBB_PATH = "external_obb_path"
        private const val DEFAULT_PASSKEY = "THE BOYS"
    }

    private var isHubOpen = false
    private lateinit var webView: WebView
    private lateinit var loadingContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var prefs: SharedPreferences

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var videoListJson: String = "[]"

    // Home screen views
    private lateinit var homeRoot: View
    private lateinit var hubButton: Button
    private lateinit var settingsButton: Button
    private lateinit var guideButton: Button

    // File pickers
    private val importMetaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importMetadata(it) }
    }
    private val exportMetaLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportMetadata(it) }
    }
    private val pickObbLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleExternalObbPicked(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        supportActionBar?.hide()
        hideSystemUI()

        // Check passkey
        if (!prefs.contains(KEY_PASSKEY)) {
            prefs.edit { putString(KEY_PASSKEY, DEFAULT_PASSKEY) }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (isHubOpen && ::webView.isInitialized) {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    showHomeScreen()
                }
            } else {
                finish()
            }
        }

        // Always ask for passkey on launch (simple authentication)
        showPasskeyDialog()
    }

    private fun showPasskeyDialog() {
        val savedPass = prefs.getString(KEY_PASSKEY, DEFAULT_PASSKEY) ?: DEFAULT_PASSKEY
        val input = EditText(this)
        input.hint = getString(R.string.password_hint)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.layoutDirection = View.LAYOUT_DIRECTION_LTR
        input.textDirection = View.TEXT_DIRECTION_LTR
        input.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        input.gravity = Gravity.START

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.auth_title))
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                if (input.text.toString().trim() == savedPass) {
                    showHomeScreen()
                } else {
                    Toast.makeText(this, getString(R.string.wrong_password), Toast.LENGTH_SHORT).show()
                    showPasskeyDialog()
                }
            }
            .setNegativeButton(getString(R.string.exit)) { _, _ -> finish() }
            .create()
        dialog.window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_RTL
        dialog.show()
    }

    private fun showHomeScreen() {
        setContentView(R.layout.activity_home)
        homeRoot = findViewById(R.id.homeRoot)
        hubButton = findViewById(R.id.hubButton)
        settingsButton = findViewById(R.id.settingsButton)
        guideButton = findViewById(R.id.guideButton)

        hubButton.setOnClickListener { openWebviewHub() }
        settingsButton.setOnClickListener { showSettingsDialog() }
        guideButton.setOnClickListener { showObbGuideDialog() }
        isHubOpen = false
    }

    private fun openWebviewHub() {
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        loadingContainer = findViewById(R.id.loadingContainer)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(VideoPlayerInterface(this), "VideoPlayer")
        webView.addJavascriptInterface(AndroidInterface(), "Android")

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            allowContentAccess = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = false
        }

        mainScope.launch {
            videoListJson = scanObbAndGenerateJson { progress, total ->
                val pct = if (total > 0) (progress * 100 / total) else 0
                progressBar.progress = pct
                progressText.text = "$pct%"
            }
            loadingContainer.visibility = View.GONE
            webView.loadUrl("file:///android_asset/index.html")
            isHubOpen = true
        }
    }

    // ==================== SETTINGS DIALOG ====================
    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.settings_title))

        val scrollView = ScrollView(this)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        scrollView.addView(view)
        builder.setView(scrollView)

        // UI references
        val themeSpinner = view.findViewById<Spinner>(R.id.themeSpinner)
        val playerSpinner = view.findViewById<Spinner>(R.id.playerSpinner)
        val passwordEditText = view.findViewById<EditText>(R.id.passwordEditText)
        val changePasswordBtn = view.findViewById<Button>(R.id.changePasswordBtn)
        val exportMetaBtn = view.findViewById<Button>(R.id.exportMetaBtn)
        val importMetaBtn = view.findViewById<Button>(R.id.importMetaBtn)
        val pickObbBtn = view.findViewById<Button>(R.id.pickObbBtn)
        val resetObbBtn = view.findViewById<Button>(R.id.resetObbBtn)
        val obbStatusText = view.findViewById<TextView>(R.id.obbStatusText)

        // Theme
        val themes = arrayOf(getString(R.string.theme_dark), getString(R.string.theme_economy))
        themeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val currentTheme = prefs.getString(KEY_THEME, "dark") ?: "dark"
        themeSpinner.setSelection(if (currentTheme == "economy") 1 else 0)

        // Player
        val players = arrayOf(getString(R.string.player_internal), getString(R.string.player_external))
        playerSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, players).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val currentPlayer = prefs.getString(KEY_PLAYER, "internal") ?: "internal"
        playerSpinner.setSelection(if (currentPlayer == "external") 1 else 0)

        // Password
        val currentPass = prefs.getString(KEY_PASSKEY, DEFAULT_PASSKEY) ?: DEFAULT_PASSKEY
        passwordEditText.setText(currentPass)
        changePasswordBtn.setOnClickListener {
            val newPass = passwordEditText.text.toString().trim()
            if (newPass.isEmpty()) {
                Toast.makeText(this, getString(R.string.password_empty), Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit { putString(KEY_PASSKEY, newPass) }
                Toast.makeText(this, getString(R.string.password_changed), Toast.LENGTH_SHORT).show()
            }
        }

        // External OBB status
        val usingExternal = prefs.getBoolean(KEY_USE_EXTERNAL_OBB, false)
        if (usingExternal) {
            val path = prefs.getString(KEY_EXTERNAL_OBB_PATH, "")
            obbStatusText.text = getString(R.string.external_obb_active, path ?: "")
            pickObbBtn.isEnabled = false
            resetObbBtn.isEnabled = true
        } else {
            obbStatusText.text = getString(R.string.using_default_obb)
            pickObbBtn.isEnabled = true
            resetObbBtn.isEnabled = false
        }

        pickObbBtn.setOnClickListener {
            pickObbLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }

        resetObbBtn.setOnClickListener {
            val cachedFile = File(cacheDir, "external.obb")
            cachedFile.delete()
            prefs.edit {
                putBoolean(KEY_USE_EXTERNAL_OBB, false)
                    .remove(KEY_EXTERNAL_OBB_PATH)
            }
            obbStatusText.text = getString(R.string.using_default_obb)
            pickObbBtn.isEnabled = true
            resetObbBtn.isEnabled = false
            Toast.makeText(this, getString(R.string.obb_reset), Toast.LENGTH_SHORT).show()
        }

        // Metadata import/export
        exportMetaBtn.setOnClickListener { exportMetaLauncher.launch("metadata.json") }
        importMetaBtn.setOnClickListener { importMetaLauncher.launch("application/json") }

        builder.setPositiveButton(getString(R.string.save)) { _, _ ->
            // Save theme and player choices
            val newTheme = if (themeSpinner.selectedItemPosition == 1) "economy" else "dark"
            val newPlayer = if (playerSpinner.selectedItemPosition == 1) "external" else "internal"
            prefs.edit {
                putString(KEY_THEME, newTheme)
                    .putString(KEY_PLAYER, newPlayer)
            }
            if (::webView.isInitialized && webView.isVisible) {
                webView.loadUrl("javascript:applyTheme('$newTheme')")
            }
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton(getString(R.string.cancel), null)
        builder.show()
    }

    private fun handleExternalObbPicked(uri: Uri) {
        try {
            val destFile = File(cacheDir, "external.obb")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            prefs.edit {
                putBoolean(KEY_USE_EXTERNAL_OBB, true)
                    .putString(KEY_EXTERNAL_OBB_PATH, destFile.absolutePath)
            }
            Toast.makeText(this, getString(R.string.external_obb_set), Toast.LENGTH_SHORT).show()
            // Refresh webview if open
            if (::webView.isInitialized && webView.isVisible) {
                refreshVideoList()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.external_obb_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun importMetadata(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val jsonStr = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            val imported = JSONObject(jsonStr)
            val current = loadCustomMetadata()
            val merged = JSONObject()
            current.keys().forEach { key -> merged.put(key, current.get(key)) }
            imported.keys().forEach { key -> merged.put(key, imported.get(key)) }
            saveCustomMetadata(merged)
            Toast.makeText(this, getString(R.string.metadata_imported), Toast.LENGTH_SHORT).show()
            if (::webView.isInitialized && webView.isVisible) {
                refreshVideoList()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.metadata_import_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportMetadata(uri: Uri) {
        try {
            val json = loadCustomMetadata().toString()
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
            Toast.makeText(this, getString(R.string.metadata_exported), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.metadata_export_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshVideoList() {
        mainScope.launch {
            videoListJson = scanObbAndGenerateJson { _, _ -> }
            webView.loadUrl("javascript:refreshData($videoListJson)")
        }
    }

    // ==================== OBB GUIDE ====================
    private fun showObbGuideDialog() {
        val textView = TextView(this).apply {
            text = getString(R.string.obb_guide_text)
            setPadding(32, 32, 32, 32)
            textSize = 16f
            movementMethod = ScrollingMovementMethod()
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.obb_guide_title))
            .setView(textView)
            .setPositiveButton(getString(R.string.got_it), null)
            .show()
    }

    // ==================== OBB SCANNING ====================
    private fun findObbFiles(): List<File> {
        val useExternal = prefs.getBoolean(KEY_USE_EXTERNAL_OBB, false)
        if (useExternal) {
            val path = prefs.getString(KEY_EXTERNAL_OBB_PATH, null)
            if (path != null) {
                val file = File(path)
                if (file.exists()) return listOf(file)
            }
            // Fallback if external file missing
        }

        val obbDir = obbDir
        val files = mutableListOf<File>()
        if (obbDir.exists()) {
            obbDir.listFiles { f -> f.name.endsWith(".obb") }?.let { files.addAll(it) }
        }
        // Try manual main file path
        try {
            val vc = packageManager.getPackageInfo(packageName, 0).versionCode
            val mainFile = File("/storage/emulated/0/Android/obb/$packageName/main.$vc.$packageName.obb")
            if (mainFile.exists() && !files.any { it.absolutePath == mainFile.absolutePath }) {
                files.add(mainFile)
            }
        } catch (_: Exception) {}

        // Sort: main file first (if we can identify it), then others
        val mainFileName = try {
            val vc = packageManager.getPackageInfo(packageName, 0).versionCode
            "main.$vc.$packageName.obb"
        } catch (e: Exception) { "" }
        files.sortBy { file ->
            if (file.name == mainFileName) 0 else 1
        }
        return files
    }

    private suspend fun scanObbAndGenerateJson(onProgress: (Int, Int) -> Unit): String = withContext(Dispatchers.IO) {
        try {
            val obbFiles = findObbFiles()
            if (obbFiles.isEmpty()) return@withContext "[]"

            val cacheDir = File(cacheDir, "thumbnails").apply { mkdirs() }
            val metadata = loadMetadata()
            val customMetadata = loadCustomMetadata()
            val videoExtensions = setOf("mp4", "webm", "mkv", "avi", "mov")
            val items = JSONArray()
            var idCounter = 1

            // Count total video entries across all OBBs for progress
            var totalEntries = 0
            val obbEntryMap = mutableMapOf<File, List<java.util.zip.ZipEntry>>()
            for (obbFile in obbFiles) {
                try {
                    ZipFile(obbFile).use { zip ->
                        val entries = zip.entries().toList().filter { e ->
                            !e.isDirectory && e.name.substringAfterLast('.', "").lowercase() in videoExtensions
                        }
                        obbEntryMap[obbFile] = entries
                        totalEntries += entries.size
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open OBB: ${obbFile.absolutePath}", e)
                }
            }

            var processed = 0

            for ((obbFile, entries) in obbEntryMap) {
                ZipFile(obbFile).use { zip ->
                    for (entry in entries) {
                        val name = entry.name
                        val baseName = name.substringAfterLast('/').substringBeforeLast('.')
                        val thumbFile = File(cacheDir, "${baseName.hashCode()}.jpg")
                        if (!thumbFile.exists()) {
                            generateThumbnail(zip, entry, thumbFile, 20_000_000)
                        }
                        val custom = customMetadata.optJSONObject(name) ?: JSONObject()
                        val assetMeta = metadata.optJSONObject(name) ?: JSONObject()
                        val item = JSONObject().apply {
                            put("id", idCounter++)
                            put("title", custom.optString("title", assetMeta.optString("title", baseName)))
                            put("category", custom.optString("category", assetMeta.optString("category", "")))
                            put("description", custom.optString("description", assetMeta.optString("description", "")))
                            put("thumbnail", if (thumbFile.exists()) Uri.fromFile(thumbFile).toString() else "")
                            put("link", name)
                            put("size", entry.size)
                        }
                        items.put(item)
                        processed++
                        if (processed % 5 == 0 || processed == totalEntries) {
                            withContext(Dispatchers.Main) { onProgress(processed, totalEntries) }
                        }
                    }
                }
            }
            return@withContext items.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
            return@withContext "[]"
        }
    }

    private fun loadMetadata(): JSONObject {
        return try {
            JSONObject(assets.open("metadata.json").bufferedReader().use { it.readText() })
        } catch (e: Exception) { JSONObject() }
    }

    private fun loadCustomMetadata(): JSONObject {
        val file = File(filesDir, "custom_metadata.json")
        return if (file.exists()) try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() } else JSONObject()
    }

    private fun saveCustomMetadata(json: JSONObject) {
        File(filesDir, "custom_metadata.json").writeText(json.toString())
    }

    private fun generateThumbnail(zip: ZipFile, entry: java.util.zip.ZipEntry, outputFile: File, timeUs: Long) {
        // Phase 1 – fast partial extraction (up to 150 MB)
        if (tryGenerateThumbnail(zip, entry, outputFile, timeUs, extractFull = false)) {
            return
        }
        // Phase 2 – full extraction as fallback
        Log.d(TAG, "Fast method failed for ${entry.name}, trying full extraction")
        tryGenerateThumbnail(zip, entry, outputFile, timeUs, extractFull = true)
    }

    private fun tryGenerateThumbnail(
        zip: ZipFile,
        entry: java.util.zip.ZipEntry,
        outputFile: File,
        timeUs: Long,
        extractFull: Boolean
    ): Boolean {
        var tempFile: File? = null
        try {
            val maxExtract = if (extractFull) entry.size else minOf(entry.size, 150L * 1024 * 1024)
            tempFile = File.createTempFile("thumb", ".mp4", cacheDir)
            zip.getInputStream(entry).use { input ->
                FileOutputStream(tempFile).use { out ->
                    val buf = ByteArray(8192)
                    var remaining = maxExtract
                    while (remaining > 0) {
                        val toRead = minOf(buf.size.toLong(), remaining).toInt()
                        val read = input.read(buf, 0, toRead)
                        if (read == -1) break
                        out.write(buf, 0, read)
                        remaining -= read
                    }
                }
            }

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tempFile.absolutePath)

            var bitmap: Bitmap? = null
            // Try multiple time points / strategies
            bitmap = retriever.getFrameAtTime(20_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap == null) bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap == null) bitmap = retriever.getFrameAtTime(30_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap == null) bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap == null) bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)   // first frame
            if (bitmap == null) bitmap = retriever.getFrameAtTime(5_000_000, MediaMetadataRetriever.OPTION_CLOSEST)
            if (bitmap == null) bitmap = retriever.getFrameAtTime(10_000_000, MediaMetadataRetriever.OPTION_CLOSEST)

            retriever.release()

            if (bitmap != null) {
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
                return true
            }
            return false
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail attempt failed for ${entry.name}", e)
            return false
        } finally {
            tempFile?.delete()
        }
    }

    // ==================== NATIVE INTERFACES ====================
    inner class AndroidInterface {
        @android.webkit.JavascriptInterface
        fun getVideoList(): String = videoListJson

        @android.webkit.JavascriptInterface
        fun getLastPlayedVideoId(): Int = prefs.getInt(KEY_LAST_PLAYED, -1)

        @android.webkit.JavascriptInterface
        fun loadFavorites(): String = prefs.getString("favorites_json", "[]") ?: "[]"

        @android.webkit.JavascriptInterface
        fun saveFavorites(json: String) { prefs.edit { putString("favorites_json", json) } }

        @android.webkit.JavascriptInterface
        fun saveCustomMetadata(id: Int, title: String, category: String, description: String) {
            try {
                val items = JSONArray(videoListJson)
                if (id < 1 || id > items.length()) return
                val entry = items.getJSONObject(id - 1)
                val link = entry.getString("link")
                val currentCustom = loadCustomMetadata()
                val obj = currentCustom.optJSONObject(link) ?: JSONObject()
                obj.put("title", title)
                obj.put("category", category)
                obj.put("description", description)
                currentCustom.put(link, obj)
                saveCustomMetadata(currentCustom)

                Toast.makeText(this@MainActivity, getString(R.string.metadata_saved), Toast.LENGTH_SHORT).show()

                // Return to home and then immediately open the webview again
                Handler(Looper.getMainLooper()).post {
                    showHomeScreen()
                    // Small delay to ensure home screen is set, then open hub
                    Handler(Looper.getMainLooper()).postDelayed({
                        openWebviewHub()
                    }, 200)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, getString(R.string.metadata_save_error), Toast.LENGTH_SHORT).show()
            }
        }

        @android.webkit.JavascriptInterface
        fun getThemeMode(): String = prefs.getString(KEY_THEME, "dark") ?: "dark"
    }

    inner class VideoPlayerInterface(private val activity: MainActivity) {
        private val cacheDir = File(activity.cacheDir, "extracted_videos").apply { mkdirs() }

        @android.webkit.JavascriptInterface
        fun play(videoPath: String) {
            val cleanPath = videoPath.trim().removePrefix("/")
            val cachedFile = File(cacheDir, cleanPath.replace("/", "_"))

            if (cachedFile.exists()) {
                playVideoFromFile(cachedFile)
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val obbFile = findObbFileForPath(cleanPath)
                        ?: throw Exception("OBB not found")
                    ZipFile(obbFile).use { zip ->
                        val entry = zip.getEntry(cleanPath)
                            ?: throw Exception("Entry not found")
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(cachedFile).use { out -> input.copyTo(out) }
                        }
                    }
                    cleanExtractedVideosCache()
                    withContext(Dispatchers.Main) {
                        playVideoFromFile(cachedFile)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, getString(R.string.cannot_play_video), Toast.LENGTH_LONG).show()
                    }
                }
            }

            // Save last played ID
            try {
                val items = JSONArray(videoListJson)
                for (i in 0 until items.length()) {
                    if (items.getJSONObject(i).getString("link") == cleanPath) {
                        prefs.edit { putInt(KEY_LAST_PLAYED, items.getJSONObject(i).getInt("id")) }
                        break
                    }
                }
            } catch (_: Exception) {}
        }

        private fun findObbFileForPath(videoPath: String): File? {
            // Check all available OBB files for the entry
            val obbFiles = findObbFiles()
            for (f in obbFiles) {
                try {
                    ZipFile(f).use { zip ->
                        if (zip.getEntry(videoPath) != null) return f
                    }
                } catch (_: Exception) {}
            }
            return null
        }

        private fun playVideoFromFile(file: File) {
            val playerType = prefs.getString(KEY_PLAYER, "internal") ?: "internal"
            if (playerType == "external") {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    activity, "${activity.packageName}.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(Intent.createChooser(intent, getString(R.string.play_video)))
                } catch (e: Exception) {
                    Toast.makeText(activity, getString(R.string.no_player_found), Toast.LENGTH_SHORT).show()
                }
            } else {
                // Internal player using file path
                val intent = Intent(activity, VideoPlayerActivity::class.java).apply {
                    putExtra("video_uri", "file://${file.absolutePath}")
                }
                startActivity(intent)
            }
        }

        private fun cleanExtractedVideosCache(maxFiles: Int = 3) {
            val files = cacheDir.listFiles() ?: return
            if (files.size <= maxFiles) return
            files.sortByDescending { it.lastModified() }
            files.drop(maxFiles).forEach { it.delete() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // not needed
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }
}