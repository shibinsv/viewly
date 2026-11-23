package com.fauran.fileviewer.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentFormatTest {

    @Test
    fun detectsFormatsByExtension() {
        assertEquals(DocumentFormat.DOCX, DocumentFormat.fromFileName("report.DOCX"))
        assertEquals(DocumentFormat.XLSX, DocumentFormat.fromFileName("budget-2026.xlsx"))
        assertEquals(DocumentFormat.PPTX, DocumentFormat.fromFileName("/storage/deck.name.pptx"))
    }

    @Test
    fun rejectsUnsupportedAndMissingNames() {
        assertNull(DocumentFormat.fromFileName("old-format.doc"))
        assertNull(DocumentFormat.fromFileName("file.xls"))
        assertNull(DocumentFormat.fromFileName("archive.pptx.zip"))
        assertNull(DocumentFormat.fromFileName(null))
        assertNull(DocumentFormat.fromFileName("noextension"))
    }

    @Test
    fun detectsFormatsByMimeType() {
        assertEquals(
            DocumentFormat.DOCX,
            DocumentFormat.fromMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        )
        assertNull(DocumentFormat.fromMimeType("application/msword"))
        assertNull(DocumentFormat.fromMimeType(null))
    }
}
