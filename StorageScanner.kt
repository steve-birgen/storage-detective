package com.yourapp.storageapp.storage

import android.annotation.SuppressLint
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.yourapp.storageapp.storage.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class StorageScanner(private val context: Context) {

    // 1. TOTAL / FREE STORAGE
    suspend fun getStorageSummary(): StorageSummary = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val used = total - free

        StorageSummary(
            totalBytes = total,
            usedBytes = used,
            freeBytes = free,
            identifiedBytes = 0L,
            attributedBytes = 0L,
            unclassifiedBytes = 0L,
        )
    }

    // 2. MEDIA CATEGORIES
    suspend fun scanMediaCategories(): List<StorageCategory> = withContext(Dispatchers.IO) {
        val list = mutableListOf<StorageCategory>()

        list.add(
            scanMediaType(
                "Photos",
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE,
                CategoryType.PHOTOS,
            ),
        )
        list.add(
            scanMediaType(
                "Videos",
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                CategoryType.VIDEOS,
            ),
        )
        list.add(
            scanMediaType(
                "Audio",
                MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO,
                CategoryType.AUDIO,
            ),
        )
        list.add(scanDocuments())
        list.add(scanDownloads())

        list
    }

    private fun scanMediaType(
        name: String,
        mediaType: Int,
        type: CategoryType,
    ): StorageCategory {
        var totalSize = 0L
        var count = 0

        val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(mediaType.toString())

        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                while (cursor.moveToNext()) {
                    totalSize += cursor.getLong(sizeCol)
                    count++
                }
            }
        } catch (_: Exception) {
            // Permission denied or no media
        }

        return StorageCategory(
            name = name,
            sizeBytes = totalSize,
            fileCount = count,
            type = type,
        )
    }

    private fun scanDocuments(): StorageCategory {
        var totalSize = 0L
        var count = 0

        val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?"
        val selectionArgs = arrayOf(
            "application/pdf%",
            "application/msword%",
            "application/vnd.openxmlformats%",
        )

        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                while (cursor.moveToNext()) {
                    totalSize += cursor.getLong(sizeCol)
                    count++
                }
            }
        } catch (_: Exception) {
            // Skip
        }

        return StorageCategory(
            name = "Documents",
            sizeBytes = totalSize,
            fileCount = count,
            type = CategoryType.DOCUMENTS,
        )
    }

    private fun scanDownloads(): StorageCategory {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        )
        var totalSize = 0L
        var count = 0

        try {
            downloadsDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    totalSize += file.length()
                    count++
                }
            }
        } catch (_: Exception) {
            // Skip
        }

        return StorageCategory(
            name = "Downloads",
            sizeBytes = totalSize,
            fileCount = count,
            type = CategoryType.DOWNLOADS,
        )
    }

    // 3. APP STORAGE (requires PACKAGE_USAGE_STATS permission)
    @SuppressLint("QueryPermissionsNeeded")
    suspend fun scanAppStorage(): List<AppStorageInfo> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext emptyList()

        val statsManager = context.getSystemService(StorageStatsManager::class.java)
            ?: return@withContext emptyList()
        val storageManager = context.getSystemService(StorageManager::class.java)
        val pm = context.packageManager

        val apps = mutableListOf<AppStorageInfo>()

        val installedPackages = pm.getInstalledPackages(0)

        for (pkg in installedPackages) {
            try {
                val uuid = storageManager.getUuidForPath(Environment.getDataDirectory())
                val stats = statsManager.queryStatsForPackage(
                    uuid,
                    pkg.packageName,
                    android.os.Process.myUserHandle(),
                )

                val dataBytes = stats.dataBytes
                val cacheBytes = stats.cacheBytes
                val total = dataBytes + cacheBytes

                if (total > (10 * 1024 * 1024)) { // Only > 10 MB
                    apps.add(
                        AppStorageInfo(
                            packageName = pkg.packageName,
                            appName = pm.getApplicationLabel(pkg.applicationInfo).toString(),
                            dataBytes = dataBytes,
                            cacheBytes = cacheBytes,
                            totalBytes = total,
                        ),
                    )
                }
            } catch (_: Exception) {
                // Skip packages we can't read
            }
        }

        apps.sortedByDescending { it.totalBytes }
    }

    // 4. LARGE FILES
    suspend fun findLargeFiles(minSizeMb: Long = 100): List<File> = withContext(Dispatchers.IO) {
        val results = mutableListOf<File>()
        val minBytes = minSizeMb * 1024 * 1024

        val root = Environment.getExternalStorageDirectory()
        scanDirectoryForLargeFiles(root, minBytes, results, maxDepth = 5)

        results.sortedByDescending { it.length() }
    }

    private fun scanDirectoryForLargeFiles(
        dir: File,
        minBytes: Long,
        results: MutableList<File>,
        maxDepth: Int,
        currentDepth: Int = 0,
    ) {
        if (currentDepth > maxDepth) return
        if (!dir.canRead()) return

        try {
            dir.listFiles()?.forEach { file ->
                if ((file.isFile) && (file.length() >= minBytes)) {
                    results.add(file)
                } else if (file.isDirectory && !file.name.startsWith(".")) {
                    scanDirectoryForLargeFiles(file, minBytes, results, maxDepth, currentDepth + 1)
                }
            }
        } catch (_: Exception) {
            // Skip unreadable dirs
        }
    }

    // 5. DUPLICATE FILE DETECTION
    suspend fun findDuplicateFiles(
        minSizeBytes: Long = 100 * 1024,
        maxFilesToScan: Int = 1000,
    ): List<DuplicateFileGroup> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val allFiles = mutableListOf<File>()

        collectFilesForDuplicateCheck(root, minSizeBytes, allFiles, maxFilesToScan, maxDepth = 5)

        if (allFiles.isEmpty()) return@withContext emptyList()

        val candidateGroups = allFiles.groupBy { it.length() }
            .filter { (size, fileList) -> (size >= minSizeBytes) && (fileList.size > 1) }

        if (candidateGroups.isEmpty()) return@withContext emptyList()

        val duplicateGroups = mutableListOf<DuplicateFileGroup>()

        for ((size, files) in candidateGroups) {
            val hashGroups = files.groupBy { computeFastHash(it) }
                .filter { (hash, fileList) -> hash.isNotEmpty() && (fileList.size > 1) }

            for ((hash, duplicateFiles) in hashGroups) {
                duplicateGroups.add(
                    DuplicateFileGroup(
                        hash = hash,
                        sizeBytes = size,
                        files = duplicateFiles,
                    ),
                )
            }
        }

        duplicateGroups.sortedByDescending { it.wastedBytes }
    }

    private fun collectFilesForDuplicateCheck(
        dir: File,
        minSizeBytes: Long,
        results: MutableList<File>,
        maxFiles: Int,
        maxDepth: Int,
        currentDepth: Int = 0,
    ) {
        if ((results.size >= maxFiles) || (currentDepth > maxDepth)) return
        if (!dir.canRead()) return

        try {
            dir.listFiles()?.forEach { file ->
                if (results.size >= maxFiles) return@forEach
                if (file.isFile && (file.length() >= minSizeBytes)) {
                    results.add(file)
                } else if (file.isDirectory && !file.name.startsWith(".")) {
                    collectFilesForDuplicateCheck(file, minSizeBytes, results, maxFiles, maxDepth, currentDepth + 1)
                }
            }
        } catch (_: Exception) {
            // Skip unreadable dirs
        }
    }

    private fun computeFastHash(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                var totalRead = 0
                val maxHashBytes = 1024 * 1024
                while ((bytesRead != -1) && (totalRead < maxHashBytes)) {
                    md.update(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    bytesRead = input.read(buffer)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    // 6. JUNK / CACHE SCANNER
    suspend fun scanJunkFiles(): JunkScanResult = withContext(Dispatchers.IO) {
        val junkItems = mutableListOf<JunkFileItem>()

        context.cacheDir?.let { collectCacheFiles(it, "App Cache", junkItems) }
        context.externalCacheDir?.let { collectCacheFiles(it, "External Cache", junkItems) }

        val root = Environment.getExternalStorageDirectory()
        collectTempAndLogFiles(root, junkItems, maxDepth = 5)

        val totalSize = junkItems.sumOf { it.sizeBytes }
        JunkScanResult(
            totalSizeBytes = totalSize,
            items = junkItems,
        )
    }

    private fun collectCacheFiles(dir: File, category: String, results: MutableList<JunkFileItem>) {
        if (!dir.canRead()) return
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    results.add(JunkFileItem(file = file, sizeBytes = file.length(), category = category))
                } else if (file.isDirectory) {
                    collectCacheFiles(file, category, results)
                }
            }
        } catch (_: Exception) {
            // Skip unreadable files
        }
    }

    private fun collectTempAndLogFiles(
        dir: File,
        results: MutableList<JunkFileItem>,
        maxDepth: Int,
        currentDepth: Int = 0,
    ) {
        if ((currentDepth > maxDepth) || !dir.canRead()) return
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val name = file.name.lowercase()
                    if (name.endsWith(".tmp") || name.endsWith(".log") || name.endsWith(".bak") || name.endsWith(".chk")) {
                        results.add(
                            JunkFileItem(
                                file = file,
                                sizeBytes = file.length(),
                                category = if (name.endsWith(".log")) "Log Files" else "Temp Files",
                            ),
                        )
                    }
                } else if (file.isDirectory && !file.name.startsWith(".")) {
                    collectTempAndLogFiles(file, results, maxDepth, currentDepth + 1)
                }
            }
        } catch (_: Exception) {
            // Skip unreadable dirs
        }
    }

    // 7. FULL SCAN
    suspend fun fullScan(): FullScanResult = withContext(Dispatchers.IO) {
        val summary = getStorageSummary()
        val media = scanMediaCategories()
        val apps = scanAppStorage()
        val duplicates = findDuplicateFiles()
        val junk = scanJunkFiles()

        val photos = media.find { it.type == CategoryType.PHOTOS }?.sizeBytes ?: 0
        val videos = media.find { it.type == CategoryType.VIDEOS }?.sizeBytes ?: 0
        val audio = media.find { it.type == CategoryType.AUDIO }?.sizeBytes ?: 0
        val docs = media.find { it.type == CategoryType.DOCUMENTS }?.sizeBytes ?: 0
        val downloads = media.find { it.type == CategoryType.DOWNLOADS }?.sizeBytes ?: 0

        val appsTotal = apps.sumOf { it.totalBytes }

        val identified = photos + videos + audio + docs + downloads
        val unclassified = (summary.usedBytes - identified - appsTotal).coerceAtLeast(0)

        FullScanResult(
            summary = summary.copy(
                identifiedBytes = identified,
                attributedBytes = appsTotal,
                unclassifiedBytes = unclassified,
            ),
            categories = media,
            apps = apps,
            duplicateGroups = duplicates,
            junkResult = junk,
            timestamp = System.currentTimeMillis(),
        )
    }
}
