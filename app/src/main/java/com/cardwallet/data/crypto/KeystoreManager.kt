package com.cardwallet.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Why a VMK cipher could not be prepared. */
sealed interface VmkState {
    data class Ready(
        val cipher: Cipher,
    ) : VmkState

    /** Key was invalidated (enrollment change / lock-screen reset). Recovery = PIN unlock (F2.5). */
    data object PermanentlyInvalidated : VmkState

    data object Missing : VmkState
}

/**
 * Owns the two AndroidKeyStore keys (plan §3):
 *  - VMK — auth-per-use; every use must be authorized through
 *    BiometricPrompt.CryptoObject (biometric or device credential).
 *  - PIN-wrap outer key — no user-auth requirement, but device-bound and
 *    non-extractable: it forces PIN guessing to run on this device.
 *
 * Both try StrongBox first and fall back to TEE.
 */
@Singleton
class KeystoreManager
    @Inject
    constructor() {
        private val keyStore: KeyStore =
            KeyStore.getInstance(PROVIDER).apply { load(null) }

        // --- VMK (hardware wrap, auth-gated) ---

        fun createVmk(): SecretKey {
            keyStore.deleteEntry(VMK_ALIAS)
            return generateKey(VMK_ALIAS) {
                setUserAuthenticationRequired(true)
                setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
            }
        }

        fun deleteVmk() = keyStore.deleteEntry(VMK_ALIAS)

        /** Encrypt-mode cipher for sealing Wrap A. Keystore generates the IV. */
        fun vmkEncryptCipher(): VmkState {
            val key = keyStore.getKey(VMK_ALIAS, null) as? SecretKey ?: return VmkState.Missing
            return try {
                val cipher = Cipher.getInstance(VaultCipher.TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key)
                VmkState.Ready(cipher)
            } catch (_: KeyPermanentlyInvalidatedException) {
                VmkState.PermanentlyInvalidated
            }
        }

        /** Decrypt-mode cipher for opening Wrap A with its stored nonce. */
        fun vmkDecryptCipher(nonce: ByteArray): VmkState {
            val key = keyStore.getKey(VMK_ALIAS, null) as? SecretKey ?: return VmkState.Missing
            return try {
                val cipher = Cipher.getInstance(VaultCipher.TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(VaultCipher.TAG_BITS, nonce))
                VmkState.Ready(cipher)
            } catch (_: KeyPermanentlyInvalidatedException) {
                VmkState.PermanentlyInvalidated
            }
        }

        // --- PIN-wrap outer key (device-bound, no auth) ---

        /**
         * Keystore keys forbid caller-provided IVs on encrypt (nonce-reuse
         * safeguard), so the outer-layer ciphers live here: encrypt lets the
         * Keystore generate the IV; decrypt replays the stored one.
         */
        fun pinWrapEncrypt(plaintext: ByteArray): EncryptedPayload {
            val cipher = Cipher.getInstance(VaultCipher.TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, pinWrapKey())
            return EncryptedPayload(nonce = cipher.iv, ciphertext = cipher.doFinal(plaintext))
        }

        fun pinWrapDecrypt(payload: EncryptedPayload): ByteArray {
            val cipher = Cipher.getInstance(VaultCipher.TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                pinWrapKey(),
                GCMParameterSpec(VaultCipher.TAG_BITS, payload.nonce),
            )
            return cipher.doFinal(payload.ciphertext)
        }

        private fun pinWrapKey(): SecretKey {
            (keyStore.getKey(PIN_WRAP_ALIAS, null) as? SecretKey)?.let { return it }
            return generateKey(PIN_WRAP_ALIAS) { /* no auth requirement */ }
        }

        fun deleteAll() {
            keyStore.deleteEntry(VMK_ALIAS)
            keyStore.deleteEntry(PIN_WRAP_ALIAS)
        }

        private fun generateKey(
            alias: String,
            configure: KeyGenParameterSpec.Builder.() -> Unit,
        ): SecretKey {
            val purposes = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            val builder =
                KeyGenParameterSpec
                    .Builder(alias, purposes)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .apply(configure)
            return try {
                generate(builder.setIsStrongBoxBacked(true).build())
            } catch (_: StrongBoxUnavailableException) {
                generate(builder.setIsStrongBoxBacked(false).build())
            }
        }

        private fun generate(spec: KeyGenParameterSpec): SecretKey {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            generator.init(spec)
            return generator.generateKey()
        }

        companion object {
            const val PROVIDER = "AndroidKeyStore"
            const val VMK_ALIAS = "cardwallet_vmk"
            const val PIN_WRAP_ALIAS = "cardwallet_pin_wrap"
            const val KEY_BITS = 256
        }
    }
