package com.cardwallet.data.crypto

import javax.inject.Inject
import javax.inject.Singleton

/** Wall-clock source, injectable so backoff logic is testable. */
fun interface TimeSource {
    fun nowMillis(): Long
}

/** Result of asking whether a PIN attempt is currently allowed. */
sealed interface AttemptGate {
    data object Allowed : AttemptGate

    data class Backoff(
        val remainingMillis: Long,
    ) : AttemptGate
}

/** The slice of persistence the tracker needs; implemented by [VaultMetaStore]. */
interface PinAttemptStore {
    suspend fun failedAttempts(): Int

    suspend fun backoffUntilMillis(): Long

    suspend fun recordFailedAttempt(
        count: Int,
        backoffUntilMillis: Long,
    )

    suspend fun resetAttempts()
}

/**
 * App-level rate limiting for PIN unlock (F2.3): [FREE_ATTEMPTS] free tries,
 * then exponential backoff 30s·2ⁿ capped at 30min. Persisted so process death
 * doesn't reset the clock. This bounds ON-DEVICE guessing; off-device guessing
 * is impossible by construction (device-bound outer wrap, plan §3).
 */
@Singleton
class PinAttemptTracker
    @Inject
    constructor(
        private val store: PinAttemptStore,
        private val time: TimeSource,
    ) {
        suspend fun gate(): AttemptGate {
            val until = store.backoffUntilMillis()
            val now = time.nowMillis()
            return if (now < until) AttemptGate.Backoff(until - now) else AttemptGate.Allowed
        }

        suspend fun recordFailure(): AttemptGate {
            val count = store.failedAttempts() + 1
            val backoffUntil =
                if (count <= FREE_ATTEMPTS) {
                    0L
                } else {
                    val exponent = (count - FREE_ATTEMPTS - 1).coerceAtMost(MAX_EXPONENT)
                    time.nowMillis() + (BASE_BACKOFF_MILLIS shl exponent).coerceAtMost(MAX_BACKOFF_MILLIS)
                }
            store.recordFailedAttempt(count, backoffUntil)
            return gate()
        }

        suspend fun recordSuccess() = store.resetAttempts()

        companion object {
            const val FREE_ATTEMPTS = 5
            const val BASE_BACKOFF_MILLIS = 30_000L
            const val MAX_BACKOFF_MILLIS = 30L * 60 * 1000
            const val MAX_EXPONENT = 6
        }
    }
