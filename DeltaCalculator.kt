package com.yourapp.storageapp.storage

import com.yourapp.storageapp.storage.models.StorageDelta
import com.yourapp.storageapp.storage.models.StorageSnapshot
import kotlin.math.abs

object DeltaCalculator {

    fun calculate(older: StorageSnapshot, newer: StorageSnapshot): List<StorageDelta> {
        val deltas = mutableListOf<StorageDelta>()

        deltas.add(StorageDelta("Photos", newer.photosBytes - older.photosBytes))
        deltas.add(StorageDelta("Videos", newer.videosBytes - older.videosBytes))
        deltas.add(StorageDelta("Audio", newer.audioBytes - older.audioBytes))
        deltas.add(StorageDelta("Documents", newer.documentsBytes - older.documentsBytes))
        deltas.add(StorageDelta("Downloads", newer.downloadsBytes - older.downloadsBytes))
        deltas.add(StorageDelta("Apps", newer.appsBytes - older.appsBytes))
        deltas.add(StorageDelta("Other", newer.otherBytes - older.otherBytes))

        return deltas
            .asSequence()
            .filter { it.deltaBytes != 0L }
            .sortedByDescending { abs(it.deltaBytes) }
            .toList()
    }

    @Suppress("unused")
    fun totalGrowth(deltas: List<StorageDelta>): Long =
        deltas.sumOf { it.deltaBytes }
}
