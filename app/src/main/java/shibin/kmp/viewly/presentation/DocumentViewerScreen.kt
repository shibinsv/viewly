package shibin.kmp.viewly.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.fauran.fileviewer.viewer.OfficeDocumentViewer
import com.fauran.fileviewer.viewer.rememberOfficeViewerState
import com.idesade.compose.pdf.PdfReader
import shibin.kmp.viewly.domain.common.DeviceDocument
import shibin.kmp.viewly.domain.common.DocumentType
import shibin.kmp.viewly.domain.common.uriToCacheFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    document: DeviceDocument,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cachedFile = remember(document.uri) {
        uriToCacheFile(context, document.uri, document.name)
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = document.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareDocument(context, document.uri, document.name) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (cachedFile == null) {
                Text("Unable to load document")
            } else if (document.type == DocumentType.PDF) {
                PdfReader(
                    file = cachedFile,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val viewerState = rememberOfficeViewerState(cachedFile)
                OfficeDocumentViewer(
                    state = viewerState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun shareDocument(context: Context, uri: Uri, title: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share file via"))
}
