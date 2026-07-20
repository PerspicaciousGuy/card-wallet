package com.cardwallet.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure mapping guards for the persisted-value round trips (F6.1, F6.7). */
class ThemeAndTimeoutMappingTest {
    @Test
    fun `auto-lock millis map back to their enum uniquely`() {
        AutoLockTimeout.entries.forEach { timeout ->
            val roundTrip = AutoLockTimeout.entries.first { it.millis == timeout.millis }
            assertEquals(timeout, roundTrip)
        }
    }

    @Test
    fun `immediately means zero and one minute means 60s`() {
        assertEquals(0L, AutoLockTimeout.IMMEDIATELY.millis)
        assertEquals(ONE_MINUTE_MILLIS, AutoLockTimeout.ONE_MINUTE.millis)
    }

    @Test
    fun `theme names map back to their enum`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.entries.first { it.name == mode.name })
        }
    }

    private companion object {
        const val ONE_MINUTE_MILLIS = 60_000L
    }
}
