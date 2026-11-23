package com.fauran.fileviewer.viewer

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Extracts the bundled ooxml.zip (packaged from the @silurus/ooxml webjar at
 * build time) into filesDir on first use. Serving from real files keeps the
 * stored APK small — only the ~4.6 MB zip ships instead of ~14 MB of
 * uncompressed JS/WASM.
 * Call from a background thread only.
 */
internal object OoxmlAssets {

    private const val ZIP_ASSET = "ooxml.zip"
    private const val VERSION_ASSET = "ooxml-version.txt"
    private const val MARKER = ".ready"

    /** Returns the extracted renderer directory, extracting it on first call. */
    fun ensureExtracted(context: Context): File {
        val version = context.assets.open(VERSION_ASSET).bufferedReader().use { it.readText().trim() }
        val root = File(File(context.filesDir, "officeviewer/ooxml"), version)
        if (File(root, MARKER).isFile) return root

        val tmp = File(root.parentFile, root.name + ".tmp")
        tmp.deleteRecursively()
        root.deleteRecursively()
        tmp.mkdirs()
        context.assets.open(ZIP_ASSET).use { zip ->
            ZipInputStream(zip.buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val out = File(tmp, entry.name)
                    // zip-slip guard: canonical path must stay inside the target dir
                    if (!out.canonicalFile.startsWith(tmp.canonicalFile)) {
                        throw IOException("Blocked archive entry escaping target dir: ${entry.name}")
                    }
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        // use{} closes the buffered stream — without the flush,
                        // small entries extract as 0 bytes and large ones lose their tail.
                        out.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        if (!tmp.renameTo(root)) throw IOException("Could not finalize extracted renderer assets")
        File(root, MARKER).writeText(version)

        // Drop other versions extracted previously.
        root.parentFile?.listFiles()
            ?.filter { it.isDirectory && it.name != root.name && it.name != tmp.name }
            ?.forEach { it.deleteRecursively() }
        return root
    }
}
