package com.cardwallet.features.lock

import javax.crypto.Cipher

/** Result of a system auth sheet round-trip (see BiometricAuthenticator.kt). */
sealed interface BiometricOutcome {
    data class Authorized(
        val cipher: Cipher,
    ) : BiometricOutcome

    data object Dismissed : BiometricOutcome

    data object Unavailable : BiometricOutcome
}
