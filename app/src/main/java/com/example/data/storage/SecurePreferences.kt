package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.domain.model.ServerConfig

class SecurePreferences(context: Context) {
  private val prefs: SharedPreferences = createPrefs(context)

  private fun createPrefs(context: Context): SharedPreferences {
    return try {
      val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
      EncryptedSharedPreferences.create(
        PREFS_FILE,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
      )
    } catch (_: Exception) {
      // Fallback for JVM test environments without Android Keystore hardware
      context.getSharedPreferences(PREFS_FILE_FALLBACK, Context.MODE_PRIVATE)
    }
  }

  fun loadConfig(): ServerConfig {
    return ServerConfig(
      baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
      apiToken = prefs.getString(KEY_API_TOKEN, "").orEmpty(),
      tokenSetAtMillis = prefs.getLong(KEY_TOKEN_SET_AT, 0L),
      cfAccessClientId = prefs.getString(KEY_CF_CLIENT_ID, "").orEmpty(),
      cfAccessClientSecret = prefs.getString(KEY_CF_CLIENT_SECRET, "").orEmpty(),
    )
  }

  fun saveConfig(config: ServerConfig) {
    prefs.edit()
      .putString(KEY_BASE_URL, config.baseUrl)
      .putString(KEY_API_TOKEN, config.apiToken)
      .putLong(KEY_TOKEN_SET_AT, config.tokenSetAtMillis)
      .putString(KEY_CF_CLIENT_ID, config.cfAccessClientId)
      .putString(KEY_CF_CLIENT_SECRET, config.cfAccessClientSecret)
      .apply()
  }

  fun updateToken(token: String, setAtMillis: Long = System.currentTimeMillis()) {
    prefs.edit()
      .putString(KEY_API_TOKEN, token)
      .putLong(KEY_TOKEN_SET_AT, setAtMillis)
      .apply()
  }

  fun clearTokenKeepUrl() {
    prefs.edit()
      .remove(KEY_API_TOKEN)
      .remove(KEY_TOKEN_SET_AT)
      .apply()
  }

  private companion object {
    const val PREFS_FILE = "redirect_secure_prefs"
    const val PREFS_FILE_FALLBACK = "redirect_fallback_prefs"
    const val KEY_BASE_URL = "server_base_url"
    const val KEY_API_TOKEN = "api_token"
    const val KEY_TOKEN_SET_AT = "token_set_at"
    const val KEY_CF_CLIENT_ID = "cf_access_client_id"
    const val KEY_CF_CLIENT_SECRET = "cf_access_client_secret"
  }
}
