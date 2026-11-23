package com.fauran.fileviewer.viewer

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File

enum class OfficeViewerStatus { Idle, Loading, Loaded, Failed }

/** What got loaded: format, total pages/sheets/slides and the current 1-based index. */
@Immutable
data class OfficeDocumentInfo(
    val format: DocumentFormat,
    val pageCount: Int,
    val pageIndex: Int,
)

/**
 * Observable state and control surface for [OfficeDocumentViewer].
 * Obtain via [rememberOfficeViewerState]; load a document, then drive
 * navigation and zoom from here or read [status]/[documentInfo] for your UI.
 */
@Stable
class OfficeViewerState internal constructor() {

    var status: OfficeViewerStatus by mutableStateOf(OfficeViewerStatus.Idle)
        internal set
    var documentInfo: OfficeDocumentInfo? by mutableStateOf(null)
        internal set
    var errorMessage: String? by mutableStateOf(null)
        internal set

    /** Rolling JS-side diagnostics; useful on ROMs that filter logcat. */
    var debugMessages: List<String> by mutableStateOf(emptyList())
        internal set

    /** Current text selection in the document; null when nothing is selected. */
    var selectedText: String? by mutableStateOf(null)
        internal set

    /**
     * When set, `copySelection()` routes through this sink instead of the system
     * clipboard (security: copied document text never leaves the process).
     */
    var clipboardSink: ClipboardSink?
        get() = view?.clipboardSink
        set(value) { pendingSink = value; view?.clipboardSink = value }
    private var pendingSink: ClipboardSink? = null

    internal var view: OfficeViewerView? = null
        set(value) {
            field?.let(::unwire)
            field = value
            if (value != null) {
                wire(value)
                pendingSink?.let { value.clipboardSink = it }
                pendingLoad?.invoke()
                pendingLoad = null
            }
        }

    private var pendingLoad: (() -> Unit)? = null
    private var lastSource: (() -> Unit)? = null

    fun load(uri: Uri) = enqueue {
        view?.load(uri)
    }

    fun load(file: File) = enqueue {
        view?.load(file)
    }

    /** Re-loads the last source after a failure. */
    fun retry() {
        lastSource?.invoke()
    }

    fun nextPage() = view?.nextPage()
    fun previousPage() = view?.previousPage()
    fun goToPage(index: Int) = view?.goToPage(index)
    fun zoomIn() = view?.zoomIn()
    fun zoomOut() = view?.zoomOut()
    fun fitPage() = view?.fitPage()
    fun fitWidth() = view?.fitWidth()

    /** Copies the current selection to the system clipboard. */
    fun copySelection() = view?.copySelection()

    /** Selects the whole document text layer (docx/pptx; no-op for xlsx). */
    fun selectAll() = view?.selectAll()

    private fun enqueue(load: () -> Unit) {
        status = OfficeViewerStatus.Loading
        errorMessage = null
        lastSource = load
        if (view == null) pendingLoad = load else load()
    }

    private fun wire(view: OfficeViewerView) {
        view.onLoadListener = OnLoadListener { format, pages, index ->
            status = OfficeViewerStatus.Loaded
            documentInfo = OfficeDocumentInfo(format, pages, index)
        }
        view.onPageListener = OnPageListener { index ->
            documentInfo = documentInfo?.copy(pageIndex = index)
        }
        view.onErrorListener = OnErrorListener { message ->
            status = OfficeViewerStatus.Failed
            errorMessage = message
        }
        view.onDebugListener = OnDebugListener { message ->
            debugMessages = (debugMessages + message).takeLast(5)
        }
        view.onSelectionListener = OnSelectionListener { text ->
            selectedText = text
        }
    }

    private fun unwire(view: OfficeViewerView) {
        view.onLoadListener = null
        view.onPageListener = null
        view.onErrorListener = null
        view.onDebugListener = null
        view.onSelectionListener = null
    }
}

@Composable
fun rememberOfficeViewerState(): OfficeViewerState = remember { OfficeViewerState() }

/** Creates state and loads [uri] whenever it changes (null = nothing loaded). */
@Composable
fun rememberOfficeViewerState(uri: Uri?): OfficeViewerState {
    val state = remember { OfficeViewerState() }
    LaunchedEffect(uri) { if (uri != null) state.load(uri) }
    return state
}

/** Creates state and loads [file] whenever it changes (null = nothing loaded). */
@Composable
fun rememberOfficeViewerState(file: File?): OfficeViewerState {
    val state = remember { OfficeViewerState() }
    LaunchedEffect(file) { if (file != null) state.load(file) }
    return state
}
