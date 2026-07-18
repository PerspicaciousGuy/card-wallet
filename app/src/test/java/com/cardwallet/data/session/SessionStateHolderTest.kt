package com.cardwallet.data.session

import app.cash.turbine.test
import com.cardwallet.data.crypto.TimeSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeClock(
    var now: Long = 0L,
) : TimeSource {
    override fun nowMillis() = now
}

class SessionStateHolderTest {
    private val clock = FakeClock()
    private val session = SessionStateHolder(clock)

    @Test
    fun `cold start is locked with no key`() {
        assertEquals(SessionState.Locked, session.state.value)
        assertNull(session.dekOrNull())
    }

    @Test
    fun `unlock exposes the key and emits Unlocked`() =
        runTest {
            session.state.test {
                assertEquals(SessionState.Locked, awaitItem())
                session.unlock(testDek())
                assertEquals(SessionState.Unlocked, awaitItem())
                assertNotNull(session.dekOrNull())
            }
        }

    @Test
    fun `lock zeroes the key material and emits Locked`() {
        val dek = testDek()
        session.unlock(dek)
        session.lock()
        assertEquals(SessionState.Locked, session.state.value)
        assertNull(session.dekOrNull())
        assertTrue("DEK bytes must be zeroed on lock", dek.all { it == 0.toByte() })
    }

    @Test
    fun `background shorter than the timeout keeps the session unlocked`() {
        session.unlock(testDek())
        session.onAppBackgrounded()
        clock.now += SessionStateHolder.AUTO_LOCK_TIMEOUT_MILLIS - 1
        session.onAppForegrounded()
        assertEquals(SessionState.Unlocked, session.state.value)
    }

    @Test
    fun `background beyond the timeout locks on return`() {
        session.unlock(testDek())
        session.onAppBackgrounded()
        clock.now += SessionStateHolder.AUTO_LOCK_TIMEOUT_MILLIS
        session.onAppForegrounded()
        assertEquals(SessionState.Locked, session.state.value)
        assertNull(session.dekOrNull())
    }

    @Test
    fun `foreground without a prior background is a no-op`() {
        session.unlock(testDek())
        session.onAppForegrounded()
        assertEquals(SessionState.Unlocked, session.state.value)
    }

    private fun testDek() = ByteArray(DEK_SIZE) { (it + 1).toByte() }

    private companion object {
        const val DEK_SIZE = 32
    }
}
