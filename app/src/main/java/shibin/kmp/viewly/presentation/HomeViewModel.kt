package shibin.kmp.viewly.presentation

import android.app.Application
import android.os.Build
import android.os.Environment
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

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    var documentList by mutableStateOf<List<DeviceDocument>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var hasPermission by mutableStateOf(false)
        private set

    init {
        checkAndLoadDocuments()
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
                return true
            }
        }
        return false
    }
}
