package shibin.kmp.viewly.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import shibin.kmp.viewly.R
import shibin.kmp.viewly.domain.common.DeviceDocument
import shibin.kmp.viewly.domain.common.DocumentType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SortOption(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME_ASC("Name (A–Z)"),
    SIZE_DESC("Largest first")
}

private enum class DocumentViewMode {
    LIST, GRID
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onDocumentSelected: (DeviceDocument) -> Unit,
    viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val documentList = viewModel.documentList
    val isLoading = viewModel.isLoading
    val hasPermission = viewModel.hasPermission

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<DocumentType?>(null) }
    var showStarredOnly by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.NEWEST) }
    var viewMode by remember { mutableStateOf(DocumentViewMode.LIST) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Bottom sheet states
    var actionSheetDoc by remember { mutableStateOf<DeviceDocument?>(null) }
    var infoDialogDoc by remember { mutableStateOf<DeviceDocument?>(null) }

    val sortedAndFilteredDocs by remember(documentList, searchQuery, selectedType, showStarredOnly, sortOption, viewModel.starredDocIds) {
        derivedStateOf {
            val filtered = documentList.filter { doc ->
                val matchesStarred = !showStarredOnly || viewModel.starredDocIds.contains(doc.id)
                val matchesType = selectedType == null || doc.type == selectedType
                val matchesQuery = doc.name.contains(searchQuery.trim(), ignoreCase = true) ||
                        doc.folderName.contains(searchQuery.trim(), ignoreCase = true)
                matchesStarred && matchesType && matchesQuery
            }

            when (sortOption) {
                SortOption.NEWEST -> filtered.sortedByDescending { it.dateModifiedMillis }
                SortOption.OLDEST -> filtered.sortedBy { it.dateModifiedMillis }
                SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                SortOption.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            }
        }
    }

    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose {}

        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                viewModel.refreshDocuments()
            }
        }
        val externalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Files.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
        } else {
            android.provider.MediaStore.Files.getContentUri("external")
        }
        context.contentResolver.registerContentObserver(externalUri, true, observer)

        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }


    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.checkAndLoadDocuments()
    }

    val manualPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = getFileNameFromUri(context, uri)
            val docType = detectDocumentType(fileName)
            onDocumentSelected(
                DeviceDocument(
                    id = System.currentTimeMillis(),
                    name = fileName,
                    uri = uri,
                    sizeBytes = 0L,
                    dateModifiedMillis = System.currentTimeMillis(),
                    type = docType
                )
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1A2332),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.logo),
                                contentDescription = "Viewly Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column {
                            Text(
                                text = "Viewly",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                            if (hasPermission) {
                                Text(
                                    text = "${documentList.size} documents",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            manualPickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    "application/vnd.ms-powerpoint"
                                )
                            )
                        },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Browse",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Segmented Tabs: All Files vs Folders (Hidden when inside a specific folder)
            if (viewModel.selectedFolderPath == null) {
                PrimaryTabRow(
                    selectedTabIndex = viewModel.currentTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Tab(
                        selected = viewModel.currentTab == HomeTab.ALL,
                        onClick = {
                            viewModel.currentTab = HomeTab.ALL
                            viewModel.selectedFolderPath = null
                        },
                        text = { Text("All Files") }
                    )
                    Tab(
                        selected = viewModel.currentTab == HomeTab.FOLDERS,
                        onClick = {
                            viewModel.currentTab = HomeTab.FOLDERS
                            viewModel.selectedFolderPath = null
                        },
                        text = { Text("Folders (${viewModel.folders.size})") }
                    )
                }
            } else {
                // Clean Folder Header when inside a folder
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.selectedFolderPath = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to folders")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = viewModel.folders.find { it.path == viewModel.selectedFolderPath }?.name ?: "Folder",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val count = sortedAndFilteredDocs.count { it.folderPath == viewModel.selectedFolderPath }
                            Text(
                                text = "$count items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (hasPermission && viewModel.currentTab == HomeTab.ALL && viewModel.selectedFolderPath == null) {
                // Recent & Starred Horizontal Bar with Snapping
                val recentDocs = documentList.sortedByDescending { it.dateModifiedMillis }.take(5)
                if (recentDocs.isNotEmpty()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent & Starred",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val recentListState = rememberLazyListState()
                        LazyRow(
                            state = recentListState,
                            flingBehavior = rememberSnapFlingBehavior(lazyListState = recentListState),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recentDocs, key = { it.id }) { doc ->
                                RecentDocumentCard(
                                    doc = doc,
                                    isStarred = viewModel.starredDocIds.contains(doc.id),
                                    onStarClick = { viewModel.toggleStar(doc.id) },
                                    onClick = { onDocumentSelected(doc) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

// Modern Pill Search Bar + Sort & View Mode Trigger
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = { Text("Search files or folders...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                            IconButton(onClick = {
                                viewMode = if (viewMode == DocumentViewMode.LIST) DocumentViewMode.GRID else DocumentViewMode.LIST
                            }) {
                                Icon(
                                    imageVector = if (viewMode == DocumentViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                    contentDescription = "Toggle View Mode"
                                )
                            }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    SortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = option.label,
                                                    fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                sortOption = option
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(25),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
// Color-Matched Filter Chips (Including Starred Chip)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedType == null && !showStarredOnly,
                            onClick = {
                                selectedType = null
                                showStarredOnly = false
                            },
                            label = { Text("All (${documentList.size})") },
                            shape = RoundedCornerShape(50)
                        )
                    }
                    if (viewModel.starredDocIds.isNotEmpty()) {
                        item {
                            FilterChip(
                                selected = showStarredOnly,
                                onClick = { showStarredOnly = !showStarredOnly },
                                label = { Text("Starred (${viewModel.starredDocIds.size})") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFC107).copy(alpha = 0.16f),
                                    selectedLabelColor = Color(0xFF8D6E63)
                                )
                            )
                        }
                    }
                    items(DocumentType.entries) { type ->
                        val count = documentList.count { it.type == type }
                        if (count > 0) {
                            val accent = documentAccentColor(type)
                            FilterChip(
                                selected = selectedType == type && !showStarredOnly,
                                onClick = {
                                    showStarredOnly = false
                                    selectedType = if (selectedType == type) null else type
                                },
                                label = { Text("${type.label} ($count)") },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.16f),
                                    selectedLabelColor = accent
                                )
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !hasPermission -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("Storage permission is needed to find documents on your phone.")
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } else {
                                    legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                            }) {
                                Text("Grant Permission")
                            }
                        }
                    }
                    isLoading && documentList.isEmpty() -> {
                        CircularProgressIndicator()
                    }
                    documentList.isEmpty() -> {
                        Text("No documents found on device")
                    }
                    viewModel.currentTab == HomeTab.FOLDERS && viewModel.selectedFolderPath == null -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            items(viewModel.folders, key = { it.path }) { folder ->
                                FolderCard(
                                    folder = folder,
                                    onClick = { viewModel.selectedFolderPath = folder.path }
                                )
                            }
                        }
                    }
                    else -> {
                        val docsToShow = if (viewModel.currentTab == HomeTab.FOLDERS && viewModel.selectedFolderPath != null) {
                            sortedAndFilteredDocs.filter { it.folderPath == viewModel.selectedFolderPath }
                        } else {
                            sortedAndFilteredDocs
                        }

                        if (viewModel.currentTab == HomeTab.FOLDERS && viewModel.selectedFolderPath != null) {
                            // Clean flat list/grid inside folders
                            if (viewMode == DocumentViewMode.GRID) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(docsToShow, key = { it.id }) { doc ->
                                        DocumentGridCardItem(
                                            doc = doc,
                                            isStarred = viewModel.starredDocIds.contains(doc.id),
                                            onStarClick = { viewModel.toggleStar(doc.id) },
                                            onClick = { onDocumentSelected(doc) },
                                            onMoreClick = { actionSheetDoc = doc }
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                                ) {
                                    items(docsToShow, key = { it.id }) { doc ->
                                        DocumentCardItem(
                                            doc = doc,
                                            isStarred = viewModel.starredDocIds.contains(doc.id),
                                            onStarClick = { viewModel.toggleStar(doc.id) },
                                            onClick = { onDocumentSelected(doc) },
                                            onMoreClick = { actionSheetDoc = doc }
                                        )
                                    }
                                }
                            }
                        } else {
                            val groupedDocs = if (sortOption == SortOption.NEWEST || sortOption == SortOption.OLDEST) {
                                docsToShow.groupBy { formatGroupHeader(it.dateModifiedMillis) }
                            } else {
                                mapOf(sortOption.label to docsToShow)
                            }

                            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                                isRefreshing = isLoading,
                                onRefresh = { viewModel.refreshDocuments() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (viewMode == DocumentViewMode.GRID) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(docsToShow, key = { it.id }) { doc ->
                                            DocumentGridCardItem(
                                                doc = doc,
                                                isStarred = viewModel.starredDocIds.contains(doc.id),
                                                onStarClick = { viewModel.toggleStar(doc.id) },
                                                onClick = { onDocumentSelected(doc) },
                                                onMoreClick = { actionSheetDoc = doc }
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        groupedDocs.forEach { (dateHeader, docsInGroup) ->
                                            stickyHeader(key = dateHeader) {
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                                                ) {
                                                    Text(
                                                        text = dateHeader,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(
                                                            horizontal = 20.dp,
                                                            vertical = 8.dp
                                                        )
                                                    )
                                                }
                                            }
                                            items(docsInGroup, key = { it.id }) { doc ->
                                                DocumentCardItem(
                                                    doc = doc,
                                                    isStarred = viewModel.starredDocIds.contains(doc.id),
                                                    onStarClick = { viewModel.toggleStar(doc.id) },
                                                    onClick = { onDocumentSelected(doc) },
                                                    onMoreClick = { actionSheetDoc = doc }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
// Quick Actions Bottom Sheet
    if (actionSheetDoc != null) {
        val doc = actionSheetDoc!!
        ModalBottomSheet(
            onDismissRequest = { actionSheetDoc = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = {
                        Text(doc.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text("${doc.folderName} • ${formatFileSize(doc.sizeBytes)}")
                    },
                    leadingContent = { DocumentTypeBadge(type = doc.type) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Surface(
                    onClick = {
                        shareSafeDocument(context, doc)
                        actionSheetDoc = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("Share") },
                        leadingContent = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                }
                Surface(
                    onClick = {
                        infoDialogDoc = doc
                        actionSheetDoc = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("File details") },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                    )
                }
                if (doc.uri.scheme == "file") {
                    Surface(
                        onClick = {
                            val deleted = viewModel.deleteDocument(doc)
                            if (deleted) {
                                Toast.makeText(context, "Deleted ${doc.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Unable to delete file", Toast.LENGTH_SHORT).show()
                            }
                            actionSheetDoc = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingContent = {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        )
                    }
                }
            }
        }
    }
// File Details Dialog
    if (infoDialogDoc != null) {
        val doc = infoDialogDoc!!
        AlertDialog(
            onDismissRequest = { infoDialogDoc = null },
            title = { Text("File Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name: ${doc.name}")
                    Text("Type: ${doc.type.label}")
                    Text("Folder: ${doc.folderName}")
                    Text("Size: ${formatFileSize(doc.sizeBytes)}")
                    Text("Modified: ${formatFullDateTime(doc.dateModifiedMillis)}")
                }
            },
            confirmButton = {
                TextButton(onClick = { infoDialogDoc = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun RecentDocumentCard(
    doc: DeviceDocument,
    isStarred: Boolean,
    onStarClick: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .width(200.dp)
            .height(75.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DocumentTypeBadge(type = doc.type, size = 25)
                IconButton(
                    onClick = onStarClick,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Star",
                        tint = if (isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = doc.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FolderCard(
    folder: FolderSummary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${folder.documentCount} documents",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DocumentCardItem(
    doc: DeviceDocument,
    isStarred: Boolean,
    onStarClick: () -> Unit,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
        ) {
            DocumentTypeBadge(type = doc.type)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${doc.folderName} • ${formatFileSize(doc.sizeBytes)} • ${formatTimeOnly(doc.dateModifiedMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onStarClick) {
                Icon(
                    imageVector = if (isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Star",
                    tint = if (isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DocumentGridCardItem(
    doc: DeviceDocument,
    isStarred: Boolean,
    onStarClick: () -> Unit,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DocumentTypeBadge(type = doc.type, size = 32)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onStarClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Star",
                            tint = if (isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Column {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatFileSize(doc.sizeBytes)} • ${doc.folderName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DocumentTypeBadge(type: DocumentType, size: Int = 45) {
    val badgeColor = documentAccentColor(type)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = badgeColor.copy(alpha = 0.14f),
        modifier = Modifier.size(size.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = type.label,
                fontWeight = FontWeight.Bold,
                fontSize = (size / 3).sp,
                color = badgeColor
            )
        }
    }
}
private fun documentAccentColor(type: DocumentType): Color {
    return when (type) {
        DocumentType.PDF -> Color(0xFFD32F2F)
        DocumentType.WORD -> Color(0xFF1976D2)
        DocumentType.EXCEL -> Color(0xFF2E7D32)
        DocumentType.POWERPOINT -> Color(0xFFE65100)
    }
}
private fun shareSafeDocument(context: Context, doc: DeviceDocument) {
    val shareUri = if (doc.uri.scheme == "file" && doc.uri.path != null) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(doc.uri.path!!))
    } else {
        doc.uri
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_STREAM, shareUri)
        putExtra(Intent.EXTRA_SUBJECT, doc.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}
private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "Picked Document.pdf"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            name = cursor.getString(nameIndex) ?: name
        }
    }
    return name
}
private fun detectDocumentType(fileName: String): DocumentType {
    return when (fileName.substringAfterLast(".", "").lowercase()) {
        "pdf" -> DocumentType.PDF
        "doc", "docx" -> DocumentType.WORD
        "xls", "xlsx" -> DocumentType.EXCEL
        "ppt", "pptx" -> DocumentType.POWERPOINT
        else -> DocumentType.PDF
    }
}
private fun formatGroupHeader(timeMillis: Long): String {
    return when {
        DateUtils.isToday(timeMillis) -> "Today"
        DateUtils.isToday(timeMillis + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timeMillis))
    }
}
private fun formatTimeOnly(timeMillis: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timeMillis))
}
private fun formatFullDateTime(timeMillis: Long): String {
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timeMillis))
}
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
