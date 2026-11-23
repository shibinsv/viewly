package shibin.kmp.viewly.domain.common

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

enum class DocumentType(val label: String) {
    PDF("PDF"),
    WORD("DOCX"),
    EXCEL("XLSX"),
    POWERPOINT("PPTX")
}

data class DeviceDocument(
    val id: Long,
    val name: String,
    val uri: Uri,
    val sizeBytes: Long,
    val dateModifiedMillis: Long,
    val type: DocumentType,
    val folderName: String = "Storage"
)

private val SUPPORTED_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")

fun fetchDeviceDocuments(context: Context): List<DeviceDocument> {
    // Key by lowercase canonical path so no two distinct files ever overwrite each other
    val documentsByPath = LinkedHashMap<String, DeviceDocument>()

    // 1. Direct File System Scan FIRST (always has the true, instant file.lastModified() timestamp)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
        val rootDir = Environment.getExternalStorageDirectory()
        if (rootDir != null && rootDir.exists()) {
            scanDirectoryRecursively(rootDir, documentsByPath)
        }
    }

    // 2. Supplement with MediaStore (catches any files not exposed via direct path)
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Files.getContentUri("external")
    }

    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.Files.FileColumns.DATA
    )

    val extConditions = SUPPORTED_EXTENSIONS.joinToString(" OR ") {
        "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
    }
    val extArgs = SUPPORTED_EXTENSIONS.map { "%.$it" }.toTypedArray()
    val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

    context.contentResolver.query(
        collection,
        projection,
        extConditions,
        extArgs,
        sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: continue
            val size = cursor.getLong(sizeCol)
            if (size <= 0L) continue

            val path = if (dataCol != -1) cursor.getString(dataCol) else null
            val pathKey = path?.lowercase() ?: "mediastore_$id"
            if (documentsByPath.containsKey(pathKey)) continue

            // Use the newer of DATE_MODIFIED or DATE_ADDED, fallback to real file.lastModified()
            val modSeconds = cursor.getLong(modifiedCol)
            val addSeconds = cursor.getLong(addedCol)
            val diskMillis = path?.let { File(it).takeIf { f -> f.exists() }?.lastModified() } ?: 0L
            val bestTimestamp = maxOf(modSeconds * 1000L, addSeconds * 1000L, diskMillis)

            val folder = path?.let { File(it).parentFile?.name } ?: "Storage"
            val contentUri = ContentUris.withAppendedId(collection, id)
            val type = detectTypeFromName(name) ?: continue

            documentsByPath[pathKey] = DeviceDocument(
                id = id,
                name = name,
                uri = contentUri,
                sizeBytes = size,
                dateModifiedMillis = bestTimestamp,
                type = type,
                folderName = folder
            )
        }
    }

    return documentsByPath.values.sortedByDescending { it.dateModifiedMillis }
}

private fun scanDirectoryRecursively(
    dir: File,
    documentsByPath: MutableMap<String, DeviceDocument>
) {
    val files = dir.listFiles() ?: return
    for (file in files) {
        if (file.isDirectory) {
            val dirName = file.name
            if (dirName.startsWith(".")) continue
            if (dir.name == "Android" && !dirName.equals("media", ignoreCase = true)) continue
            scanDirectoryRecursively(file, documentsByPath)
        } else if (file.isFile && file.length() > 0L) {
            val type = detectTypeFromName(file.name) ?: continue
            val pathKey = file.absolutePath.lowercase()

            documentsByPath[pathKey] = DeviceDocument(
                id = file.absolutePath.hashCode().toLong(),
                name = file.name,
                uri = Uri.fromFile(file),
                sizeBytes = file.length(),
                dateModifiedMillis = file.lastModified(),
                type = type,
                folderName = file.parentFile?.name ?: "Storage"
            )
        }
    }
}


private fun detectTypeFromName(fileName: String): DocumentType? {
    return when (fileName.substringAfterLast(".", "").lowercase()) {
        "pdf" -> DocumentType.PDF
        "doc", "docx" -> DocumentType.WORD
        "xls", "xlsx" -> DocumentType.EXCEL
        "ppt", "pptx" -> DocumentType.POWERPOINT
        else -> null
    }
}
