package shibin.kmp.viewly.domain.common

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

fun uriToCacheFile(context: Context, uri: Uri, fileName: String): File? {
    return try {
        context.cacheDir.listFiles()?.forEach {
            if (it.name.startsWith("temp_doc_")) it.delete()
        }

        val extension = fileName.substringAfterLast(".", "pdf")
        val tempFile = File(context.cacheDir, "temp_doc_${System.currentTimeMillis()}.$extension")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


