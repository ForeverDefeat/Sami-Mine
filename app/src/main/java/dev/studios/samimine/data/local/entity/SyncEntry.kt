package dev.studios.samimine.data.local.entity

import androidx.room.Entity

@Entity(tableName = "sync_queue", primaryKeys = ["kind", "runId"])
data class SyncEntry(
    val kind: String, val runId: String, val workerId: String,
    val state: String = "PENDING", val attempts: Int = 0, val error: String? = null,
)
