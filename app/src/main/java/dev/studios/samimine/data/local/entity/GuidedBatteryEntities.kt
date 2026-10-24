package dev.studios.samimine.data.local.entity

import androidx.room.*
import com.google.gson.Gson
import dev.studios.samimine.domain.model.*

@Entity(tableName = "battery_runs", indices = [Index("workerId")])
data class GuidedBatteryEntity(@PrimaryKey val id: String, val workerId: String, val status: String,
    val startedAtMillis: Long, val snapshot: String) {
    fun toDomain(): GuidedBatteryRun = Gson().fromJson(snapshot, GuidedBatteryRun::class.java)
}
fun GuidedBatteryRun.toEntity() = GuidedBatteryEntity(id, workerId, status.name, startedAtMillis, Gson().toJson(this))

@Entity(tableName = "battery_config_cache")
data class BatteryConfigCache(@PrimaryKey val organizationId: String, val snapshot: String)
