package com.cardwallet.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** F6.7 theme selection. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

private const val NO_DELAY_MILLIS = 0L
private const val THIRTY_SECONDS_MILLIS = 30_000L
private const val ONE_MINUTE_MILLIS = 60_000L
private const val FIVE_MINUTES_MILLIS = 300_000L

/** F6.1 auto-lock choices; IMMEDIATELY locks on any backgrounding. */
enum class AutoLockTimeout(
    val millis: Long,
) {
    IMMEDIATELY(NO_DELAY_MILLIS),
    THIRTY_SECONDS(THIRTY_SECONDS_MILLIS),
    ONE_MINUTE(ONE_MINUTE_MILLIS),
    FIVE_MINUTES(FIVE_MINUTES_MILLIS),
}

private const val FIFTEEN_SECONDS_MILLIS = 15_000L

/**
 * How long a copied secret may sit in the system clipboard.
 *
 * [NEVER] leaves the copy in place: some people rely on normal clipboard
 * behaviour, and silently wiping it would look like a bug. Copies are still
 * flagged sensitive and are still cleared when the vault locks.
 */
enum class ClipboardTimeout(
    val millis: Long,
) {
    NEVER(NO_DELAY_MILLIS),
    FIFTEEN_SECONDS(FIFTEEN_SECONDS_MILLIS),
    THIRTY_SECONDS(THIRTY_SECONDS_MILLIS),
    ONE_MINUTE(ONE_MINUTE_MILLIS),
}

/**
 * Non-sensitive preferences ONLY (plan §3 rule 1) — its own DataStore file,
 * fully separate from the vault metadata.
 */
@Singleton
class SettingsStore
    @Inject
    constructor(
        @param:Named("settings") private val dataStore: DataStore<Preferences>,
    ) {
        val autoLockTimeout: Flow<AutoLockTimeout> =
            dataStore.data.map { prefs ->
                prefs[AUTO_LOCK]?.let { stored ->
                    AutoLockTimeout.entries.firstOrNull { it.millis == stored }
                } ?: AutoLockTimeout.ONE_MINUTE
            }

        val themeMode: Flow<ThemeMode> =
            dataStore.data.map { prefs ->
                prefs[THEME]?.let { stored ->
                    ThemeMode.entries.firstOrNull { it.name == stored }
                } ?: ThemeMode.SYSTEM
            }

        val clipboardTimeout: Flow<ClipboardTimeout> =
            dataStore.data.map { prefs ->
                prefs[CLIPBOARD]?.let { stored ->
                    ClipboardTimeout.entries.firstOrNull { it.name == stored }
                } ?: ClipboardTimeout.THIRTY_SECONDS
            }

        suspend fun setAutoLockTimeout(value: AutoLockTimeout) {
            dataStore.edit { it[AUTO_LOCK] = value.millis }
        }

        suspend fun setThemeMode(value: ThemeMode) {
            dataStore.edit { it[THEME] = value.name }
        }

        suspend fun setClipboardTimeout(value: ClipboardTimeout) {
            dataStore.edit { it[CLIPBOARD] = value.name }
        }

        private companion object {
            val AUTO_LOCK = longPreferencesKey("auto_lock_millis")
            val THEME = stringPreferencesKey("theme_mode")

            // Stored by NAME, unlike AUTO_LOCK: NEVER and IMMEDIATELY would
            // both be 0ms, so a millis lookup could not tell them apart.
            val CLIPBOARD = stringPreferencesKey("clipboard_timeout")
        }
    }
