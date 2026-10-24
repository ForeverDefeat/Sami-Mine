package dev.studios.samimine.domain.model

enum class BatteryRunStatus { PREPARED, IN_PROGRESS, PAUSED, COMPLETED, ABANDONED }
enum class BatteryStepStatus { PENDING, IN_PROGRESS, COMPLETED, OMITTED, INTERRUPTED }
data class BatteryDefinition(val id: String, val name: String, val version: Int, val organizationId: String,
    val modules: List<String>, val protocols: Map<String, String>, val schemaVersion: Int = 1) {
    fun validate(instruments: List<InstrumentDefinition> = InstrumentCatalog.definitions) {
        require(id.isNotBlank() && name.isNotBlank() && name.length <= 100 && version > 0 && organizationId.isNotBlank())
        require(schemaVersion == 1) { "Actualiza la aplicación para usar esta batería" }
        require(modules.size in 1..8 && modules.distinct().size == modules.size && protocols.keys == modules.toSet())
        val catalog = BatteryCatalog.modules(instruments).associateBy { it.id }
        require(modules.all { catalog[it]?.let { m -> m.available && m.protocolVersion == protocols[it] } == true }) {
            "Hay módulos no habilitados o protocolos incompatibles. Actualiza la aplicación o consulta al administrador."
        }
    }
    companion object {
        val completeOrder = listOf("KSS", "PVT", "FLANKER", "GO_NO_GO", "VISUAL_SEARCH", "DSST", "N_BACK", "CHALDER")
        fun brief(organizationId: String, instruments: List<InstrumentDefinition>) = BatteryDefinition("brief", "Recorrido breve KSS → PVT", 1,
            organizationId, listOf("KSS", "PVT"), BatteryCatalog.modules(instruments).filter { it.id in listOf("KSS", "PVT") }
                .associate { it.id to checkNotNull(it.protocolVersion) }).also { it.validate(instruments) }
    }
}
data class BatteryAttempt(val kind: String, val runId: String, val status: String)
data class BatteryStep(val moduleId: String, val status: BatteryStepStatus = BatteryStepStatus.PENDING,
    val attempts: List<BatteryAttempt> = emptyList())
data class GuidedBatteryRun(val id: String, val workerId: String, val shift: Shift, val startedAtMillis: Long,
    val definition: BatteryDefinition, val steps: List<BatteryStep> = definition.modules.map { BatteryStep(it) },
    val status: BatteryRunStatus = BatteryRunStatus.PREPARED, val finishedAtMillis: Long? = null) {
    val nextStep: Int get() = steps.indexOfFirst { it.status !in setOf(BatteryStepStatus.COMPLETED, BatteryStepStatus.OMITTED) }
    val terminal: Boolean get() = status in setOf(BatteryRunStatus.COMPLETED, BatteryRunStatus.ABANDONED)
    fun validate() {
        require(id.isNotBlank() && workerId.isNotBlank() && steps.map { it.moduleId } == definition.modules)
        require(steps.all { it.status != BatteryStepStatus.OMITTED || it.moduleId == "KSS" })
        require(status != BatteryRunStatus.COMPLETED || nextStep == -1)
    }
}
