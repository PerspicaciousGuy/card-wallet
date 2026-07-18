package com.cardwallet.data.crypto

import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives the PIN-wrap KEK: PBKDF2-HMAC-SHA256, 600k iterations (OWASP-level
 * for SHA-256), per-install random salt. The derived key is only ever used to
 * open the INNER layer of Wrap B — the outer layer is a device-bound Keystore
 * key, so PIN guessing cannot run off-device (plan §3).
 *
 * `iterations` is injectable so unit tests don't pay the full cost.
 */
@Singleton
class PinKdf(
    private val iterations: Int,
) {
    @Inject
    constructor() : this(DEFAULT_ITERATIONS)

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun deriveKey(
        pin: CharArray,
        salt: ByteArray,
    ): SecretKey {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        try {
            val derived = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
            return SecretKeySpec(derived, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val DEFAULT_ITERATIONS = 600_000
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
    }
}
