package com.example.samimine.app.di

import android.content.Context
import androidx.room.Room
import com.example.samimine.application.usecase.EvaluationUseCases
import com.example.samimine.core.time.AndroidMonotonicClock
import com.example.samimine.data.local.database.SamiMineDatabase
import com.example.samimine.data.repository.RoomEvaluationRepository
import com.example.samimine.data.local.database.SamiMineDatabase.Companion.MIGRATION_1_2
import com.example.samimine.data.local.database.SamiMineDatabase.Companion.MIGRATION_2_3

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(context.applicationContext, SamiMineDatabase::class.java, "samimine-demo.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    val repository = RoomEvaluationRepository(database)
    val useCases = EvaluationUseCases(repository)
    val clock = AndroidMonotonicClock()
}
