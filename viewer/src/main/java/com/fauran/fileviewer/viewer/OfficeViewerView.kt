package com.fauran.fileviewer.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.AttributeSet
import android.view.ActionMode
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** The document finished loading; [pages] is page/slide/sheet count, [index] is 1-based. */
fun interface OnLoadListener {
    fun onLoaded(format: DocumentFormat, pages: Int, index: Int)
}

fun interface OnPageListener {
    fun onPageChanged(index: Int)
}

fun interface OnErrorListener {
    fun onError(message: String)
}

/** Diagnostics from the JS layer; MIUI-style ROMs swallow logcat, so callers can surface these on screen. */
fun interface OnDebugListener {
    fun onDebug(message: String)
}

/** Current DOM text selection; null when nothing is selected. */
fun interface OnSelectionListener {
    fun onSelectionChanged(text: String?)
}

/**
 * Host-supplied clipboard handler. When set, `copySelection()` routes through it
 * instead of the system clipboard — lets security-sensitive apps keep copied
 * document text inside the process.
 */
fun interface ClipboardSink {
    fun copyText(text: String)
}

/**
 * Offline viewer for OOXML documents (docx/xlsx/pptx), rendering
 * office-open-xml-viewer (@silurus/ooxml, MIT) inside a WebView.
 * All assets are bundled; no network access is performed.
 */
class OfficeViewerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val main = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    // Each view instance serves documents from its own tokenised cache dir.
    private val token = java.util.UUID.randomUUID().toString()
    private val docFile = File(File(context.cacheDir, "officeviewer"), token).let {
        it.mkdirs()
        File(it, "file")
    }

    private val assetHandler = FileAssetHandler(context)

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", assetHandler)
        .addPathHandler("/document/", DocumentPathHandler(docFile))
        .build()

    private val webView = object : WebView(context) {
        override fun startActionMode(
            callback: android.view.ActionMode.Callback?,
            type: Int
        ): android.view.ActionMode? {
            // Security: Chromium's floating text-selection toolbar (Copy/Share/Web Search)
            // is rebuilt with viewer-owned actions only — see SecureSelectionCallback.
            if (type == android.view.ActionMode.TYPE_FLOATING && callback != null) {
                return super.startActionMode(SecureSelectionCallback(callback), type)
            }
            return super.startActionMode(callback, type)
        }
    }

    var onLoadListener: OnLoadListener? = null
    var onPageListener: OnPageListener? = null
    var onErrorListener: OnErrorListener? = null
    var onDebugListener: OnDebugListener? = null
    var onSelectionListener: OnSelectionListener? = null

    /** When set, copies bypass the system clipboard entirely. */
    var clipboardSink: ClipboardSink? = null

    init {
        // Remote debugging (chrome://inspect) on debuggable builds — essential for
        // diagnosing the JS layer on devices where logcat is unreliable.
        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.setBackgroundColor(0xFFFFFFFF.toInt())
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String) {
                android.util.Log.i(TAG, "page finished: $url")
            }

            override fun onReceivedError(
                view: WebView,
                request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (request.isForMainFrame) {
                    android.util.Log.w(TAG, "main frame error ${request.url}: ${error.description}")
                    postError("Failed to load viewer page: ${error.description}")
                } else {
                    android.util.Log.w(TAG, "resource error ${request.url}: ${error.description}")
                }
            }
        }
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.i(
                    TAG,
                    "console[${message.messageLevel()}] ${message.sourceId()}:${message.lineNumber()}: ${message.message()}"
                )
                return true
            }
        }
        webView.addJavascriptInterface(Bridge(), "OfficeBridge")
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Loads a document from a content:// or file:// Uri (e.g. from ACTION_OPEN_DOCUMENT). */
    fun load(uri: Uri) {
        io.execute {
            val result = runCatching {
                val format = DocumentFormat.fromFileName(queryDisplayName(uri))
                    ?: DocumentFormat.fromMimeType(context.contentResolver.getType(uri))
                    ?: throw IllegalArgumentException(
                        "Unsupported document type (need .docx, .xlsx or .pptx): ${
                            queryDisplayName(
                                uri
                            ) ?: uri
                        }"
                    )
                context.contentResolver.openInputStream(uri)?.use { input ->
                    docFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("Cannot open $uri")
                format to OoxmlAssets.ensureExtracted(context)
            }
            main.post {
                result.fold(
                    onSuccess = { (format, rendererRoot) ->
                        assetHandler.rendererRoot = rendererRoot
                        openViewer(format)
                    },
                    onFailure = { onErrorListener?.onError("Failed to open document: ${it.message}") }
                )
            }
        }
    }

    /** Loads a document from a local file. */
    fun load(file: File) {
        io.execute {
            val result = runCatching {
                val format = DocumentFormat.fromFileName(file.name)
                    ?: throw IllegalArgumentException("Unsupported document type (need .docx, .xlsx or .pptx): ${file.name}")
                file.copyTo(docFile, overwrite = true)
                format to OoxmlAssets.ensureExtracted(context)
            }
            main.post {
                result.fold(
                    onSuccess = { (format, rendererRoot) ->
                        assetHandler.rendererRoot = rendererRoot
                        openViewer(format)
                    },
                    onFailure = { onErrorListener?.onError("Failed to open document: ${it.message}") }
                )
            }
        }
    }

    fun nextPage() = dispatch("next")
    fun previousPage() = dispatch("prev")
    fun zoomIn() = dispatch("zoomIn")
    fun zoomOut() = dispatch("zoomOut")
    fun fitPage() = dispatch("fitPage")
    fun fitWidth() = dispatch("fitWidth")

    /** Copies the current selection (text layer for docx/pptx, cells for xlsx) to the system clipboard. */
    fun copySelection() = dispatch("copySelection")

    /** Selects the whole document text layer (docx/pptx; no-op for xlsx). */
    fun selectAll() = dispatch("selectAll")

    /** Current document format, so the selection menu can hide inapplicable actions. */
    internal var currentFormat: DocumentFormat? = null

    /**
     * Rebuilds the floating selection toolbar with viewer-owned Copy / Select All.
     * The delegate is kept alive so Chromium's internal selection wiring keeps working;
     * the system's Share / Web Search items never reach the menu.
     */
    private inner class SecureSelectionCallback(
        private val delegate: android.view.ActionMode.Callback
    ) : ActionMode.Callback2() {

        override fun onGetContentRect(
            mode: ActionMode?,
            view: View?,
            outRect: Rect?
        ) {
            if (delegate is ActionMode.Callback2) {
                delegate.onGetContentRect(mode, view, outRect)
            } else {
                super.onGetContentRect(mode, view, outRect)
            }
        }

        override fun onCreateActionMode(
            mode: ActionMode,
            menu: android.view.Menu
        ): Boolean {
            delegate.onCreateActionMode(mode, menu)
            rebuildMenu(menu)
            return true
        }

        override fun onPrepareActionMode(
            mode: ActionMode,
            menu: android.view.Menu
        ): Boolean {
            delegate.onPrepareActionMode(mode, menu)
            rebuildMenu(menu)
            return true
        }

        override fun onActionItemClicked(
            mode: ActionMode,
            item: android.view.MenuItem
        ): Boolean =
            when (item.itemId) {
                R.id.action_copy -> {
                    copySelection()
                    mode.finish()
                    true
                }

                R.id.action_select_all -> {
                    selectAll()
                    true // keep the toolbar up so Copy can follow
                }

                else -> delegate.onActionItemClicked(mode, item)
            }

        override fun onDestroyActionMode(mode: android.view.ActionMode) {
            delegate.onDestroyActionMode(mode)
        }

        private fun rebuildMenu(menu: android.view.Menu) {
            menu.clear()
            android.view.MenuInflater(context).inflate(R.menu.viewer_selection, menu)
            if (currentFormat == DocumentFormat.XLSX) {
                menu.removeItem(R.id.action_select_all)
            }
        }
    }

    fun goToPage(index: Int) {
        // ponytail: single dispatch format, goTo passes a number instead of cmd
        webView.evaluateJavascript("window.ovDispatch && window.ovDispatch({goTo: $index})", null)
    }

    private fun dispatch(cmd: String) {
        webView.evaluateJavascript("window.ovDispatch && window.ovDispatch({cmd: '$cmd'})", null)
    }

    @SuppressLint("SetJavaScriptEnabled") // JS is required to drive the bundled renderer
    private fun openViewer(format: DocumentFormat) {
        android.util.Log.i(TAG, "opening viewer for $format")
        currentFormat = format
        checkWebViewVersion() // reports via onErrorListener and skips loading on failure
        if (webviewUsable == false) return
        webView.loadUrl(
            "https://appassets.androidplatform.net/assets/viewer.html?format=${format.name.lowercase()}&token=$token"
        )
    }

    private var webviewUsable: Boolean? = null

    private fun checkWebViewVersion() {
        if (webviewUsable != null) return
        val pkg = try {
            WebViewCompat.getCurrentWebViewPackage(context)
        } catch (_: Exception) {
            null
        }
        webviewUsable = when {
            pkg == null -> {
                if (Build.VERSION.SDK_INT >= 28) {
                    postError("No WebView provider is installed on this device")
                    false
                } else true // pre-P devices without an updatable provider always have a system WebView
            }

            pkg.versionName.orEmpty().substringBefore('.').toIntOrNull()
                ?.let { it < MIN_WEBVIEW_MAJOR } == true -> {
                postError("System WebView ${pkg.versionName} is too old; ${MIN_WEBVIEW_MAJOR}+ required for WASM rendering")
                false
            }

            else -> true
        }
    }

    private fun postError(message: String) {
        android.util.Log.w(TAG, "viewer error: $message")
        main.post { onErrorListener?.onError(message) }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }

    private inner class Bridge {
        @JavascriptInterface
        fun onLoadSuccess(format: String, pages: Int, index: Int) {
            main.post {
                onLoadListener?.onLoaded(DocumentFormat.valueOf(format.uppercase()), pages, index)
                onPageListener?.onPageChanged(index)
            }
        }

        @JavascriptInterface
        fun onLoadError(message: String) {
            postError(message)
        }

        @JavascriptInterface
        fun onPageChanged(index: Int) {
            main.post { onPageListener?.onPageChanged(index) }
        }

        @JavascriptInterface
        fun onDebug(message: String) {
            android.util.Log.i(TAG, "js: $message")
            main.post { onDebugListener?.onDebug(message) }
        }

        @JavascriptInterface
        fun onSelectionChanged(text: String) {
            android.util.Log.i(TAG, "selection changed: ${text.length} chars")
            main.post { onSelectionListener?.onSelectionChanged(text.ifEmpty { null }) }
        }

        @JavascriptInterface
        fun onCopyResult(text: String) {
            android.util.Log.i(TAG, "copy result: ${text.length} chars (sink set: ${clipboardSink != null})")
            main.post {
                val sink = clipboardSink
                when {
                    text.isEmpty() -> onDebugListener?.onDebug("copy: nothing selected")
                    sink != null -> {
                        sink.copyText(text)
                        onDebugListener?.onDebug("copied ${text.length} chars via local clipboard sink")
                    }

                    else -> {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "document",
                                text
                            )
                        )
                        android.util.Log.w(TAG, "copied ${text.length} chars to SYSTEM clipboard (no sink set)")
                        onDebugListener?.onDebug("copied ${text.length} chars to clipboard")
                    }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        webView.destroy()
        io.shutdown()
        docFile.parentFile?.deleteRecursively()
    }

    /** Serves viewer.html from bundled assets and the renderer files from the
     *  extracted [OoxmlAssets] dir, with explicit MIME types: Android's
     *  MimeTypeMap misses .mjs/.wasm, and ES modules refuse to load with a
     *  non-JavaScript MIME type. Everything is local, so responses carry
     *  Cache-Control: no-store — without it Chromium happily keeps serving a
     *  stale viewer.html from its HTTP cache across app updates. */
    private class FileAssetHandler(context: Context) : WebViewAssetLoader.PathHandler {
        private val assets = context.assets

        @Volatile
        var rendererRoot: File? = null

        override fun handle(path: String): WebResourceResponse? {
            if (path == "viewer.html") {
                return try {
                    noStore(WebResourceResponse("text/html", null, assets.open(path)))
                } catch (_: IOException) {
                    null
                }
            }
            val root = rendererRoot ?: return null
            if (!path.startsWith("ooxml/")) return null
            val file = File(root, path.removePrefix("ooxml/"))
            if (!file.canonicalFile.startsWith(root.canonicalFile) || !file.isFile) return null
            return try {
                noStore(WebResourceResponse(mimeFor(path), null, file.inputStream().buffered()))
            } catch (_: IOException) {
                null
            }
        }

        private fun mimeFor(path: String): String =
            when (path.substringAfterLast('.', "").lowercase()) {
                "mjs", "js" -> "text/javascript"
                "wasm" -> "application/wasm"
                "html" -> "text/html"
                "css" -> "text/css"
                "json" -> "application/json"
                else -> "application/octet-stream"
            }

        private fun noStore(response: WebResourceResponse): WebResourceResponse {
            response.responseHeaders = mapOf("Cache-Control" to "no-store")
            return response
        }
    }

    /** Serves this instance's cached document at /document/&lt;token&gt;/file, same-origin for the page. */
    private class DocumentPathHandler(private val file: File) : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            if (!path.endsWith("/file") || !file.isFile) return null
            return try {
                val response = WebResourceResponse("application/octet-stream", null, file.inputStream())
                response.responseHeaders = mapOf("Cache-Control" to "no-store")
                response
            } catch (_: IOException) {
                null
            }
        }
    }

    private companion object {
        const val TAG = "OfficeViewer"
        const val MIN_WEBVIEW_MAJOR = 91
    }
}
