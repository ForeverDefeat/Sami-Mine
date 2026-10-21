package dev.studios.samimine.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.gson.Gson
import dev.studios.samimine.domain.model.*
import dev.studios.samimine.domain.repository.*
import kotlinx.coroutines.tasks.await

class AccessDenied : Exception("Cuenta sin acceso habilitado. Contacta al administrador.")

class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {
    override val currentUid get() = auth.currentUser?.uid
    override suspend fun signIn(email: String, password: String): String =
        checkNotNull(auth.signInWithEmailAndPassword(email.trim(), password).await().user).uid
    override fun signOut() = auth.signOut()
}

class FirebaseUserRepository(private val db: FirebaseFirestore) : UserRepository {
    override suspend fun profile(uid: String): UserProfile {
        val doc = db.collection("users").document(uid).get(Source.SERVER).await()
        return doc.toProfile().also { if (!it.active) throw AccessDenied() }
    }
    override suspend fun workers(profile: UserProfile, after: String?): List<UserProfile> {
        var query: Query = db.collection("users").whereEqualTo("organizationId", profile.organizationId).whereEqualTo("role", "WORKER")
        if (profile.role == UserRole.WELLBEING) query = query.whereEqualTo("wellbeingUid", profile.uid).whereEqualTo("active", true)
        query = query.orderBy(FieldPath.documentId()).limit(25)
        if (after != null) query = query.startAfter(after)
        return query.get(Source.SERVER).await().documents.map { it.toProfile() }
    }
    override suspend fun wellbeing(profile: UserProfile): List<UserProfile> = db.collection("users")
        .whereEqualTo("organizationId", profile.organizationId).whereEqualTo("role", "WELLBEING")
        .whereEqualTo("active", true).limit(50).get(Source.SERVER).await().documents.map { it.toProfile() }
    override suspend fun updateWorker(uid: String, name: String, area: String, employment: String, active: Boolean, wellbeingUid: String?) {
        db.collection("users").document(uid).update(mapOf("name" to name.trim(), "area" to area.trim(),
            "employment" to employment.trim(), "active" to active, "wellbeingUid" to wellbeingUid)).await()
    }
}

fun DocumentSnapshot.toProfile(): UserProfile {
    if (!exists()) throw AccessDenied()
    return UserProfile(id, getString("name") ?: throw AccessDenied(), getString("organizationId") ?: throw AccessDenied(),
        UserRole.valueOf(getString("role") ?: throw AccessDenied()), getBoolean("active") == true,
        getString("area") ?: "", getString("employment") ?: "", getString("wellbeingUid"))
}

/** Only a previously verified worker profile is cached. Passwords/tokens belong to Firebase Auth. */
class WorkerSessionCache(context: Context) {
    private val preferences = context.getSharedPreferences("verified-worker", Context.MODE_PRIVATE)
    private val gson = Gson()
    fun save(profile: UserProfile) {
        if (profile.role == UserRole.WORKER) preferences.edit().putString("profile", gson.toJson(profile)).commit()
        else clear()
    }
    fun restore(uid: String): UserProfile? = runCatching {
        gson.fromJson(preferences.getString("profile", null), UserProfile::class.java)
            ?.takeIf { it.uid == uid && it.role == UserRole.WORKER && it.active }
    }.getOrNull()
    fun clear() { preferences.edit().clear().commit() }
}

fun Context.isOnline(): Boolean {
    val connectivity = getSystemService(ConnectivityManager::class.java)
    // Connectivity is only a hint. Auth and Source.SERVER reads are the authority, including private test networks.
    return connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

object PilotEnvironment { // Set only by the instrumented test runner; never by intents or preferences.
    var demoTests = false
    var firebaseApp: com.google.firebase.FirebaseApp? = null
}
