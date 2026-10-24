package dev.studios.samimine.app.di

import android.content.Context
import androidx.room.Room
import dev.studios.samimine.application.usecase.EvaluationUseCases
import dev.studios.samimine.core.time.AndroidMonotonicClock
import dev.studios.samimine.data.local.database.SamiMineDatabase
import dev.studios.samimine.data.repository.RoomEvaluationRepository
import dev.studios.samimine.data.local.database.SamiMineDatabase.Companion.MIGRATION_1_2
import dev.studios.samimine.data.local.database.SamiMineDatabase.Companion.MIGRATION_2_3
import dev.studios.samimine.data.local.database.SamiMineDatabase.Companion.MIGRATION_3_4
import dev.studios.samimine.data.local.database.SamiMineDatabase.Companion.MIGRATION_4_5
import dev.studios.samimine.data.local.database.SamiMineDatabase.Companion.MIGRATION_5_6
import dev.studios.samimine.data.remote.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.security.MessageDigest

/**
 * Inyección manual: comparte una base local y un repositorio para PVT y pruebas cognitivas.
 * Usa el contexto de aplicación para no retener una Activity y registra todas las migraciones.
 */
class AppContainer(private val context: Context) {
    private val database = Room.databaseBuilder(context.applicationContext, SamiMineDatabase::class.java, "samimine-demo.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
    val repository = RoomEvaluationRepository(database)
    val useCases = EvaluationUseCases(repository)
    val clock = AndroidMonotonicClock()
    val firebaseAuth by lazy { FirebaseAuth.getInstance(PilotEnvironment.firebaseApp ?: com.google.firebase.FirebaseApp.getInstance()) }
    val firestore by lazy {
        FirebaseFirestore.getInstance(PilotEnvironment.firebaseApp ?: com.google.firebase.FirebaseApp.getInstance()).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
    }
    val auth by lazy { FirebaseAuthRepository(firebaseAuth) }
    val users by lazy { FirebaseUserRepository(firestore) }
    val sessionCache by lazy { WorkerSessionCache(context) }
    private val userDatabases = mutableMapOf<String, SamiMineDatabase>()
    @Synchronized fun database(uid: String): SamiMineDatabase = userDatabases.getOrPut(uid) {
        val hash = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        Room.databaseBuilder(context.applicationContext, SamiMineDatabase::class.java, "samimine-user-$hash.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
    }
    fun sync(uid: String) = FirebaseSyncRepository(context.applicationContext, database(uid), firebaseAuth, firestore)
}
