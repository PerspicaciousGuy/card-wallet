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

    @Test
    fun `clipboard names map back to their enum`() {
        ClipboardTimeout.entries.forEach { timeout ->
            assertEquals(timeout, ClipboardTimeout.entries.first { it.name == timeout.name })
        }
    }

    @Test
    fun `clipboard millis are NOT unique so the store must key on name`() {
        // NEVER and a zero delay collide; this is why SettingsStore persists
        // ClipboardTimeout by name while AutoLockTimeout persists by millis.
        assertEquals(0L, ClipboardTimeout.NEVER.millis)
        val byMillis = ClipboardTimeout.entries.count { it.millis == 0L }
        assertEquals(1, byMillis)
    }

    @Test
    fun `default clipboard timeout is thirty seconds`() {
        assertEquals(THIRTY_SECONDS_MILLIS, ClipboardTimeout.THIRTY_SECONDS.millis)
    }

    private companion object {
        const val ONE_MINUTE_MILLIS = 60_000L
        const val THIRTY_SECONDS_MILLIS = 30_000L
    }
}
