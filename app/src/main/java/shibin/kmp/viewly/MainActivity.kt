package shibin.kmp.viewly

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import shibin.kmp.viewly.domain.common.DeviceDocument
import shibin.kmp.viewly.domain.common.DocumentType
import shibin.kmp.viewly.presentation.DocumentViewerScreen
import shibin.kmp.viewly.presentation.HomeScreen
import shibin.kmp.viewly.ui.theme.ViewlyTheme

class MainActivity : ComponentActivity() {
    private var incomingDocument by mutableStateOf<DeviceDocument?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingDocument = extractDocumentFromIntent(this, intent)

        setContent {
            ViewlyTheme {
                var selectedDoc by remember { mutableStateOf(incomingDocument) }

                if (selectedDoc == null) {
                    HomeScreen(onDocumentSelected = { doc -> selectedDoc = doc })
                } else {
                    DocumentViewerScreen(
                        document = selectedDoc!!,
                        onBack = {
                            if (incomingDocument != null) {
                                finish()
                            } else {
                                selectedDoc = null
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingDocument = extractDocumentFromIntent(this, intent)
    }
}

fun extractDocumentFromIntent(context: Context, intent: Intent?): DeviceDocument? {
    if (intent == null) return null
    val uri: Uri = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        }
        else -> intent.data
    } ?: return null

    var name = "Document"
    var sizeBytes = 0L

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex != -1) {
                cursor.getString(nameIndex)?.let { name = it }
            }
            if (sizeIndex != -1) {
                sizeBytes = cursor.getLong(sizeIndex)
            }
        }
    }

    if (name == "Document" || sizeBytes == 0L) {
        val path = uri.path ?: uri.toString()
        val lastSegment = path.substringAfterLast('/')
        if (lastSegment.isNotEmpty()) {
            name = lastSegment
        }
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                sizeBytes = pfd.statSize
            }
        } catch (_: Exception) {}
    }

    val type = when (name.substringAfterLast(".", "").lowercase()) {
        "pdf" -> DocumentType.PDF
        "doc", "docx" -> DocumentType.WORD
        "xls", "xlsx" -> DocumentType.EXCEL
        "ppt", "pptx" -> DocumentType.POWERPOINT
        else -> {
            val mime = context.contentResolver.getType(uri) ?: intent.type
            when {
                mime?.contains("pdf") == true -> DocumentType.PDF
                mime?.contains("word") == true || mime?.contains("document") == true -> DocumentType.WORD
                mime?.contains("sheet") == true || mime?.contains("excel") == true -> DocumentType.EXCEL
                mime?.contains("presentation") == true || mime?.contains("powerpoint") == true -> DocumentType.POWERPOINT
                else -> DocumentType.PDF
            }
        }
    }

    return DeviceDocument(
        id = uri.hashCode().toLong(),
        name = name,
        uri = uri,
        sizeBytes = sizeBytes,
        dateModifiedMillis = System.currentTimeMillis(),
        type = type,
        folderName = "External"
    )
}
