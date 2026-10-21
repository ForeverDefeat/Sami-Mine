package dev.studios.samimine.domain.repository

import dev.studios.samimine.domain.model.*

interface AuthRepository {
    val currentUid: String?
    suspend fun signIn(email: String, password: String): String
    fun signOut()
}

interface UserRepository {
    suspend fun profile(uid: String): UserProfile
    suspend fun workers(profile: UserProfile, after: String? = null): List<UserProfile>
    suspend fun wellbeing(profile: UserProfile): List<UserProfile>
    suspend fun updateWorker(uid: String, name: String, area: String, employment: String, active: Boolean, wellbeingUid: String?)
}

interface SyncRepository {
    suspend fun sendPending(uid: String): Boolean
    fun schedule(uid: String)
}
