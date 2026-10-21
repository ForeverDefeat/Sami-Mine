package dev.studios.samimine.domain.model

enum class UserRole(val label: String) { WORKER("Trabajador"), WELLBEING("Personal de bienestar"), ADMIN("Administrador / supervisor") }

data class UserProfile(
    val uid: String, val name: String, val organizationId: String, val role: UserRole,
    val active: Boolean, val area: String = "", val employment: String = "",
    val wellbeingUid: String? = null,
) {
    fun worker() = Worker(uid, name, area, employment)
}

data class UserSession(val profile: UserProfile, val offline: Boolean = false)
