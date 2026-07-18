package com.cardwallet.data.crypto

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Wrap A blob: DEK sealed by the VMK (nonce comes from the Keystore cipher). */
class HardwareWrap(
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

/** Wrap B blob: DEK sealed by PBKDF2(pin) then by the device-bound outer key. */
class PinWrap(
    val salt: ByteArray,
    val outerNonce: ByteArray,
    val outerCiphertext: ByteArray,
)

/**
 * Persistence for the wrap blobs. Everything stored here is ciphertext, salts,
 * or nonces — safe at rest by construction; the gate is the Keystore, not this
 * file (plan §3). Attempt tracking lives in [DataStorePinAttemptStore].
 */
@Singleton
class VaultMetaStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        suspend fun isVaultCreated(): Boolean = read()[VAULT_CREATED] != null

        suspend fun saveHardwareWrap(wrap: HardwareWrap) {
            dataStore.edit {
                it[WRAP_A_NONCE] = wrap.nonce.b64()
                it[WRAP_A_CT] = wrap.ciphertext.b64()
            }
        }

        suspend fun hardwareWrap(): HardwareWrap? {
            val prefs = read()
            val nonce = prefs[WRAP_A_NONCE]
            val ct = prefs[WRAP_A_CT]
            return if (nonce != null && ct != null) {
                HardwareWrap(nonce.unB64(), ct.unB64())
            } else {
                null
            }
        }

        suspend fun clearHardwareWrap() {
            dataStore.edit {
                it.remove(WRAP_A_NONCE)
                it.remove(WRAP_A_CT)
            }
        }

        suspend fun savePinWrap(wrap: PinWrap) {
            dataStore.edit {
                it[WRAP_B_SALT] = wrap.salt.b64()
                it[WRAP_B_NONCE] = wrap.outerNonce.b64()
                it[WRAP_B_CT] = wrap.outerCiphertext.b64()
                it[VAULT_CREATED] = 1
            }
        }

        suspend fun pinWrap(): PinWrap? {
            val prefs = read()
            val salt = prefs[WRAP_B_SALT]
            val nonce = prefs[WRAP_B_NONCE]
            val ct = prefs[WRAP_B_CT]
            return if (salt != null && nonce != null && ct != null) {
                PinWrap(salt.unB64(), nonce.unB64(), ct.unB64())
            } else {
                null
            }
        }

        suspend fun eraseAll() {
            dataStore.edit { it.clear() }
        }

        private suspend fun read(): Preferences = dataStore.data.first()

        private fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)

        private fun String.unB64(): ByteArray = Base64.getDecoder().decode(this)

        companion object {
            private val VAULT_CREATED = intPreferencesKey("vault_created")
            private val WRAP_A_NONCE = stringPreferencesKey("wrap_a_nonce")
            private val WRAP_A_CT = stringPreferencesKey("wrap_a_ct")
            private val WRAP_B_SALT = stringPreferencesKey("wrap_b_salt")
            private val WRAP_B_NONCE = stringPreferencesKey("wrap_b_nonce")
            private val WRAP_B_CT = stringPreferencesKey("wrap_b_ct")
        }
    }
