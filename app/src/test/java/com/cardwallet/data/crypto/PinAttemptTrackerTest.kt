package com.cardwallet.data.crypto

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAttemptStore : PinAttemptStore {
    var attempts = 0
    var backoffUntil = 0L

    override suspend fun failedAttempts() = attempts

    override suspend fun backoffUntilMillis() = backoffUntil

    override suspend fun recordFailedAttempt(
        count: Int,
        backoffUntilMillis: Long,
    ) {
        attempts = count
        backoffUntil = backoffUntilMillis
    }

    override suspend fun resetAttempts() {
        attempts = 0
        backoffUntil = 0L
    }
}

private class FakeClock(
    var now: Long = 0L,
) : TimeSource {
    override fun nowMillis() = now
}

class PinAttemptTrackerTest {
    private val store = FakeAttemptStore()
    private val clock = FakeClock()
    private val tracker = PinAttemptTracker(store, clock)

    @Test
    fun `first five failures carry no backoff`() =
        runTest {
            repeat(PinAttemptTracker.FREE_ATTEMPTS) {
                assertEquals(AttemptGate.Allowed, tracker.recordFailure())
            }
        }

    @Test
    fun `sixth failure starts a 30s backoff`() =
        runTest {
            repeat(PinAttemptTracker.FREE_ATTEMPTS) { tracker.recordFailure() }
            val gate = tracker.recordFailure()
            assertTrue(gate is AttemptGate.Backoff)
            assertEquals(PinAttemptTracker.BASE_BACKOFF_MILLIS, (gate as AttemptGate.Backoff).remainingMillis)
        }

    @Test
    fun `backoff doubles per failure and caps at 30 minutes`() =
        runTest {
            repeat(PinAttemptTracker.FREE_ATTEMPTS + 1) { tracker.recordFailure() }
            var expected = PinAttemptTracker.BASE_BACKOFF_MILLIS
            repeat(EXTRA_FAILURES) {
                clock.now = store.backoffUntil // wait out the current backoff
                val gate = tracker.recordFailure() as AttemptGate.Backoff
                expected = (expected * 2).coerceAtMost(PinAttemptTracker.MAX_BACKOFF_MILLIS)
                assertEquals(expected, gate.remainingMillis)
            }
            assertEquals(PinAttemptTracker.MAX_BACKOFF_MILLIS, expected)
        }

    @Test
    fun `gate blocks during backoff and opens after it elapses`() =
        runTest {
            repeat(PinAttemptTracker.FREE_ATTEMPTS + 1) { tracker.recordFailure() }
            assertTrue(tracker.gate() is AttemptGate.Backoff)
            clock.now = store.backoffUntil
            assertEquals(AttemptGate.Allowed, tracker.gate())
        }

    @Test
    fun `success resets attempts and backoff`() =
        runTest {
            repeat(PinAttemptTracker.FREE_ATTEMPTS + 2) { tracker.recordFailure() }
            tracker.recordSuccess()
            assertEquals(AttemptGate.Allowed, tracker.gate())
            assertEquals(0, store.attempts)
        }

    private companion object {
        const val EXTRA_FAILURES = 8
    }
}
