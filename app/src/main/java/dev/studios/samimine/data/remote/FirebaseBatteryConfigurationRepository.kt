package dev.studios.samimine.data.remote

import com.google.firebase.firestore.*
import com.google.gson.Gson
import dev.studios.samimine.data.local.database.SamiMineDatabase
import dev.studios.samimine.data.local.entity.BatteryConfigCache
import dev.studios.samimine.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class BatteryDraft(val name: String, val modules: List<String>, val revision: Long)

/** Configuration contains only module identifiers, never instrument wording or participant results. */
class FirebaseBatteryConfigurationRepository(private val db: FirebaseFirestore, val organizationId: String) {
    private val root get() = db.collection("organizations").document(organizationId)
    private val draft get() = root.collection("batteryDrafts").document("default")
    private val active get() = root.collection("batterySettings").document("active")
    private val gson = Gson()
    suspend fun loadDraft(): BatteryDraft {
        val row = draft.get(Source.SERVER).await()
        return if (!row.exists()) BatteryDraft("Batería SamiMine", BatteryDefinition.completeOrder, 0)
        else BatteryDraft(row.getString("name")!!, (row.get("modules") as List<*>).map { it as String }, row.getLong("revision")!!)
    }
    suspend fun saveDraft(value: BatteryDraft): BatteryDraft {
        validateShape(value)
        db.runTransaction { tx ->
            val old = tx.get(draft)
            check((old.getLong("revision") ?: 0L) == value.revision) { "Otro administrador modificó el borrador. Vuelve a cargarlo." }
            tx.set(draft, mapOf("name" to value.name.trim(), "modules" to value.modules,
                "revision" to value.revision + 1, "schemaVersion" to 1))
        }.await()
        return value.copy(name = value.name.trim(), revision = value.revision + 1)
    }
    suspend fun publish(value: BatteryDraft): BatteryDefinition {
        validateShape(value)
        val protocols = BatteryCatalog.modules().filter { it.id in value.modules }.associate { it.id to checkNotNull(it.protocolVersion) }
        // Availability is checked both here and by Security Rules against the server-owned catalog.
        BatteryDefinition("default", value.name, 1, organizationId, value.modules, protocols).validate()
        val result = db.runTransaction { tx ->
            val currentDraft = tx.get(draft)
            val current = tx.get(active)
            check(currentDraft.getLong("revision") == value.revision && currentDraft.getString("name") == value.name &&
                currentDraft.get("modules") == value.modules) { "Guarda o vuelve a cargar el borrador antes de publicar." }
            val version = (current.getLong("version") ?: 0L) + 1
            val definition = BatteryDefinition("default", value.name, version.toInt(), organizationId, value.modules, protocols)
            val reference = root.collection("batteryVersions").document(version.toString())
            tx.set(reference, mapOf("id" to definition.id, "name" to definition.name, "version" to version,
                "organizationId" to organizationId, "modules" to value.modules, "protocols" to protocols, "schemaVersion" to 1))
            tx.set(active, mapOf("version" to version))
            definition
        }.await()
        return result
    }
    suspend fun download(): BatteryDefinition? {
        val version = active.get(Source.SERVER).await().getLong("version") ?: return null
        val row = root.collection("batteryVersions").document(version.toString()).get(Source.SERVER).await()
        check(row.exists()) { "No se encuentra la versión publicada" }
        val result = gson.fromJson(gson.toJson(row.data), BatteryDefinition::class.java)
        check(result.organizationId == organizationId && result.version.toLong() == version)
        result.validate()
        return result
    }
    suspend fun workerDefinition(local: SamiMineDatabase, offline: Boolean): Pair<BatteryDefinition?, String?> {
        if (!offline) {
            try {
                val definition = download()
                if (definition == null) {
                    // Replace a previously cached active configuration with an explicit empty marker.
                    local.guided().cache(BatteryConfigCache(organizationId, "null"))
                    return null to "El administrador todavía no ha publicado una batería."
                }
                local.guided().cache(BatteryConfigCache(organizationId, gson.toJson(definition)))
                return definition to null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: IllegalArgumentException) { return null to "La batería requiere módulos o protocolos de otra versión. Actualiza la aplicación." }
            catch (_: Exception) { /* On network failure use only an already validated, organization-specific cache. */ }
        }
        val cached = local.guided().cached(organizationId)?.snapshot
        val definition = cached?.let { gson.fromJson(it, BatteryDefinition::class.java) }
        definition?.validate()
        return definition to if (definition == null) "Conéctate para descargar una batería publicada por administración."
            else "Usando la última batería descargada. Los nuevos cambios se obtendrán al actualizar con conexión."
    }
    private fun validateShape(value: BatteryDraft) {
        require(value.name.trim().length in 1..100 && value.revision >= 0)
        require(value.modules.size in 1..8 && value.modules.distinct().size == value.modules.size)
        require(value.modules.all { id -> BatteryCatalog.modules().any { it.id == id } })
    }
}
