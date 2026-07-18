package com.cardwallet.data.crypto

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** PIN attempt/backoff persistence, in the same vault_meta DataStore file. */
@Singleton
class DataStorePinAttemptStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : PinAttemptStore {
        override suspend fun failedAttempts(): Int = dataStore.data.first()[PIN_ATTEMPTS] ?: 0

        override suspend fun backoffUntilMillis(): Long = dataStore.data.first()[BACKOFF_UNTIL] ?: 0L

        override suspend fun recordFailedAttempt(
            count: Int,
            backoffUntilMillis: Long,
        ) {
            dataStore.edit {
                it[PIN_ATTEMPTS] = count
                it[BACKOFF_UNTIL] = backoffUntilMillis
            }
        }

        override suspend fun resetAttempts() {
            dataStore.edit {
                it.remove(PIN_ATTEMPTS)
                it.remove(BACKOFF_UNTIL)
            }
        }

        private companion object {
            val PIN_ATTEMPTS = intPreferencesKey("pin_attempts")
            val BACKOFF_UNTIL = longPreferencesKey("backoff_until")
        }
    }
