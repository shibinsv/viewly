package shibin.kmp.viewly.presentation

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shibin.kmp.viewly.domain.common.DeviceDocument
import shibin.kmp.viewly.domain.common.fetchDeviceDocuments
import java.io.File

enum class HomeTab {
    ALL, FOLDERS
}

data class FolderSummary(
    val path: String,
    val name: String,
    val documentCount: Int
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    var documentList by mutableStateOf<List<DeviceDocument>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var hasPermission by mutableStateOf(false)
        private set

    var currentTab by mutableStateOf(HomeTab.ALL)
    var selectedFolderPath by mutableStateOf<String?>(null)

    var starredDocIds by mutableStateOf(setOf<Long>())
        private set

    val folders: List<FolderSummary> by derivedStateOf {
        documentList.groupBy { it.folderPath }.map { (path, docs) ->
            FolderSummary(
                path = path,
                name = docs.firstOrNull()?.folderName ?: "Storage",
                documentCount = docs.size
            )
        }.sortedBy { it.name.lowercase() }
    }

    init {
        loadStarredDocs()
        checkAndLoadDocuments()
    }

    private fun loadStarredDocs() {
        val prefs = getApplication<Application>().getSharedPreferences("viewly_prefs", Context.MODE_PRIVATE)
        val stored = prefs.getStringSet("starred_docs", emptySet()) ?: emptySet()
        starredDocIds = stored.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun toggleStar(docId: Long) {
        val updated = if (starredDocIds.contains(docId)) {
            starredDocIds - docId
        } else {
            starredDocIds + docId
        }
        starredDocIds = updated
        val prefs = getApplication<Application>().getSharedPreferences("viewly_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("starred_docs", updated.map { it.toString() }.toSet()).apply()
    }

    fun checkAndLoadDocuments() {
        val context = getApplication<Application>()
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        hasPermission = granted
        if (granted && documentList.isEmpty()) {
            isLoading = true
            viewModelScope.launch(Dispatchers.IO) {
                val docs = fetchDeviceDocuments(context)
                withContext(Dispatchers.Main) {
                    documentList = docs
                    isLoading = false
                }
            }
        }
    }

    fun refreshDocuments() {
        val context = getApplication<Application>()
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        hasPermission = granted
        if (granted) {
            isLoading = true
            viewModelScope.launch(Dispatchers.IO) {
                val docs = fetchDeviceDocuments(context)
                withContext(Dispatchers.Main) {
                    documentList = docs
                    isLoading = false
                }
            }
        }
    }

    fun deleteDocument(doc: DeviceDocument): Boolean {
        if (doc.uri.scheme == "file") {
            val deleted = doc.uri.path?.let { File(it).delete() } ?: false
            if (deleted) {
                documentList = documentList.filterNot { it.id == doc.id }
                if (starredDocIds.contains(doc.id)) {
                    toggleStar(doc.id)
                }
                return true
            }
        }
        return false
    }
}
