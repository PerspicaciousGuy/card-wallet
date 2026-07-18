package com.cardwallet.data.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Nonce + ciphertext pair. Not a data class: ByteArray equality is identity. */
class EncryptedPayload(
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

/**
 * The only place in the app that touches [Cipher] for data encryption
 * (plan §3 rule 10). AES-256-GCM with a fresh random 96-bit nonce per call;
 * the 128-bit auth tag makes every decrypt a tamper check — [decrypt] throws
 * [javax.crypto.AEADBadTagException] on any modification.
 */
@Singleton
class VaultCipher
    @Inject
    constructor() {
        private val random = SecureRandom()

        fun encrypt(
            key: SecretKey,
            plaintext: ByteArray,
        ): EncryptedPayload {
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            return EncryptedPayload(nonce, cipher.doFinal(plaintext))
        }

        fun decrypt(
            key: SecretKey,
            payload: EncryptedPayload,
        ): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, payload.nonce))
            return cipher.doFinal(payload.ciphertext)
        }

        companion object {
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val NONCE_BYTES = 12
            const val TAG_BITS = 128
        }
    }
