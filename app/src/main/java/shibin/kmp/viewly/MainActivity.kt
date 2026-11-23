package shibin.kmp.viewly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import shibin.kmp.viewly.domain.common.DeviceDocument
import shibin.kmp.viewly.presentation.DocumentViewerScreen
import shibin.kmp.viewly.presentation.HomeScreen
import shibin.kmp.viewly.ui.theme.ViewlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ViewlyTheme {
                var selectedDoc by remember { mutableStateOf<DeviceDocument?>(null) }

                if (selectedDoc == null) {
                    HomeScreen(onDocumentSelected = { doc -> selectedDoc = doc })
                } else {
                    DocumentViewerScreen(
                        document = selectedDoc!!,
                        onBack = { selectedDoc = null }
                    )
                }
            }
        }
    }
}
