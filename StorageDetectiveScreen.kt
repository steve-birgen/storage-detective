package com.yourapp.storageapp.storage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.storageapp.storage.models.*
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDetectiveScreen(viewModel: StorageViewModel) {

    val isScanning by viewModel.isScanning.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val sortedApps by viewModel.sortedApps.collectAsState()
    val appSortOption by viewModel.appSortOption.collectAsState()
    val deltas by viewModel.deltas.collectAsState()
    val largeFiles by viewModel.largeFiles.collectAsState()
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    val recycledFiles by viewModel.recycledFiles.collectAsState()
    val junkResult by viewModel.junkResult.collectAsState()
    val smartSuggestions by viewModel.smartSuggestions.collectAsState()
    val historySnapshots by viewModel.historySnapshots.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Detective") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = { viewModel.runFullScan() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                    }
                },
            )
        },
    ) { padding ->
        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Scanning storage...", fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This may take a few seconds",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                // High Storage Alert
                summary?.let { s ->
                    if (s.usedPercentage >= 85f) {
                        item {
                            HighStorageAlertCard(
                                usedPercentage = s.usedPercentage,
                                onCleanJunkClick = viewModel::cleanJunkFiles,
                            )
                        }
                    }
                    item { TotalStorageCard(s) }
                    item { StorageTreemapCard(s, categories, apps) }
                }

                // Smart AI Suggestions
                if (smartSuggestions.isNotEmpty()) {
                    item {
                        SmartSuggestionsCard(
                            suggestions = smartSuggestions,
                        ) { action ->
                            when (action) {
                                SuggestionAction.CLEAN_JUNK -> viewModel.cleanJunkFiles()
                                SuggestionAction.RECYCLE_DUPLICATES -> {
                                    duplicateGroups.firstOrNull()?.files?.getOrNull(1)?.let {
                                        viewModel.recycleFile(it)
                                    }
                                }
                                SuggestionAction.VIEW_LARGE_FILES -> { }
                                SuggestionAction.OPEN_APP_HUB -> { }
                            }
                        }
                    }
                }

                // Junk & Cache Cleaner
                junkResult?.let { j ->
                    if (j.totalSizeBytes > 0) {
                        item {
                            JunkCleanerCard(
                                junkResult = j,
                                onCleanJunk = viewModel::cleanJunkFiles,
                            )
                        }
                    }
                }

                // App Manager Hub
                if (apps.isNotEmpty()) {
                    item {
                        AppManagerHubCard(
                            apps = sortedApps,
                            currentSort = appSortOption,
                            onSortSelected = viewModel::setAppSortOption,
                            onOpenAppSettings = viewModel::openAppSettings,
                        )
                    }
                }

                // Storage History Trend Chart
                if (historySnapshots.isNotEmpty()) {
                    item {
                        StorageHistoryCard(snapshots = historySnapshots)
                    }
                }

                // Recycle Bin Card
                if (recycledFiles.isNotEmpty()) {
                    item {
                        RecycleBinCard(
                            recycledFiles = recycledFiles,
                            onRestore = viewModel::restoreRecycledFile,
                            onDeletePermanently = viewModel::permanentlyDeleteRecycledFile,
                            onEmptyBin = viewModel::emptyRecycleBin,
                        )
                    }
                }

                // Categories (Identified)
                if (categories.isNotEmpty()) {
                    item { SectionHeader("Identified", "What we can see") }
                    items(categories) { cat ->
                        CategoryRow(cat)
                    }
                }

                // Apps (Attributed)
                if (apps.isNotEmpty()) {
                    item { SectionHeader("Attributed", "App storage") }
                    items(apps.take(10)) { app ->
                        AppRow(app)
                    }
                }

                // Duplicate Files
                if (duplicateGroups.isNotEmpty()) {
                    val totalWastedBytes = duplicateGroups.sumOf { it.wastedBytes }
                    item {
                        SectionHeader(
                            "Duplicate Files (${duplicateGroups.size} sets)",
                            "Potential savings: ${formatBytes(totalWastedBytes)}",
                        )
                    }
                    items(duplicateGroups.take(10)) { group ->
                        DuplicateGroupRow(
                            group = group,
                            onRecycleFile = viewModel::recycleFile,
                        )
                    }
                }

                // Unclassified
                summary?.let { s ->
                    if (s.unclassifiedBytes > 0) {
                        item {
                            SectionHeader("Unclassified", "Android doesn't expose details")
                        }
                        item { UnclassifiedCard(s.unclassifiedBytes) }
                    }
                }

                // What grew today
                if (deltas.isNotEmpty()) {
                    item { SectionHeader("What grew today?", "Since last scan") }
                    items(deltas) { delta ->
                        DeltaRow(delta)
                    }
                }

                // Large files
                if (largeFiles.isNotEmpty()) {
                    item { SectionHeader("Large Files", "Over 100 MB") }
                    items(largeFiles.take(10)) { file ->
                        LargeFileRow(
                            file = file,
                            onRecycleFile = viewModel::recycleFile,
                        )
                    }
                }

                // Error
                errorMessage?.let {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    it,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                TextButton(onClick = { viewModel.clearError() }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// COMPONENTS
// ─────────────────────────────────────────────

@Composable
fun TotalStorageCard(summary: StorageSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Total Storage",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { summary.usedPercentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                color = when {
                    summary.usedPercentage > 90 -> Color.Red
                    summary.usedPercentage > 75 -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatBytes(summary.usedBytes), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "${String.format(Locale.getDefault(), "%.1f", summary.usedPercentage)}% used",
                    fontSize = 14.sp,
                )
            }
            Text(
                "${formatBytes(summary.freeBytes)} free of ${formatBytes(summary.totalBytes)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            CertaintyRow("Identified", summary.identifiedBytes, Color(0xFF4CAF50))
            CertaintyRow("Attributed", summary.attributedBytes, Color(0xFF2196F3))
            CertaintyRow("Unclassified", summary.unclassifiedBytes, Color(0xFF9E9E9E))
        }
    }
}

// ─────────────────────────────────────────────
// SMART SUGGESTIONS COMPONENT
// ─────────────────────────────────────────────

@Composable
fun SmartSuggestionsCard(
    suggestions: List<SmartSuggestion>,
    onAction: (SuggestionAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Smart Storage Recommendations",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = "AI-powered cleanup suggestions",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = suggestion.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            SuggestionChip(
                                onClick = { },
                                label = {
                                    Text(
                                        text = suggestion.priority.name,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (suggestion.priority == SuggestionPriority.HIGH) Color(0xFFFFEBEE) else Color(0xFFE3F2FD),
                                    labelColor = if (suggestion.priority == SuggestionPriority.HIGH) Color(0xFFC62828) else Color(0xFF1565C0),
                                ),
                            )
                        }
                        Text(
                            text = suggestion.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { onAction(suggestion.actionType) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(suggestion.actionText, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// APP MANAGER HUB COMPONENT
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerHubCard(
    apps: List<AppStorageInfo>,
    currentSort: AppSortOption,
    onSortSelected: (AppSortOption) -> Unit,
    onOpenAppSettings: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(value = false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "App Manager Hub (${apps.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = "Analyze app data, caches & system settings",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle App Manager Hub",
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))

                // Sorting selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Sort by:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = currentSort == AppSortOption.TOTAL_SIZE,
                            onClick = { onSortSelected(AppSortOption.TOTAL_SIZE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        ) {
                            Text("Total", fontSize = 10.sp)
                        }
                        SegmentedButton(
                            selected = currentSort == AppSortOption.CACHE_SIZE,
                            onClick = { onSortSelected(AppSortOption.CACHE_SIZE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        ) {
                            Text("Cache", fontSize = 10.sp)
                        }
                        SegmentedButton(
                            selected = currentSort == AppSortOption.DATA_SIZE,
                            onClick = { onSortSelected(AppSortOption.DATA_SIZE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        ) {
                            Text("Data", fontSize = 10.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                apps.take(15).forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Data: ${formatBytes(app.dataBytes)} • Cache: ${formatBytes(app.cacheBytes)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatBytes(app.totalBytes),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { onOpenAppSettings(app.packageName) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Launch,
                                contentDescription = "Manage app settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HighStorageAlertCard(
    usedPercentage: Float,
    onCleanJunkClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFE65100),
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "High Storage Alert (${String.format(Locale.getDefault(), "%.1f", usedPercentage)}% Used)",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100),
                    fontSize = 14.sp,
                )
                Text(
                    text = "Storage space is critically low. Clean junk or remove large files to free up space.",
                    fontSize = 11.sp,
                    color = Color(0xFFBF360C),
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onCleanJunkClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Clean Junk", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun JunkCleanerCard(
    junkResult: JunkScanResult,
    onCleanJunk: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Junk & Cache Cleaner",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = if (junkResult.totalSizeBytes > 0) {
                                "${formatBytes(junkResult.totalSizeBytes)} cleanable junk found"
                            } else {
                                "Storage is clean and optimized"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (junkResult.totalSizeBytes > 0) {
                    Button(onClick = onCleanJunk) {
                        Text("Clean")
                    }
                }
            }

            if (junkResult.items.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                val categories = junkResult.items.groupBy { it.category }
                categories.forEach { (catName, items) ->
                    val groupSize = items.sumOf { it.sizeBytes }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(catName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatBytes(groupSize), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

val StorageSnapshot.usedPercentage: Float
    get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes) * 100 else 0f

@Composable
fun StorageHistoryCard(snapshots: List<StorageSnapshot>) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    tint = primaryColor,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Storage History Trend",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = "Tracking storage usage over time",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (snapshots.size > 1) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    val width = size.width
                    val height = size.height
                    val count = snapshots.size

                    val maxPct = (snapshots.maxOfOrNull { it.usedPercentage } ?: 100f).coerceAtLeast(10f)
                    val minPct = (snapshots.minOfOrNull { it.usedPercentage } ?: 0f).coerceAtLeast(0f)
                    val pctRange = (maxPct - minPct).coerceAtLeast(5f)

                    val points = snapshots.mapIndexed { index, snapshot ->
                        val x = if (count > 1) (index.toFloat() / (count - 1)) * width else width / 2f
                        val y = height - (((snapshot.usedPercentage - minPct) / pctRange) * height)
                        Offset(x, y)
                    }

                    // Fill Path
                    val fillPath = Path().apply {
                        moveTo(points.first().x, height)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.35f), primaryColor.copy(alpha = 0.05f)),
                        ),
                    )

                    // Line Path
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path = linePath,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // Points
                    points.forEach { point ->
                        drawCircle(
                            color = primaryColor,
                            radius = 4.dp.toPx(),
                            center = point,
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = point,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${snapshots.size} historical snapshots",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Current: ${String.format(Locale.getDefault(), "%.1f", snapshots.last().usedPercentage)}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                    )
                }
            } else {
                Text(
                    text = "Run more scans over time to generate historical trend charts.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// RECYCLE BIN COMPONENT
// ─────────────────────────────────────────────

@Composable
fun RecycleBinCard(
    recycledFiles: List<RecycledFileEntity>,
    onRestore: (RecycledFileEntity) -> Unit,
    onDeletePermanently: (RecycledFileEntity) -> Unit,
    onEmptyBin: () -> Unit,
) {
    var expanded by remember { mutableStateOf(value = false) }
    val totalBytes = recycledFiles.sumOf { it.sizeBytes }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Recycle Bin (${recycledFiles.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = "${formatBytes(totalBytes)} stored in bin",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onEmptyBin) {
                        Text("Empty Bin", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle details",
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                recycledFiles.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.fileName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Original: ${item.originalPath}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatBytes(item.sizeBytes),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        IconButton(onClick = { onRestore(item) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Restore,
                                contentDescription = "Restore file",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        IconButton(onClick = { onDeletePermanently(item) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = "Delete permanently",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// DUPLICATE FILES COMPONENT
// ─────────────────────────────────────────────

@Composable
fun DuplicateGroupRow(
    group: DuplicateFileGroup,
    onRecycleFile: (File) -> Unit,
) {
    var expanded by remember { mutableStateOf(value = false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.files.firstOrNull()?.name ?: "Duplicate Files",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${group.files.size} copies • ${formatBytes(group.sizeBytes)} each • Wasting ${formatBytes(group.wastedBytes)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle details",
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                group.files.forEachIndexed { index, file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SuggestionChip(
                                    onClick = { },
                                    label = {
                                        Text(
                                            if (index == 0) "Original" else "Duplicate",
                                            fontSize = 10.sp,
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (index == 0) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                        labelColor = if (index == 0) Color(0xFF2E7D32) else Color(0xFFE65100),
                                    ),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = file.parent ?: "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (index > 0) {
                            IconButton(
                                onClick = { onRecycleFile(file) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Move to Recycle Bin",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// TREEMAP VISUALIZATION
// ─────────────────────────────────────────────

data class TreemapNode(
    val id: String,
    val label: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val color: Color,
)

data class TreemapRect(
    val node: TreemapNode,
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
)

enum class TreemapMode {
    CATEGORIES,
    DETAILED,
}

fun layoutTreemap(
    nodes: List<TreemapNode>,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
): List<TreemapRect> {
    if (nodes.isEmpty() || (width <= 0.dp) || (height <= 0.dp)) return emptyList()
    if (nodes.size == 1) {
        return listOf(TreemapRect(nodes[0], x, y, width, height))
    }

    val totalSize = nodes.sumOf { it.sizeBytes }
    if (totalSize <= 0) return emptyList()

    var bestSplit = 1
    var bestDiff = Long.MAX_VALUE
    var currentSum = 0L

    for (i in 0 until (nodes.size - 1)) {
        currentSum += nodes[i].sizeBytes
        val diff = kotlin.math.abs((2 * currentSum) - totalSize)
        if (diff < bestDiff) {
            bestDiff = diff
            bestSplit = i + 1
        }
    }

    val leftNodes = nodes.subList(0, bestSplit)
    val rightNodes = nodes.subList(bestSplit, nodes.size)

    val leftSize = leftNodes.sumOf { it.sizeBytes }
    val ratio = leftSize.toFloat() / totalSize.toFloat()

    val result = mutableListOf<TreemapRect>()

    if (width >= height) {
        val wLeft = width * ratio
        val wRight = width - wLeft
        result.addAll(layoutTreemap(leftNodes, x, y, wLeft, height))
        result.addAll(layoutTreemap(rightNodes, x + wLeft, y, wRight, height))
    } else {
        val hTop = height * ratio
        val hBottom = height - hTop
        result.addAll(layoutTreemap(leftNodes, x, y, width, hTop))
        result.addAll(layoutTreemap(rightNodes, x, y + hTop, width, hBottom))
    }

    return result
}

fun buildTreemapNodes(
    summary: StorageSummary,
    categories: List<StorageCategory>,
    apps: List<AppStorageInfo>,
    mode: TreemapMode,
): List<TreemapNode> {
    val nodes = mutableListOf<TreemapNode>()

    val categoryColors = mapOf(
        CategoryType.PHOTOS to Color(0xFF4CAF50),    // Green
        CategoryType.VIDEOS to Color(0xFF2196F3),    // Blue
        CategoryType.AUDIO to Color(0xFF9C27B0),     // Purple
        CategoryType.DOCUMENTS to Color(0xFFFF9800), // Orange
        CategoryType.DOWNLOADS to Color(0xFF00BCD4), // Cyan
        CategoryType.OTHER to Color(0xFF795548),     // Brown
    )

    categories.forEach { cat ->
        if (cat.sizeBytes > 0) {
            nodes.add(
                TreemapNode(
                    id = cat.name,
                    label = cat.name,
                    sizeBytes = cat.sizeBytes,
                    formattedSize = formatBytes(cat.sizeBytes),
                    color = categoryColors[cat.type] ?: Color(0xFF607D8B),
                ),
            )
        }
    }

    if (mode == TreemapMode.CATEGORIES) {
        val appsTotal = apps.sumOf { it.totalBytes }
        if (appsTotal > 0) {
            nodes.add(
                TreemapNode(
                    id = "Apps",
                    label = "Apps",
                    sizeBytes = appsTotal,
                    formattedSize = formatBytes(appsTotal),
                    color = Color(0xFFE91E63), // Pink
                ),
            )
        }
    } else {
        // Detailed mode: show top apps individually
        val topApps = apps.take(5)
        val appColors = listOf(
            Color(0xFFE91E63), // Pink
            Color(0xFFD81B60), // Dark Pink
            Color(0xFFC2185B), // Deep Pink
            Color(0xFF8E24AA), // Purple
            Color(0xFF3949AB), // Indigo
        )

        topApps.forEachIndexed { index, app ->
            if (app.totalBytes > 0) {
                nodes.add(
                    TreemapNode(
                        id = app.packageName,
                        label = app.appName,
                        sizeBytes = app.totalBytes,
                        formattedSize = formatBytes(app.totalBytes),
                        color = appColors[index % appColors.size],
                    ),
                )
            }
        }

        val otherAppsBytes = apps.asSequence().drop(5).sumOf { it.totalBytes }
        if (otherAppsBytes > 0) {
            nodes.add(
                TreemapNode(
                    id = "OtherApps",
                    label = "Other Apps",
                    sizeBytes = otherAppsBytes,
                    formattedSize = formatBytes(otherAppsBytes),
                    color = Color(0xFFAB47BC), // Light Purple
                ),
            )
        }
    }

    if (summary.unclassifiedBytes > 0) {
        nodes.add(
            TreemapNode(
                id = "Unclassified",
                label = "Unclassified",
                sizeBytes = summary.unclassifiedBytes,
                formattedSize = formatBytes(summary.unclassifiedBytes),
                color = Color(0xFF78909C), // Slate Grey
            ),
        )
    }

    return nodes.sortedByDescending { it.sizeBytes }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StorageTreemapCard(
    summary: StorageSummary,
    categories: List<StorageCategory>,
    apps: List<AppStorageInfo>,
) {
    var selectedNode by remember { mutableStateOf<TreemapNode?>(null) }
    var mode by remember { mutableStateOf(TreemapMode.CATEGORIES) }

    val nodes = remember(summary, categories, apps, mode) {
        buildTreemapNodes(summary, categories, apps, mode)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Storage Treemap",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )

                // Mode Selector
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = mode == TreemapMode.CATEGORIES,
                        onClick = {
                            mode = TreemapMode.CATEGORIES
                            selectedNode = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text("Category", fontSize = 11.sp)
                    }
                    SegmentedButton(
                        selected = mode == TreemapMode.DETAILED,
                        onClick = {
                            mode = TreemapMode.DETAILED
                            selectedNode = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text("Detailed", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val subtitleText = if (selectedNode != null) {
                val percentage = if (summary.usedBytes > 0) {
                    (selectedNode!!.sizeBytes.toFloat() / summary.usedBytes.toFloat()) * 100
                } else 0f
                "${selectedNode!!.label}: ${selectedNode!!.formattedSize} (${String.format(Locale.getDefault(), "%.1f", percentage)}% of used space)"
            } else {
                "Proportional storage layout. Tap any block for details."
            }

            Text(
                text = subtitleText,
                fontSize = 12.sp,
                color = if (selectedNode != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selectedNode != null) FontWeight.Bold else FontWeight.Normal,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Treemap Container
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val widthDp = this.maxWidth
                val heightDp = this.maxHeight

                val rects = remember(nodes, widthDp, heightDp) {
                    if ((widthDp > 0.dp) && (heightDp > 0.dp) && nodes.isNotEmpty()) {
                        layoutTreemap(nodes, 0.dp, 0.dp, widthDp, heightDp)
                    } else {
                        emptyList()
                    }
                }

                rects.forEach { rect ->
                    val isSelected = selectedNode == rect.node

                    Box(
                        modifier = Modifier
                            .offset(x = rect.x, y = rect.y)
                            .size(width = rect.width, height = rect.height)
                            .padding(1.5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) rect.node.color
                                else rect.node.color.copy(alpha = 0.85f),
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .clickable { selectedNode = if (isSelected) null else rect.node }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if ((rect.width >= 44.dp) && (rect.height >= 28.dp)) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = rect.node.label,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if ((rect.width < 75.dp) || (rect.height < 45.dp)) 10.sp else 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (rect.height >= 44.dp) {
                                    Text(
                                        text = rect.node.formattedSize,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = if ((rect.width < 75.dp) || (rect.height < 45.dp)) 9.sp else 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend items
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                nodes.forEach { node ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable {
                            selectedNode = if (selectedNode == node) null else node
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(node.color, RoundedCornerShape(2.dp)),
                        )
                        Text(
                            text = node.label,
                            fontSize = 11.sp,
                            fontWeight = if (selectedNode == node) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CertaintyRow(label: String, bytes: Long, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(formatBytes(bytes), fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CategoryRow(category: StorageCategory) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = iconForCategory(category.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(category.name, fontWeight = FontWeight.Medium)
                Text(
                    "${category.fileCount} files",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatBytes(category.sizeBytes), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AppRow(app: AppStorageInfo) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.appName, fontWeight = FontWeight.Medium)
                Text(
                    "Data: ${formatBytes(app.dataBytes)} • Cache: ${formatBytes(app.cacheBytes)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatBytes(app.totalBytes), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun UnclassifiedCard(bytes: Long) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = Color.Gray)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Other / Unclassified", fontWeight = FontWeight.Medium)
                Text(
                    "System data, app internals, or files Android can't identify",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatBytes(bytes), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DeltaRow(delta: StorageDelta) {
    val isPositive = delta.deltaBytes > 0
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPositive) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = null,
                tint = if (isPositive) Color.Red else Color(0xFF4CAF50),
            )
            Spacer(Modifier.width(12.dp))
            Text(delta.categoryName, modifier = Modifier.weight(1f))
            Text(
                (if (isPositive) "+" else "") + formatBytes(delta.deltaBytes),
                fontWeight = FontWeight.Bold,
                color = if (isPositive) Color.Red else Color(0xFF4CAF50),
            )
        }
    }
}

@Composable
fun LargeFileRow(
    file: File,
    onRecycleFile: (File) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    file.parent ?: "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(formatBytes(file.length()), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { onRecycleFile(file) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Move to Recycle Bin",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────

fun iconForCategory(type: CategoryType) = when (type) {
    CategoryType.PHOTOS -> Icons.Default.Photo
    CategoryType.VIDEOS -> Icons.Default.Videocam
    CategoryType.AUDIO -> Icons.Default.MusicNote
    CategoryType.DOCUMENTS -> Icons.Default.Description
    CategoryType.DOWNLOADS -> Icons.Default.Download
    CategoryType.APPS -> Icons.Default.Android
    CategoryType.TRASH -> Icons.Default.Delete
    CategoryType.OTHER -> Icons.Default.Folder
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.2f GB", gb)
}
