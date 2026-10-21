package dev.studios.samimine.data.local.dao

import androidx.room.*
import dev.studios.samimine.data.local.entity.SyncEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun enqueue(entry: SyncEntry)
    @Query("SELECT * FROM sync_queue WHERE workerId = :uid ORDER BY kind, runId") fun observe(uid: String): Flow<List<SyncEntry>>
    @Query("SELECT * FROM sync_queue WHERE workerId = :uid AND state != 'SYNCED'") suspend fun pending(uid: String): List<SyncEntry>
    @Query("UPDATE sync_queue SET state = :state, error = :error, attempts = attempts + :increment WHERE kind = :kind AND runId = :id")
    suspend fun update(kind: String, id: String, state: String, error: String? = null, increment: Int = 0)
}
