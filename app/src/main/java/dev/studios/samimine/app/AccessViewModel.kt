package dev.studios.samimine.app

import android.content.Context
import android.net.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import dev.studios.samimine.app.di.AppContainer
import dev.studios.samimine.data.remote.*
import dev.studios.samimine.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

data class RemoteRun(val id: String, val kind: String, val status: String, val startedAtMillis: Long, val summaryJson: String? = null, val title: String? = null)
data class AccessState(
    val session: UserSession? = null, val loading: Boolean = true, val error: String? = null,
    val workers: List<UserProfile> = emptyList(), val wellbeing: List<UserProfile> = emptyList(),
    val selectedWorker: UserProfile? = null, val runs: List<RemoteRun> = emptyList(),
    val detail: RemoteRun? = null, val detailChunks: List<String> = emptyList(),
    val moreWorkers: Boolean = false, val moreRuns: Boolean = false,
)

class AccessViewModel(private val context: Context, val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(AccessState())
    val state = mutableState.asStateFlow()
    private var generation = 0
    private var actionJob: Job? = null
    private var lastRun: DocumentSnapshot? = null
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { refreshAccess() }
        override fun onLost(network: Network) { refreshAccess() }
    }
    init {
        connectivity.registerDefaultNetworkCallback(networkCallback)
        restore()
    }
    private fun launchAction(action: suspend () -> Unit) {
        if (mutableState.value.loading) return
        actionJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                mutableState.update { it.copy(error = message(failure), workers = emptyList(), runs = emptyList(), detail = null, detailChunks = emptyList()) }
                if (failure is AccessDenied) blockAccess()
            } finally { mutableState.update { it.copy(loading = false) } }
        }
    }
    fun login(email: String, password: String) {
        launchAction {
            try {
                val uid = container.auth.signIn(email, password)
                val profile = container.users.profile(uid)
                container.sessionCache.save(profile)
                mutableState.value = AccessState(UserSession(profile), loading = true)
                if (profile.role != UserRole.WORKER) loadWorkers(false) else container.sync(uid).schedule(uid)
            } catch (cancelled: CancellationException) { container.auth.signOut(); throw cancelled }
            catch (failure: Exception) { container.auth.signOut(); container.sessionCache.clear(); throw failure }
        }
    }
    private fun restore() {
        viewModelScope.launch {
            val uid = container.auth.currentUid
            if (uid == null) { mutableState.value = AccessState(loading = false); return@launch }
            try {
                val profile = if (context.isOnline()) container.users.profile(uid) else container.sessionCache.restore(uid) ?: throw AccessDenied()
                container.sessionCache.save(profile)
                mutableState.value = AccessState(UserSession(profile, !context.isOnline()), loading = true)
                if (profile.role == UserRole.WORKER) container.sync(uid).schedule(uid) else loadWorkers(false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                // A network failure is eligible for cached worker access; denial never is.
                val cached = container.sessionCache.restore(uid)
                if (cached != null && (failure is FirebaseNetworkException || (failure is FirebaseFirestoreException && failure.code == FirebaseFirestoreException.Code.UNAVAILABLE)))
                    mutableState.value = AccessState(UserSession(cached, true), loading = true)
                else { container.auth.signOut(); container.sessionCache.clear(); mutableState.value = AccessState(error = message(failure), loading = true) }
            } finally { mutableState.update { it.copy(loading = false) } }
        }
    }
    fun refreshAccess() {
        viewModelScope.launch {
            val session = state.value.session ?: return@launch
            val currentGeneration = generation
            if (!context.isOnline()) {
                mutableState.update { it.copy(session = session.copy(offline = true),
                    workers = emptyList(), runs = emptyList(), detail = null, detailChunks = emptyList()) }
                return@launch
            }
            try {
                val profile = container.users.profile(session.profile.uid)
                if (currentGeneration != generation || container.auth.currentUid != profile.uid) return@launch
                container.sessionCache.save(profile)
                mutableState.update { it.copy(session = UserSession(profile)) }
                if (profile.role == UserRole.WORKER && session.offline) container.sync(profile.uid).schedule(profile.uid)
                if (profile.role != UserRole.WORKER && session.offline && !state.value.loading) refreshWorkers()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (currentGeneration != generation) return@launch
                if (failure is AccessDenied || (failure is FirebaseFirestoreException && failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)) blockAccess()
                else mutableState.update { it.copy(session = session.copy(offline = true), workers = emptyList(), runs = emptyList(), detail = null, detailChunks = emptyList()) }
            }
        }
    }
    private fun blockAccess() {
        val uid = state.value.session?.profile?.uid
        generation++
        actionJob?.cancel()
        if (uid != null) WorkManager.getInstance(context).cancelAllWorkByTag("account-$uid")
        container.sessionCache.clear(); container.auth.signOut()
        mutableState.value = AccessState(loading = false, error = "El acceso está deshabilitado. Contacta al administrador.")
    }
    fun watchAccess(): () -> Unit {
        val session = state.value.session ?: return {}
        val version = generation
        val subscription = container.firestore.collection("users").document(session.profile.uid).addSnapshotListener { snapshot, failure ->
            if (version != generation || container.auth.currentUid != session.profile.uid) return@addSnapshotListener
            if (failure is FirebaseFirestoreException && failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                blockAccess()
            } else if (snapshot != null && !snapshot.metadata.isFromCache) {
                val profile = runCatching { snapshot.toProfile() }.getOrNull()
                if (profile == null || !profile.active || profile.role != session.profile.role || profile.organizationId != session.profile.organizationId) blockAccess()
                else {
                    container.sessionCache.save(profile)
                    mutableState.update { current -> if (current.session?.profile?.uid == profile.uid) current.copy(session = UserSession(profile)) else current }
                }
            }
        }
        return { subscription.remove() }
    }
    fun watchWorker(): () -> Unit {
        val session = state.value.session ?: return {}
        val selected = state.value.selectedWorker ?: return {}
        if (session.profile.role != UserRole.WELLBEING) return {}
        val version = generation
        val subscription = container.firestore.collection("users").document(selected.uid).addSnapshotListener { snapshot, failure ->
            if (version != generation || container.auth.currentUid != session.profile.uid) return@addSnapshotListener
            val serverProfile = snapshot?.takeIf { !it.metadata.isFromCache }?.let { runCatching { it.toProfile() }.getOrNull() }
            if (failure != null || (serverProfile != null && (!serverProfile.active || serverProfile.wellbeingUid != session.profile.uid))) {
                actionJob?.cancel()
                mutableState.update { it.copy(selectedWorker = null, runs = emptyList(), detail = null, detailChunks = emptyList(),
                    workers = it.workers.filterNot { person -> person.uid == selected.uid }, loading = false,
                    error = "La asignación cambió. Actualiza la lista de trabajadores.") }
            }
        }
        return { subscription.remove() }
    }
    fun logout(workerModel: SamiMineViewModel?) {
        launchAction {
            if (workerModel != null && !workerModel.prepareSignOut()) error("No se pudo cerrar la evaluación")
            val uid = state.value.session?.profile?.uid
            generation++
            if (uid != null) WorkManager.getInstance(context).cancelAllWorkByTag("account-$uid")
            container.auth.signOut(); container.sessionCache.clear(); lastRun = null
            mutableState.value = AccessState(loading = true)
        }
    }
    fun refreshWorkers(more: Boolean = false) { launchAction { loadWorkers(more) } }
    private suspend fun loadWorkers(more: Boolean) {
        val profile = requireOnline()
        val page = container.users.workers(profile, if (more) state.value.workers.lastOrNull()?.uid else null)
        val wellbeing = if (profile.role == UserRole.ADMIN) container.users.wellbeing(profile) else emptyList()
        mutableState.update { it.copy(workers = if (more) it.workers + page else page, wellbeing = wellbeing,
            moreWorkers = page.size == 25, selectedWorker = null, runs = emptyList(), detail = null, detailChunks = emptyList()) }
    }
    fun selectWorker(worker: UserProfile) {
        launchAction {
            requireOnline(); lastRun = null
            mutableState.update { it.copy(selectedWorker = worker, runs = emptyList(), detail = null, detailChunks = emptyList()) }
            loadRuns(false)
        }
    }
    fun moreRuns() { launchAction { loadRuns(true) } }
    private suspend fun loadRuns(more: Boolean) {
        val profile = requireOnline()
        val worker = checkNotNull(state.value.selectedWorker)
        val admin = profile.role == UserRole.ADMIN
        var query: Query = container.firestore.collection("users").document(worker.uid).collection(if (admin) "tracking" else "results")
        if (!admin) query = query.whereEqualTo("complete", true)
        query = query.orderBy("startedAtMillis", Query.Direction.DESCENDING).orderBy(FieldPath.documentId()).limit(25)
        if (more && lastRun != null) query = query.startAfter(lastRun!!)
        val page = query.get(Source.SERVER).await().documents
        lastRun = page.lastOrNull()
        val runs = page.map { RemoteRun(it.id, it.getString("kind") ?: "", it.getString("status") ?: "", it.getLong("startedAtMillis") ?: 0,
            if (admin) null else it.getString("summaryJson"), if (admin) null else it.getString("title")) }
        mutableState.update { it.copy(runs = if (more) it.runs + runs else runs, moreRuns = page.size == 25) }
    }
    fun detail(run: RemoteRun) {
        launchAction {
            val profile = requireOnline(); check(profile.role == UserRole.WELLBEING)
            val worker = checkNotNull(state.value.selectedWorker)
            val root = container.firestore.collection("users").document(worker.uid).collection("results").document(run.id)
            val current = root.get(Source.SERVER).await()
            check(current.getBoolean("complete") == true)
            val chunks = mutableListOf<String>()
            var lastIndex = -1L
            do {
                val page = root.collection("chunks").orderBy("index").startAfter(lastIndex).limit(100).get(Source.SERVER).await().documents
                chunks += page.map { checkNotNull(it.getString("json")) }
                if (page.isNotEmpty()) lastIndex = page.last().getLong("index")!!
            } while (page.size == 100)
            check(chunks.size.toLong() == current.getLong("chunkCount"))
            mutableState.update { it.copy(detail = run.copy(summaryJson = current.getString("summaryJson")), detailChunks = chunks) }
        }
    }
    fun saveWorker(worker: UserProfile, name: String, area: String, employment: String, active: Boolean, wellbeingUid: String?) {
        launchAction {
            check(requireOnline().role == UserRole.ADMIN)
            require(name.trim().length in 1..100 && area.length <= 100 && employment.length <= 100)
            container.users.updateWorker(worker.uid, name, area, employment, active, wellbeingUid)
            loadWorkers(false)
        }
    }
    fun backToWorkers() { mutableState.update { it.copy(selectedWorker = null, runs = emptyList(), detail = null, detailChunks = emptyList()) }; lastRun = null }
    fun closeDetail() { mutableState.update { it.copy(detail = null, detailChunks = emptyList()) } }
    private suspend fun requireOnline(): UserProfile {
        val session = checkNotNull(state.value.session)
        val profile = container.users.profile(session.profile.uid)
        check(profile.role == session.profile.role)
        return profile
    }
    private fun message(failure: Exception): String = when (failure) {
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "Correo o contraseña incorrectos, o cuenta no habilitada."
        is FirebaseAuthException -> "No se pudo iniciar sesión. Revisa las credenciales o contacta al administrador."
        is FirebaseNetworkException -> "Se necesita conexión a Internet para esta acción."
        else -> accessError(failure)
    }
    override fun onCleared() { connectivity.unregisterNetworkCallback(networkCallback); super.onCleared() }
}
