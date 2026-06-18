package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session")

class SessionManager(private val context: Context) {
    companion object {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val USERNAME = stringPreferencesKey("username")
        val ROLE = stringPreferencesKey("role")
    }

    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_LOGGED_IN] ?: false
    }

    val usernameFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USERNAME]
    }

    val roleFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ROLE]
    }

    suspend fun saveSession(username: String, role: String) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = true
            preferences[USERNAME] = username
            preferences[ROLE] = role
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = false
            preferences[USERNAME] = ""
            preferences[ROLE] = ""
        }
    }
}
