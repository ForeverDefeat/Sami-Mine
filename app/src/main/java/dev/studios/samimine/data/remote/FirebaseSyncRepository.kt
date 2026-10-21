package dev.studios.samimine.data.remote

import android.content.Context
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.gson.*
import dev.studios.samimine.data.local.database.SamiMineDatabase
import dev.studios.samimine.data.local.entity.SyncEntry
import dev.studios.samimine.domain.model.UserRole
import dev.studios.samimine.domain.repository.SyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

data class UploadPayload(val title: String, val status: String, val startedAtMillis: Long, val summary: String, val chunks: List<String>)

/** Stable document IDs and immutable terminal payloads allow retries after any server acknowledgment. */
class FirebaseSyncRepository(
    private val context: Context, private val database: SamiMineDatabase,
    private val auth: FirebaseAuth, private val db: FirebaseFirestore,
) : SyncRepository {
    private val gson = Gson()
    override fun schedule(uid: String) {
        WorkManager.getInstance(context).enqueueUniqueWork("sync-$uid", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ResultSyncWorker>().setInputData(workDataOf("uid" to uid))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag("account-$uid").build())
    }

    suspend fun payload(entry: SyncEntry): UploadPayload {
        val root: JsonObject
        val rows: List<JsonElement>
        val title: String
        val status: String
        val started: Long
        when (entry.kind) {
            "PVT" -> {
                val item = checkNotNull(database.evaluations().get(entry.runId)).toDomain()
                check(item.worker.id == entry.workerId && item.status.name != "IN_PROGRESS")
                root = gson.toJsonTree(item).asJsonObject
                rows = root.remove("attempts").asJsonArray.toList()
                root.remove("syncStatus")
                title = "Prueba de reacción"; status = item.status.name; started = item.startedAtMillis
            }
            "COGNITIVE" -> {
                val item = checkNotNull(database.cognitiveTests().get(entry.runId)).toDomain()
                check(item.workerId == entry.workerId && item.status.name != "IN_PROGRESS")
                root = gson.toJsonTree(item).asJsonObject
                rows = root.remove("trials").asJsonArray.toList()
                root.add("metrics", gson.toJsonTree(item.metrics?.values))
                title = item.type.name; status = item.status.name; started = item.startedAtMillis
            }
            "REPORT" -> {
                val item = checkNotNull(database.battery().report(entry.runId, entry.workerId)).toDomain()
                root = gson.toJsonTree(item).asJsonObject
                val answers = root.remove("answers").asJsonObject
                rows = answers.entrySet().map { (key, value) -> JsonObject().apply { addProperty("question", key); add("answer", value) } }
                title = item.instrumentId.name; status = if (item.omitted) "OMITTED" else "COMPLETED"; started = item.answeredAtMillis
            }
            "ASSESSMENT" -> {
                val item = checkNotNull(database.battery().assessment(entry.runId, entry.workerId)).toDomain()
                check(item.status != "IN_PROGRESS")
                root = gson.toJsonTree(item).asJsonObject; rows = emptyList()
                title = "Recorrido breve"; status = item.status; started = item.startedAtMillis
            }
            else -> error("Tipo de envío desconocido")
        }
        // Bound each document well below Firestore's limit, including exceptionally long sessions.
        val chunks = mutableListOf<String>()
        var group = JsonArray()
        rows.forEach { row ->
            if (group.size() > 0 && (group.toString().length + row.toString().length + 1 > 100_000 || group.size() >= 100)) {
                chunks += group.toString(); group = JsonArray()
            }
            check(row.toString().length <= 99_998)
            group.add(row)
        }
        if (group.size() > 0) chunks += group.toString()
        check(chunks.size <= 10_000)
        check(root.toString().length < 200_000)
        return UploadPayload(title, status, started, root.toString(), chunks)
    }

    override suspend fun sendPending(uid: String): Boolean {
        if (auth.currentUser?.uid != uid) return true // Remains private and pending until the owner returns.
        val entries = database.sync().pending(uid)
        if (entries.isEmpty()) return true
        val profile = try { FirebaseUserRepository(db).profile(uid) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            entries.forEach { database.sync().update(it.kind, it.runId, "ATTENTION", accessError(failure)) }
            return failure is AccessDenied || (failure is FirebaseFirestoreException && failure.code in
                setOf(FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED))
        }
        if (profile.role != UserRole.WORKER) return true
        var allDelivered = true
        for (entry in entries) {
            if (auth.currentUser?.uid != uid) break
            database.sync().update(entry.kind, entry.runId, "SENDING", increment = 1)
            try {
                val payload = payload(entry)
                checkOwner(uid)
                val key = "${entry.kind}_${entry.runId}"
                val worker = db.collection("users").document(uid)
                val result = worker.collection("results").document(key)
                val base = mapOf("ownerUid" to uid, "organizationId" to profile.organizationId,
                    "kind" to entry.kind, "title" to payload.title, "status" to payload.status,
                    "startedAtMillis" to payload.startedAtMillis, "summaryJson" to payload.summary,
                    "chunkCount" to payload.chunks.size, "complete" to false)
                val existing = result.get(Source.SERVER).await()
                if (existing.exists()) {
                    check(existing.getString("summaryJson") == payload.summary && existing.getLong("chunkCount") == payload.chunks.size.toLong()) { "Conflicto de entrega" }
                } else result.set(base).await()
                if (existing.getBoolean("complete") != true) {
                    // Five documents keeps rule access calls below the multi-write limit without relying on cross-write caching.
                    payload.chunks.withIndex().chunked(5).forEach { batchItems ->
                        checkOwner(uid)
                        val batch = db.batch()
                        batchItems.forEach { (index, json) -> batch.set(result.collection("chunks").document(index.toString()), mapOf("index" to index, "json" to json)) }
                        batch.commit().await()
                    }
                    checkOwner(uid)
                    db.runTransaction { transaction ->
                        val current = transaction.get(result)
                        check(current.getString("summaryJson") == payload.summary)
                        if (current.getBoolean("complete") != true) {
                            transaction.update(result, "complete", true)
                            transaction.set(worker.collection("tracking").document(key), mapOf("ownerUid" to uid,
                                "organizationId" to profile.organizationId, "kind" to entry.kind, "status" to payload.status,
                                "startedAtMillis" to payload.startedAtMillis, "receivedAt" to FieldValue.serverTimestamp()))
                        }
                    }.await()
                }
                checkOwner(uid)
                check(result.get(Source.SERVER).await().getBoolean("complete") == true)
                database.sync().update(entry.kind, entry.runId, "SYNCED")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                database.sync().update(entry.kind, entry.runId, "ATTENTION", accessError(failure))
                val permanent = failure is AccessDenied || failure is IllegalStateException ||
                    (failure is FirebaseFirestoreException && failure.code in setOf(FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED, FirebaseFirestoreException.Code.INVALID_ARGUMENT))
                if (!permanent) allDelivered = false
            }
        }
        return allDelivered
    }
    private fun checkOwner(uid: String) { if (auth.currentUser?.uid != uid) throw AccessDenied() }
}

fun accessError(failure: Exception): String = when {
    failure is AccessDenied -> failure.message!!
    failure is FirebaseFirestoreException && failure.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> "Cuota temporal agotada. El resultado sigue guardado en este teléfono."
    failure is FirebaseFirestoreException && failure.code in setOf(FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED) -> "Acceso no autorizado. Vuelve a ingresar o consulta al administrador."
    failure is IllegalStateException -> "La entrega requiere revisión. Los datos siguen guardados en el teléfono."
    else -> "No se pudo conectar. Tus datos siguen guardados; puedes reintentar."
}

class ResultSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.failure()
        val container = (applicationContext as dev.studios.samimine.app.SamiMineApplication).container
        if (container.auth.currentUid != uid) return Result.success()
        return try {
            if (container.sync(uid).sendPending(uid)) Result.success() else Result.retry()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
