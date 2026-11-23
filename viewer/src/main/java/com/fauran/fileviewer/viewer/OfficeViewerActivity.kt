package com.fauran.fileviewer.viewer

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Drop-in full-screen document viewer for apps that don't use Compose.
 * Launch with [EXTRA_DOCUMENT] set to a content:// or file:// Uri
 * (callers must hold a grant for content Uris, e.g. via ACTION_OPEN_DOCUMENT).
 */
class OfficeViewerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(EXTRA_DOCUMENT)
        if (uri == null) {
            finish()
            return
        }
        val viewer = OfficeViewerView(this)
        viewer.onErrorListener = OnErrorListener { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        setContentView(viewer)
        viewer.load(uri)
    }

    companion object {
        const val EXTRA_DOCUMENT = "com.fauran.fileviewer.viewer.DOCUMENT"
    }
}
