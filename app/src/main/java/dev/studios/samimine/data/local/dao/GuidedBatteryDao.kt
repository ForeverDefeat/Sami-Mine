package dev.studios.samimine.data.local.dao

import androidx.room.*
import dev.studios.samimine.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GuidedBatteryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(row: GuidedBatteryEntity)
    @Update suspend fun update(row: GuidedBatteryEntity)
    @Query("SELECT * FROM battery_runs WHERE id = :id AND workerId = :uid") suspend fun get(id: String, uid: String): GuidedBatteryEntity?
    @Query("SELECT * FROM battery_runs WHERE workerId = :uid ORDER BY startedAtMillis DESC") suspend fun all(uid: String): List<GuidedBatteryEntity>
    @Query("SELECT * FROM battery_runs WHERE workerId = :uid ORDER BY startedAtMillis DESC") fun observe(uid: String): Flow<List<GuidedBatteryEntity>>
    @Query("SELECT * FROM battery_runs WHERE status IN ('PREPARED', 'IN_PROGRESS', 'PAUSED')") suspend fun unfinished(): List<GuidedBatteryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun cache(row: BatteryConfigCache)
    @Query("SELECT * FROM battery_config_cache WHERE organizationId = :organizationId") suspend fun cached(organizationId: String): BatteryConfigCache?
}
