package com.example.appgestin.model

import com.example.appgestin.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Registration(val deviceToken: String, val participantCode: String)
data class EmaPrompt(
    val id: String,
    val instrument: String,
    val scheduledAt: String,
    val expiresAt: String,
    val status: String
)
data class DeviceInfo(val id: String, val model: String, val platform: String, val createdAt: String)
data class ResponseAck(val supportResources: Boolean)
data class SensorAck(val accepted: Int, val duplicate: Int, val rejected: Int, val reasons: List<String> = emptyList())
data class StudyInstrument(val code: String, val name: String, val administration: String)
data class StudyMetric(val code: String, val description: String, val unit: String)

class ApiException(val status: Int, message: String) : Exception(message)

class GstiApi(private val server: String) {
    private val base = server.trim().trimEnd('/')

    init {
        require(base.startsWith("https://") ||
            (BuildConfig.DEBUG && base == "http://10.0.2.2:8000")) {
            "Usa HTTPS. Para el emulador se permite http://10.0.2.2:8000."
        }
    }

    suspend fun register(enrollmentCode: String): Registration {
        val json = request("POST", "/devices/register", null, JSONObject().apply {
            put("enrollment_code", enrollmentCode.trim())
            put("kind", "smartphone")
            put("platform", "android-${android.os.Build.VERSION.RELEASE}")
            put("model", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            put("app_version", BuildConfig.VERSION_NAME)
        })
        return Registration(json.getString("device_token"), json.getString("participant_code"))
    }

    suspend fun deviceMe(token: String): DeviceInfo {
        val result = request("GET", "/devices/me", token)
        return DeviceInfo(result.getString("id"), result.optString("model"),
            result.optString("platform"), result.getString("created_at"))
    }

    suspend fun instruments(): List<StudyInstrument> {
        val arr = requestArray("GET", "/catalog/instruments", null)
        return (0 until arr.length()).map { index ->
            val item = arr.getJSONObject(index)
            StudyInstrument(item.getString("code"), item.getString("name"),
                item.getString("administration"))
        }
    }

    suspend fun metrics(): List<StudyMetric> {
        val arr = requestArray("GET", "/catalog/metrics", null)
        return (0 until arr.length()).map { index ->
            val item = arr.getJSONObject(index)
            StudyMetric(item.getString("code"), item.getString("description"),
                item.getString("unit"))
        }
    }

    suspend fun questionnaireDocuments(): List<JSONObject> {
        val catalog = requestArray("GET", "/catalog/questionnaires", null)
        val available = (0 until catalog.length()).map { i ->
            catalog.getJSONObject(i).getString("instrument")
        }.toSet()
        return Questionnaires.codes.filter { it in available }.map { code ->
            request("GET", "/catalog/questionnaires/$code", null)
        }
    }

    suspend fun prompts(token: String): List<EmaPrompt> {
        val arr = requestArray("GET", "/ema/prompts?hours=168", token)
        return (0 until arr.length()).map { index ->
            val item = arr.getJSONObject(index)
            EmaPrompt(
                item.getString("id"), item.getString("instrument_code"),
                item.getString("scheduled_at"), item.getString("expires_at"),
                item.getString("status")
            )
        }
    }

    suspend fun delivered(token: String, promptId: String) {
        request("POST", "/ema/prompts/$promptId/delivered", token, JSONObject())
    }

    suspend fun submitEma(token: String, payload: JSONObject): ResponseAck {
        return ResponseAck(request("POST", "/questionnaires/responses", token, payload)
            .getBoolean("support_resources"))
    }

    suspend fun submitSensors(token: String, payload: JSONObject): SensorAck {
        val result = request("POST", "/ingest/sensors", token, payload)
        return SensorAck(result.getInt("accepted"), result.getInt("duplicate"),
            result.getInt("rejected"), result.optJSONArray("rejections")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it).optString("reason") }
            }.orEmpty())
    }

    private suspend fun request(
        method: String, path: String, token: String?, body: JSONObject? = null
    ): JSONObject = JSONObject(exchange(method, path, token, body))

    private suspend fun requestArray(method: String, path: String, token: String?): JSONArray =
        JSONArray(exchange(method, path, token, null))

    private suspend fun exchange(
        method: String, path: String, token: String?, body: JSONObject?
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL("$base/api/v1$path").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val result = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(result).optString("detail") }.getOrNull()
                throw ApiException(status, detail?.takeIf { it.isNotBlank() } ?: "Error HTTP $status")
            }
            result
        } finally {
            connection.disconnect()
        }
    }
}

fun timestampNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())

fun emaPayload(responseId: String, promptId: String?, startedAt: String, answers: List<Int>): JSONObject =
    JSONObject().apply {
        put("client_response_id", responseId)
        put("instrument", "EMA_DAILY")
        put("context", "ema")
        if (promptId != null) put("ema_prompt_id", promptId)
        put("started_at", startedAt)
        put("completed_at", timestampNow())
        put("answers", JSONArray().apply {
            answers.forEachIndexed { index, value ->
                put(JSONObject().put("item", index + 1).put("value", value))
            }
        })
    }

