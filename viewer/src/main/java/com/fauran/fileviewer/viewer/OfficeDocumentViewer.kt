package com.fauran.fileviewer.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose-first offline viewer for OOXML documents (.docx/.xlsx/.pptx).
 * Renders all formats as a centered, scrollable page column on a gray
 * background with pinch-to-zoom. Loading and error overlays are built in and
 * replaceable via [loadingContent] and [errorContent]; drive navigation and
 * zoom through [state].
 */
@Composable
fun OfficeDocumentViewer(
    state: OfficeViewerState,
    modifier: Modifier = Modifier,
    loadingContent: @Composable BoxScope.() -> Unit = {
        DefaultLoadingContent()
    },
    errorContent: @Composable BoxScope.(message: String?, retry: () -> Unit) -> Unit = { message, retry ->
        DefaultErrorContent(message, retry)
    },
) {
    Box(modifier) {
        // The engine must stay composed while loading — it is what reports
        // success/failure, so gating it on state would deadlock the spinner.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                OfficeViewerView(ctx).also { view -> state.view = view }
            },
        )
        when (state.status) {
            OfficeViewerStatus.Loading -> loadingContent()
            OfficeViewerStatus.Failed -> errorContent(state.errorMessage, state::retry)
            else -> {}
        }
    }
}

@Composable
private fun BoxScope.DefaultLoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Rendering…")
    }
}

@Composable
private fun BoxScope.DefaultErrorContent(message: String?, retry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Could not display this document",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = retry) { Text("Retry") }
    }
}
