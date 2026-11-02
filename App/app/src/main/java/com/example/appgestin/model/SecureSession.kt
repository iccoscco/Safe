package com.example.appgestin.model

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class Session(val server: String, val token: String, val participantCode: String)

class SecureSession(context: Context) {
    private val preferences = context.getSharedPreferences("gsti_session", Context.MODE_PRIVATE)
    private val profilePreferences = context.getSharedPreferences("safe_local_profile", Context.MODE_PRIVATE)
    private val alias = "gsti_device_session_v1"

    fun save(session: Session) {
        preferences.edit()
            .putString("server", session.server)
            .putString("participant_code", session.participantCode)
            .putString("token", encrypt(session.token))
            .apply()
    }

    fun load(): Session? {
        val server = preferences.getString("server", null) ?: return null
        val code = preferences.getString("participant_code", null) ?: return null
        val token = preferences.getString("token", null) ?: return null
        return Session(server, decrypt(token), code)
    }

    fun pendingPayload(): String? = preferences.getString("pending_payload", null)?.let(::decrypt)

    fun setPendingPayload(payload: String?) {
        preferences.edit().putString("pending_payload", payload?.let(::encrypt)).apply()
    }

    fun pendingSensorPayload(): String? =
        preferences.getString("pending_sensor_payload", null)?.let(::decrypt)

    fun setPendingSensorPayload(payload: String?) {
        preferences.edit().putString("pending_sensor_payload", payload?.let(::encrypt)).apply()
    }

    fun cachedQuestionnaires(): String? = preferences.getString("questionnaire_catalog", null)?.let(::decrypt)

    fun cacheQuestionnaires(json: String) {
        preferences.edit().putString("questionnaire_catalog", encrypt(json)).apply()
    }

    fun loadProfile(): LocalProfile = runCatching {
        val saved = profilePreferences.getString("profile", null) ?: return LocalProfile()
        val json = JSONObject(decrypt(saved))
        LocalProfile(json.optString("name", "Admin"), json.optString("email"),
            json.optString("bio"), json.optString("photo").takeIf { it.isNotBlank() })
    }.getOrDefault(LocalProfile())

    fun saveProfile(profile: LocalProfile) {
        val json = JSONObject().put("name", profile.name).put("email", profile.email)
            .put("bio", profile.bio).put("photo", profile.photoPath.orEmpty())
        profilePreferences.edit().putString("profile", encrypt(json.toString())).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
