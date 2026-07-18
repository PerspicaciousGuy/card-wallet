package com.cardwallet.data.crypto

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed interface PinUnlockResult {
    /** Plain class: ByteArray equality is identity, so data-class semantics would lie. */
    class Success(
        val dek: ByteArray,
    ) : PinUnlockResult

    data class WrongPin(
        val gate: AttemptGate,
    ) : PinUnlockResult

    data class TooManyAttempts(
        val remainingMillis: Long,
    ) : PinUnlockResult

    /** Wrap blobs missing/corrupt — vault unrecoverable without backup import. */
    data object VaultCorrupt : PinUnlockResult
}

/**
 * Orchestrates the key hierarchy (plan §3): one DEK, two wraps.
 * Wrap A (hardware): sealed/opened by Keystore VMK ciphers that MUST be
 * authorized through BiometricPrompt.CryptoObject by the caller.
 * Wrap B (PIN): PBKDF2 inner layer + device-bound Keystore outer layer.
 * All entry points are main-safe (kotlin-rules §10).
 */
@Singleton
class VaultKeyManager
    @Inject
    constructor(
        private val keystore: KeystoreManager,
        private val vaultCipher: VaultCipher,
        private val pinKdf: PinKdf,
        private val metaStore: VaultMetaStore,
        private val attempts: PinAttemptTracker,
        @param:Named("io") private val io: CoroutineDispatcher,
    ) {
        private val random = SecureRandom()

        suspend fun isVaultCreated(): Boolean = withContext(io) { metaStore.isVaultCreated() }

        /** True while a hardware wrap exists — i.e. biometric unlock is available. */
        suspend fun isBiometricEnabled(): Boolean = withContext(io) { metaStore.hardwareWrap() != null }

        /** Creates a fresh VMK and returns its encrypt cipher for the creation CryptoObject. */
        suspend fun prepareCreateCipher(): VmkState =
            withContext(io) {
                keystore.createVmk()
                keystore.vmkEncryptCipher()
            }

        /** Decrypt cipher over the stored Wrap A, for the unlock CryptoObject. Null = no wrap. */
        suspend fun prepareBiometricUnlockCipher(): VmkState? =
            withContext(io) {
                val wrap = metaStore.hardwareWrap() ?: return@withContext null
                keystore.vmkDecryptCipher(wrap.nonce)
            }

        /**
         * First-run vault creation. [authorizedVmkCipher] must have passed through
         * a successful BiometricPrompt. Returns the DEK for the session.
         */
        suspend fun createVault(
            pin: CharArray,
            authorizedVmkCipher: Cipher,
        ): ByteArray =
            withContext(io) {
                val dek = ByteArray(DEK_BYTES).also(random::nextBytes)
                val hardwareCiphertext = authorizedVmkCipher.doFinal(dek)
                metaStore.saveHardwareWrap(
                    HardwareWrap(nonce = authorizedVmkCipher.iv, ciphertext = hardwareCiphertext),
                )
                sealPinWrap(dek, pin)
                attempts.recordSuccess()
                dek
            }

        suspend fun unlockWithBiometric(authorizedCipher: Cipher): ByteArray? =
            withContext(io) {
                val wrap = metaStore.hardwareWrap() ?: return@withContext null
                val dek = authorizedCipher.doFinal(wrap.ciphertext)
                attempts.recordSuccess()
                dek
            }

        suspend fun unlockWithPin(pin: CharArray): PinUnlockResult =
            withContext(io) {
                when (val gate = attempts.gate()) {
                    is AttemptGate.Backoff -> return@withContext PinUnlockResult.TooManyAttempts(gate.remainingMillis)
                    AttemptGate.Allowed -> Unit
                }
                val wrap = metaStore.pinWrap() ?: return@withContext PinUnlockResult.VaultCorrupt
                val inner =
                    try {
                        openOuterLayer(wrap)
                    } catch (_: AEADBadTagException) {
                        return@withContext PinUnlockResult.VaultCorrupt
                    }
                try {
                    val kek = pinKdf.deriveKey(pin, wrap.salt)
                    val dek = vaultCipher.decrypt(kek, inner)
                    attempts.recordSuccess()
                    PinUnlockResult.Success(dek)
                } catch (_: AEADBadTagException) {
                    PinUnlockResult.WrongPin(attempts.recordFailure())
                }
            }

        /** F2.5: hardware wrap invalidated — drop it; PIN wrap remains the way in. */
        suspend fun disableHardwareWrap() =
            withContext(io) {
                keystore.deleteVmk()
                metaStore.clearHardwareWrap()
            }

        // Re-enabling biometrics after invalidation (re-sealing Wrap A) arrives
        // with the Settings toggle in Phase 4 — deliberately absent until then.

        private suspend fun sealPinWrap(
            dek: ByteArray,
            pin: CharArray,
        ) {
            val salt = pinKdf.newSalt()
            val kek = pinKdf.deriveKey(pin, salt)
            val inner = vaultCipher.encrypt(kek, dek)
            val innerBlob = inner.nonce + inner.ciphertext
            val outer = keystore.pinWrapEncrypt(innerBlob)
            metaStore.savePinWrap(
                PinWrap(salt = salt, outerNonce = outer.nonce, outerCiphertext = outer.ciphertext),
            )
        }

        private fun openOuterLayer(wrap: PinWrap): EncryptedPayload {
            val innerBlob =
                keystore.pinWrapDecrypt(EncryptedPayload(wrap.outerNonce, wrap.outerCiphertext))
            val nonce = innerBlob.copyOfRange(0, VaultCipher.NONCE_BYTES)
            val ct = innerBlob.copyOfRange(VaultCipher.NONCE_BYTES, innerBlob.size)
            return EncryptedPayload(nonce, ct)
        }

        companion object {
            const val DEK_BYTES = 32
        }
    }
