package com.fauran.fileviewer.viewer

/** Supported OOXML document formats. Legacy binary formats (.doc/.xls/.ppt) are not supported. */
enum class DocumentFormat(val extension: String, val mimeType: String) {
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    companion object {
        fun fromFileName(name: String?): DocumentFormat? =
            entries.firstOrNull { it.extension == name?.substringAfterLast('.', "")?.lowercase() }

        fun fromMimeType(mime: String?): DocumentFormat? =
            entries.firstOrNull { it.mimeType.equals(mime, ignoreCase = true) }
    }
}
