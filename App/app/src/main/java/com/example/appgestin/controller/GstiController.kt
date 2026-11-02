package com.example.appgestin.controller

import android.content.Context
import android.net.Uri
import com.example.appgestin.BuildConfig
import com.example.appgestin.model.LocalAccess
import com.example.appgestin.model.LocalProfile
import com.example.appgestin.model.importProfilePhoto
import com.example.appgestin.model.deleteProfilePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.appgestin.model.DeviceInfo
import com.example.appgestin.model.ApiException
import com.example.appgestin.model.EmaPrompt
import com.example.appgestin.model.GstiApi
import com.example.appgestin.model.HealthConnectCollector
import com.example.appgestin.model.Questionnaire
import com.example.appgestin.model.Questionnaires
import com.example.appgestin.model.SecureSession
import com.example.appgestin.model.Session
import com.example.appgestin.model.StudyInstrument
import com.example.appgestin.model.StudyMetric
import com.example.appgestin.model.emaPayload
import com.example.appgestin.model.questionnairePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import com.example.appgestin.model.parseQuestionnaire
import java.util.UUID

enum class Page { Login, Enroll, Home, CheckIn, Questionnaire, Study, Health, Resources, Settings }

data class AppState(
    val page: Page = Page.Login,
    val preview: Boolean = false,
    val profile: LocalProfile = LocalProfile(),
    val participantCode: String = "",
    val device: DeviceInfo? = null,
    val server: String = "",
    val prompts: List<EmaPrompt> = emptyList(),
    val instruments: List<StudyInstrument> = emptyList(),
    val metrics: List<StudyMetric> = emptyList(),
    val questionnaires: List<Questionnaire> = emptyList(),
    val catalogFromServer: Boolean = false,
    val selectedPrompt: EmaPrompt? = null,
    val selectedQuestionnaire: Questionnaire? = null,
    val hasPendingResponse: Boolean = false,
    val hasPendingSensorBatch: Boolean = false,
    val healthAvailable: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

/** Coordinates UI events; storage, API calls and navigation never live in Compose screens. */
class GstiController(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val store = SecureSession(appContext)
    private var localAuthenticated = false
    private val health = HealthConnectCollector(context.applicationContext)
    private var session: Session? = runCatching { store.load() }.getOrNull()

    var state by mutableStateOf(AppState(
        page = Page.Login,
        profile = store.loadProfile(),
        participantCode = session?.participantCode.orEmpty(),
        server = session?.server.orEmpty(),
        hasPendingResponse = runCatching { store.pendingPayload() != null }.getOrDefault(false),
        hasPendingSensorBatch = runCatching { store.pendingSensorPayload() != null }.getOrDefault(false),
        healthAvailable = runCatching { health.isAvailable() }.getOrDefault(false),
        questionnaires = cachedForms()
    ))
        private set

    private fun cachedForms(): List<Questionnaire> = runCatching {
        val arr = JSONArray(store.cachedQuestionnaires() ?: "[]")
        Questionnaires.supported((0 until arr.length()).map { parseQuestionnaire(arr.getJSONObject(it)) })
    }.getOrDefault(emptyList())

    private suspend fun loadCatalog(server: String) {
        val api = GstiApi(server)
        val instruments = api.instruments()
        val metrics = api.metrics()
        val documents = api.questionnaireDocuments()
        val remote = documents.map(::parseQuestionnaire)
        val forms = Questionnaires.supported(remote).filter { q -> instruments.any { it.code == q.code } }
        store.cacheQuestionnaires(JSONArray(documents).toString())
        state = state.copy(instruments = instruments, metrics = metrics,
            questionnaires = forms, catalogFromServer = true)
    }

    fun login(username: String, password: String) {
        if (state.busy) return
        if (!LocalAccess.accepts(username, password)) {
            state = state.copy(error = "Usuario o contraseña incorrectos.")
            return
        }
        localAuthenticated = true
        val current = session
        state = state.copy(page = Page.Home, preview = current == null, error = null, notice = null,
            server = current?.server ?: state.server.ifBlank { if (BuildConfig.DEBUG) "http://10.0.2.2:8000" else "" })
        if (current != null) refresh() else refreshPreviewCatalog()
    }

    fun showEnrollment() { state = state.copy(page = Page.Enroll, error = null, notice = null) }
    fun closeEnrollment() {
        state = state.copy(page = if (localAuthenticated) Page.Home else Page.Login, error = null, notice = null)
    }

    fun saveProfile(name: String, email: String, bio: String) {
        if (!localAuthenticated || state.busy) return
        val updated = state.profile.copy(name = name.trim().take(64), email = email.trim().take(120), bio = bio.trim().take(180))
        if (updated.name.isBlank()) {
            state = state.copy(error = "Escribe un nombre para tu perfil.")
            return
        }
        store.saveProfile(updated)
        state = state.copy(profile = updated, error = null, notice = "Perfil actualizado en este teléfono.")
    }

    fun setProfilePhoto(uri: Uri) {
        if (!localAuthenticated || state.busy) return
        scope.launch {
            state = state.copy(busy = true, error = null, notice = null)
            try {
                val path = withContext(Dispatchers.IO) { importProfilePhoto(appContext, uri) }
                val old = state.profile.photoPath
                val updated = state.profile.copy(photoPath = path)
                store.saveProfile(updated)
                state = state.copy(profile = updated, notice = "Foto de perfil actualizada.")
                withContext(Dispatchers.IO) { deleteProfilePhoto(appContext, old) }
            } catch (e: Exception) {
                state = state.copy(error = "No se pudo guardar esa foto. Prueba con otra imagen.")
            } finally { state = state.copy(busy = false) }
        }
    }

    fun removeProfilePhoto() {
        if (!localAuthenticated || state.busy) return
        val old = state.profile.photoPath
        val updated = state.profile.copy(photoPath = null)
        store.saveProfile(updated)
        state = state.copy(profile = updated, error = null, notice = "Foto eliminada de tu perfil.")
        scope.launch(Dispatchers.IO) { deleteProfilePhoto(appContext, old) }
    }

    private fun refreshPreviewCatalog() {
        if (state.busy) return
        scope.launch {
            state = state.copy(busy = true, error = null, notice = null)
            try {
                loadCatalog(state.server)
            } catch (e: Exception) {
                state = state.copy(error = if (state.questionnaires.isEmpty())
                    "Conecta con el servidor para descargar los cuatro cuestionarios. Puedes seguir usando el check-in diario."
                    else "No se pudo actualizar. Puedes consultar las preguntas descargadas anteriormente.")
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    fun register(server: String, enrollmentCode: String) {
        if (state.busy) return
        if (state.hasPendingResponse || state.hasPendingSensorBatch) {
            state = state.copy(error = "Confirma los envíos pendientes antes de cambiar la vinculación.")
            return
        }
        scope.launch {
            state = state.copy(busy = true, error = null)
            try {
                val registration = GstiApi(server).register(enrollmentCode)
                val current = Session(server.trim().trimEnd('/'), registration.deviceToken,
                    registration.participantCode)
                store.save(current)
                session = current
                state = state.copy(page = if (localAuthenticated) Page.Home else Page.Login,
                    preview = false, participantCode = current.participantCode, server = current.server,
                    notice = if (localAuthenticated) "Dispositivo vinculado." else "Dispositivo vinculado. Inicia sesión para continuar.")
                if (localAuthenticated) {
                    loadPrompts(current)
                    if (session != null) loadCatalog(current.server)
                }
            } catch (e: Exception) {
                state = state.copy(error = explain(e))
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    fun refresh() {
        val current = session ?: return
        if (state.busy) return
        scope.launch {
            state = state.copy(busy = true, error = null)
            try {
                loadPrompts(current)
                if (session != null) loadCatalog(current.server)
            } catch (e: Exception) {
                state = state.copy(error = explain(e))
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    private suspend fun loadPrompts(current: Session) {
        try {
            val api = GstiApi(current.server)
            val device = api.deviceMe(current.token)
            state = state.copy(device = device, prompts = api.prompts(current.token), error = null)
        } catch (e: ApiException) {
            if (e.status == 401 || e.status == 403) {
                invalidateDevice("La vinculación ya no está vigente. Puedes seguir explorando y volver a vincular el teléfono.")
            } else throw e
        }
    }

    fun openCheckIn(prompt: EmaPrompt?) {
        if (state.hasPendingResponse) return
        state = state.copy(page = Page.CheckIn, selectedPrompt = prompt, error = null)
        val current = session ?: return
        if (prompt != null && prompt.status == "scheduled") {
            scope.launch {
                runCatching { GstiApi(current.server).delivered(current.token, prompt.id) }
            }
        }
    }

    fun submitCheckIn(answers: List<Int>, startedAt: String) {
        if (state.preview) {
            state = state.copy(page = Page.Home, notice = "Modo local: las respuestas no se guardaron ni enviaron.", error = null)
            return
        }
        val current = session ?: return
        if (state.busy) return
        scope.launch {
            state = state.copy(busy = true, error = null)
            try {
                if (store.pendingPayload() != null) {
                    throw IllegalStateException("Hay una respuesta pendiente. Vuelve al inicio para reintentar su envío.")
                }
                val payload = emaPayload(UUID.randomUUID().toString(),
                    state.selectedPrompt?.id, startedAt, answers)
                store.setPendingPayload(payload.toString())
                state = state.copy(hasPendingResponse = true)
                val ack = GstiApi(current.server).submitEma(current.token, payload)
                completeSubmission(current, ack.supportResources)
            } catch (e: ApiException) {
                if (loseAccess(e)) return@launch
                if (e.status in 400..499) clearPending()
                state = state.copy(error = explain(e))
            } catch (e: Exception) {
                state = state.copy(error = explain(e))
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    fun openQuestionnaire(code: String) {
        if (state.hasPendingResponse || state.busy) return
        val questionnaire = state.questionnaires.firstOrNull { it.code == code } ?: return
        state = state.copy(page = Page.Questionnaire, selectedQuestionnaire = questionnaire,
            error = null, notice = null)
    }

    fun submitQuestionnaire(answers: List<Int>, startedAt: String) {
        val questionnaire = state.selectedQuestionnaire ?: return
        if (state.busy || !questionnaire.complete(answers)) return
        if (state.preview) {
            val showResources = questionnaire.needsSupport(answers)
            state = state.copy(page = if (showResources) Page.Resources else Page.Home,
                notice = "Modo local: las respuestas no se guardaron ni enviaron.", error = null)
            return
        }
        val current = session ?: return
        scope.launch {
            state = state.copy(busy = true, error = null)
            try {
                if (store.pendingPayload() != null) {
                    throw IllegalStateException("Hay una respuesta pendiente. Vuelve al inicio para reintentar su envío.")
                }
                val payload = questionnairePayload(UUID.randomUUID().toString(), questionnaire,
                    startedAt, answers)
                store.setPendingPayload(payload.toString())
                val showResourcesNow = questionnaire.needsSupport(answers)
                state = state.copy(hasPendingResponse = true,
                    page = if (showResourcesNow) Page.Resources else state.page)
                val ack = GstiApi(current.server).submitEma(current.token, payload)
                completeSubmission(current, ack.supportResources || showResourcesNow)
            } catch (e: ApiException) {
                if (loseAccess(e)) return@launch
                if (e.status in 400..499) clearPending()
                state = state.copy(error = explain(e))
            } catch (e: Exception) {
                state = state.copy(error = explain(e))
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    fun retryPending() {
        val current = session ?: return
        if (state.busy) return
        scope.launch {
            state = state.copy(busy = true, error = null)
            try {
                val payload = JSONObject(store.pendingPayload()
                    ?: throw IllegalStateException("Sin respuesta pendiente"))
                val ack = GstiApi(current.server).submitEma(current.token, payload)
                completeSubmission(current, ack.supportResources)
            } catch (e: ApiException) {
                if (loseAccess(e)) return@launch
                if (e.status in 400..499) clearPending()
                state = state.copy(error = explain(e))
            } catch (e: Exception) {
                state = state.copy(error = explain(e))
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    private suspend fun completeSubmission(current: Session, showResources: Boolean) {
        clearPending()
        state = state.copy(page = if (showResources) Page.Resources else Page.Home,
            notice = "Respuesta recibida. Gracias por este momento.", error = null)
        try {
            loadPrompts(current)
        } catch (e: Exception) {
            state = state.copy(error = explain(e))
        }
    }

    private fun clearPending() {
        store.setPendingPayload(null)
        state = state.copy(hasPendingResponse = false)
    }

    private fun loseAccess(e: ApiException): Boolean {
        if (e.status != 401 && e.status != 403) return false
        invalidateDevice(explain(e))
        return true
    }

    private fun invalidateDevice(message: String) {
        store.clear()
        session = null
        state = AppState(page = if (localAuthenticated) {
            if (state.page == Page.Resources) Page.Resources else Page.Home
        } else Page.Login, preview = localAuthenticated, profile = state.profile,
            server = state.server, questionnaires = state.questionnaires, error = message)
    }

    fun showResources() { state = state.copy(page = Page.Resources, error = null) }
    fun showHealth() { state = state.copy(page = Page.Health, error = null, notice = null) }
    fun syncHealth() {
        val current = session ?: return
        if (state.busy) return
        scope.launch {
            state = state.copy(busy = true, error = null, notice = null)
            try {
                val raw = store.pendingSensorPayload() ?: health.collectLastDay().toString().also {
                    if (JSONObject(it).getJSONArray("observations").length() > 0) {
                        store.setPendingSensorPayload(it)
                    }
                }
                val batch = JSONObject(raw)
                val count = batch.getJSONArray("observations").length()
                if (count == 0) {
                    state = state.copy(notice = "No hay datos de pasos, sueño o pulso para enviar en el último día.")
                } else {
                    state = state.copy(hasPendingSensorBatch = true)
                    val ack = GstiApi(current.server).submitSensors(current.token, batch)
                    store.setPendingSensorPayload(null)
                    state = state.copy(hasPendingSensorBatch = false,
                        notice = "Sincronización: ${ack.accepted} nuevos, ${ack.duplicate} repetidos, ${ack.rejected} rechazados." +
                            if (ack.reasons.isNotEmpty()) " ${ack.reasons.distinct().joinToString("; ")}" else "")
                }
            } catch (e: ApiException) {
                if (loseAccess(e)) return@launch
                if (e.status in 400..499) {
                    store.setPendingSensorPayload(null)
                    state = state.copy(hasPendingSensorBatch = false)
                }
                state = state.copy(error = explain(e))
            } catch (e: Exception) {
                state = state.copy(error = explain(e))
            } finally {
                state = state.copy(busy = false)
            }
        }
    }
    fun showStudy() {
        if (state.preview) {
            state = state.copy(page = Page.Study, error = null)
            refreshPreviewCatalog()
            return
        }
        val current = session ?: return
        state = state.copy(page = Page.Study, error = null)
        if (state.busy) return
        scope.launch {
            state = state.copy(busy = true)
            try {
                loadCatalog(current.server)
            } catch (e: Exception) {
                state = state.copy(error = explain(e))
            } finally {
                state = state.copy(busy = false)
            }
        }
    }
    fun showSettings() { state = state.copy(page = Page.Settings, error = null) }
    fun goHome() { state = state.copy(page = if (localAuthenticated) Page.Home else Page.Login, error = null) }

    fun signOut() {
        if (state.busy) return
        localAuthenticated = false
        state = state.copy(page = Page.Login, error = null, notice = null)
    }

    private fun explain(e: Exception): String = when (e) {
        is ApiException -> when (e.status) {
            401 -> "Código o acceso inválido. Confirma los datos con el equipo."
            403 -> "El participante fue retirado. Se detuvo el envío de datos."
            else -> e.message ?: "Error del servidor (${e.status})."
        }
        is java.net.ConnectException -> "No se pudo conectar. Revisa la dirección y que el backend esté encendido."
        is java.net.SocketTimeoutException -> "La conexión tardó demasiado. Puedes reintentar sin duplicar la respuesta."
        else -> e.message ?: "Ocurrió un problema. Inténtalo de nuevo."
    }
}
