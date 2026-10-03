package com.yourapp.storageapp.storage

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.storageapp.storage.data.StorageDatabase
import com.yourapp.storageapp.storage.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = StorageScanner(application)
    private val db = StorageDatabase.getInstance(application)
    private val recycleBinDir = File(
        application.getExternalFilesDir(null) ?: application.filesDir,
        "recycle_bin",
    )

    private val _isScanning = MutableStateFlow(value = false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _summary = MutableStateFlow<StorageSummary?>(null)
    val summary: StateFlow<StorageSummary?> = _summary.asStateFlow()

    private val _categories = MutableStateFlow<List<StorageCategory>>(emptyList())
    val categories: StateFlow<List<StorageCategory>> = _categories.asStateFlow()

    private val _apps = MutableStateFlow<List<AppStorageInfo>>(emptyList())
    val apps: StateFlow<List<AppStorageInfo>> = _apps.asStateFlow()

    private val _appSortOption = MutableStateFlow(AppSortOption.TOTAL_SIZE)
    val appSortOption: StateFlow<AppSortOption> = _appSortOption.asStateFlow()

    val sortedApps: StateFlow<List<AppStorageInfo>> = combine(_apps, _appSortOption) { appList, option ->
        when (option) {
            AppSortOption.TOTAL_SIZE -> appList.sortedByDescending { it.totalBytes }
            AppSortOption.CACHE_SIZE -> appList.sortedByDescending { it.cacheBytes }
            AppSortOption.DATA_SIZE -> appList.sortedByDescending { it.dataBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _largeFiles = MutableStateFlow<List<File>>(emptyList())
    val largeFiles: StateFlow<List<File>> = _largeFiles.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<DuplicateFileGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateFileGroup>> = _duplicateGroups.asStateFlow()

    private val _recycledFiles = MutableStateFlow<List<RecycledFileEntity>>(emptyList())
    val recycledFiles: StateFlow<List<RecycledFileEntity>> = _recycledFiles.asStateFlow()

    private val _junkResult = MutableStateFlow<JunkScanResult?>(null)
    val junkResult: StateFlow<JunkScanResult?> = _junkResult.asStateFlow()

    private val _smartSuggestions = MutableStateFlow<List<SmartSuggestion>>(emptyList())
    val smartSuggestions: StateFlow<List<SmartSuggestion>> = _smartSuggestions.asStateFlow()

    private val _historySnapshots = MutableStateFlow<List<StorageSnapshot>>(emptyList())
    val historySnapshots: StateFlow<List<StorageSnapshot>> = _historySnapshots.asStateFlow()

    private val _deltas = MutableStateFlow<List<StorageDelta>>(emptyList())
    val deltas: StateFlow<List<StorageDelta>> = _deltas.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        runFullScan()
        loadRecycledFiles()
    }

    fun runFullScan() {
        viewModelScope.launch {
            try {
                _isScanning.value = true
                _errorMessage.value = null

                val result = scanner.fullScan()
                _summary.value = result.summary
                _categories.value = result.categories
                _apps.value = result.apps
                _duplicateGroups.value = result.duplicateGroups
                _junkResult.value = result.junkResult

                saveSnapshot(result)
                calculateDeltas()
                loadHistorySnapshots()

                val foundLarge = scanner.findLargeFiles(100)
                _largeFiles.value = foundLarge

                _smartSuggestions.value = generateSmartSuggestions(result, foundLarge)

                loadRecycledFiles()

                // Check and trigger system alerts & notifications
                StorageNotificationHelper.checkAndSendAlerts(
                    context = getApplication(),
                    usedPercentage = result.summary.usedPercentage,
                    junkBytes = result.junkResult?.totalSizeBytes ?: 0L,
                )

            } catch (e: Exception) {
                _errorMessage.value = "Scan failed: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun setAppSortOption(option: AppSortOption) {
        _appSortOption.value = option
    }

    fun openAppSettings(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            _errorMessage.value = "Could not open app settings: ${e.message}"
        }
    }

    fun cleanJunkFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val items = _junkResult.value?.items ?: emptyList()
                items.forEach { item ->
                    if (item.file.exists()) {
                        item.file.delete()
                    }
                }
                // Refresh scan
                runFullScan()
            } catch (e: Exception) {
                _errorMessage.value = "Junk clean error: ${e.message}"
            }
        }
    }

    fun loadRecycledFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _recycledFiles.value = db.storageDao().getRecycledFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load recycle bin: ${e.message}"
            }
        }
    }

    private fun loadHistorySnapshots() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _historySnapshots.value = db.storageDao().getRecentSnapshots().reversed()
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    fun recycleFile(fileToRecycle: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!recycleBinDir.exists()) {
                    recycleBinDir.mkdirs()
                }
                val destFile = File(recycleBinDir, "${System.currentTimeMillis()}_${fileToRecycle.name}")
                val fileSize = fileToRecycle.length()

                if (moveFileHelper(fileToRecycle, destFile)) {
                    val entity = RecycledFileEntity(
                        originalPath = fileToRecycle.absolutePath,
                        recycledPath = destFile.absolutePath,
                        fileName = fileToRecycle.name,
                        sizeBytes = fileSize,
                        recycledTimestamp = System.currentTimeMillis(),
                    )
                    db.storageDao().insertRecycledFile(entity)

                    // Update UI state
                    _duplicateGroups.value = _duplicateGroups.value.mapNotNull { group ->
                        val remainingFiles = group.files.filter { it.absolutePath != fileToRecycle.absolutePath }
                        if (remainingFiles.size > 1) {
                            group.copy(files = remainingFiles)
                        } else {
                            null
                        }
                    }
                    _largeFiles.value = _largeFiles.value.filter { it.absolutePath != fileToRecycle.absolutePath }

                    loadRecycledFiles()
                } else {
                    _errorMessage.value = "Unable to recycle ${fileToRecycle.name}."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to move file to Recycle Bin: ${e.message}"
            }
        }
    }

    fun restoreRecycledFile(item: RecycledFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = File(item.recycledPath)
                val target = File(item.originalPath)

                if (moveFileHelper(source, target)) {
                    db.storageDao().deleteRecycledFile(item.id)
                    loadRecycledFiles()
                } else {
                    _errorMessage.value = "Unable to restore ${item.fileName} to ${item.originalPath}."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to restore file: ${e.message}"
            }
        }
    }

    fun permanentlyDeleteRecycledFile(item: RecycledFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = File(item.recycledPath)
                if (source.exists()) {
                    source.delete()
                }
                db.storageDao().deleteRecycledFile(item.id)
                loadRecycledFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to permanently delete file: ${e.message}"
            }
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recycleBinDir.listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
                db.storageDao().clearRecycleBin()
                loadRecycledFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to empty Recycle Bin: ${e.message}"
            }
        }
    }

    private fun generateSmartSuggestions(
        result: FullScanResult,
        largeFiles: List<File>,
    ): List<SmartSuggestion> {
        val list = mutableListOf<SmartSuggestion>()

        val junkBytes = result.junkResult?.totalSizeBytes ?: 0L
        if (junkBytes > (10 * 1024 * 1024)) {
            list.add(
                SmartSuggestion(
                    id = "junk",
                    title = "Clean Temporary Junk",
                    description = "Found ${formatBytes(junkBytes)} of disposable cache and temporary files.",
                    priority = SuggestionPriority.HIGH,
                    actionText = "Clean Junk",
                    actionType = SuggestionAction.CLEAN_JUNK,
                ),
            )
        }

        if (result.duplicateGroups.isNotEmpty()) {
            val wasted = result.duplicateGroups.sumOf { it.wastedBytes }
            list.add(
                SmartSuggestion(
                    id = "duplicates",
                    title = "Remove Duplicate Files",
                    description = "You have ${result.duplicateGroups.size} duplicate sets wasting ${formatBytes(wasted)}.",
                    priority = SuggestionPriority.HIGH,
                    actionText = "Recycle Duplicates",
                    actionType = SuggestionAction.RECYCLE_DUPLICATES,
                ),
            )
        }

        if (largeFiles.isNotEmpty()) {
            val totalLarge = largeFiles.sumOf { it.length() }
            list.add(
                SmartSuggestion(
                    id = "large_files",
                    title = "Review Heavy Large Files",
                    description = "${largeFiles.size} large files (>100MB) consume ${formatBytes(totalLarge)}.",
                    priority = SuggestionPriority.MEDIUM,
                    actionText = "View Files",
                    actionType = SuggestionAction.VIEW_LARGE_FILES,
                ),
            )
        }

        if (result.apps.isNotEmpty()) {
            val heavyCount = result.apps.count { it.totalBytes > (100 * 1024 * 1024) }
            if (heavyCount > 0) {
                list.add(
                    SmartSuggestion(
                        id = "apps",
                        title = "Manage Heavy Applications",
                        description = "$heavyCount applications consume over 100MB each.",
                        priority = SuggestionPriority.MEDIUM,
                        actionText = "Open App Hub",
                        actionType = SuggestionAction.OPEN_APP_HUB,
                    ),
                )
            }
        }

        return list.sortedBy { it.priority }
    }

    private fun moveFileHelper(src: File, dest: File): Boolean {
        if (!src.exists()) return false
        dest.parentFile?.mkdirs()
        if (src.renameTo(dest)) return true
        return try {
            src.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            src.delete()
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun saveSnapshot(result: FullScanResult) {
        val snapshot = StorageSnapshot(
            timestamp = result.timestamp,
            totalBytes = result.summary.totalBytes,
            usedBytes = result.summary.usedBytes,
            photosBytes = result.categories.find { it.type == CategoryType.PHOTOS }?.sizeBytes ?: 0,
            videosBytes = result.categories.find { it.type == CategoryType.VIDEOS }?.sizeBytes ?: 0,
            audioBytes = result.categories.find { it.type == CategoryType.AUDIO }?.sizeBytes ?: 0,
            documentsBytes = result.categories.find { it.type == CategoryType.DOCUMENTS }?.sizeBytes ?: 0,
            downloadsBytes = result.categories.find { it.type == CategoryType.DOWNLOADS }?.sizeBytes ?: 0,
            appsBytes = result.apps.sumOf { it.totalBytes },
            otherBytes = result.summary.unclassifiedBytes,
        )
        db.storageDao().insertSnapshot(snapshot)
    }

    private suspend fun calculateDeltas() {
        val latest = db.storageDao().getLatestSnapshot() ?: return
        val previous = db.storageDao().getSnapshotBefore(latest.timestamp) ?: return
        _deltas.value = DeltaCalculator.calculate(previous, latest)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
