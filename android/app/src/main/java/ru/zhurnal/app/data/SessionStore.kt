package ru.zhurnal.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.sessionDataStore by preferencesDataStore(name = "zhurnal_session")

data class StoredSession(
    val baseUrl: String? = null,
    val token: String? = null,
    val user: UserDto? = null,
) {
    val isAuthorized: Boolean get() = !token.isNullOrBlank() && user != null
}

class SessionStore(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
        val USER = stringPreferencesKey("user")
    }

    val session: Flow<StoredSession> = context.sessionDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toSession() }

    suspend fun current(): StoredSession = session.first()

    suspend fun saveServer(url: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[Keys.BASE_URL] = url
        }
    }

    suspend fun saveAuth(token: String, user: UserDto) {
        context.sessionDataStore.edit { preferences ->
            preferences[Keys.TOKEN] = token
            preferences[Keys.USER] = AppJson.encodeToString(UserDto.serializer(), user)
        }
    }

    suspend fun updateUser(user: UserDto) {
        context.sessionDataStore.edit { preferences ->
            if (preferences[Keys.TOKEN].isNullOrBlank()) return@edit
            preferences[Keys.USER] = AppJson.encodeToString(UserDto.serializer(), user)
        }
    }

    suspend fun clearAuth() {
        context.sessionDataStore.edit { preferences ->
            preferences.remove(Keys.TOKEN)
            preferences.remove(Keys.USER)
        }
    }

    suspend fun clearServerAndAuth() {
        context.sessionDataStore.edit { preferences ->
            preferences.remove(Keys.BASE_URL)
            preferences.remove(Keys.TOKEN)
            preferences.remove(Keys.USER)
        }
    }

    private fun Preferences.toSession(): StoredSession {
        val user = this[Keys.USER]?.let { raw ->
            runCatching { AppJson.decodeFromString(UserDto.serializer(), raw) }.getOrNull()
        }
        return StoredSession(
            baseUrl = this[Keys.BASE_URL]?.takeIf { it.isNotBlank() },
            token = this[Keys.TOKEN]?.takeIf { it.isNotBlank() },
            user = user,
        )
    }
}
