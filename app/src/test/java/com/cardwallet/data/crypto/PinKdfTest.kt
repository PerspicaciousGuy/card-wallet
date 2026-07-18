package com.cardwallet.data.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PinKdfTest {
    // Low iteration count: these tests exercise derivation logic, not KDF cost.
    private val kdf = PinKdf(iterations = TEST_ITERATIONS)

    @Test
    fun `same pin and salt derive the same key`() {
        val salt = kdf.newSalt()
        val first = kdf.deriveKey("482913".toCharArray(), salt)
        val second = kdf.deriveKey("482913".toCharArray(), salt)
        assertArrayEquals(first.encoded, second.encoded)
    }

    @Test
    fun `different pins derive different keys`() {
        val salt = kdf.newSalt()
        val first = kdf.deriveKey("482913".toCharArray(), salt)
        val second = kdf.deriveKey("482914".toCharArray(), salt)
        assertFalse(first.encoded.contentEquals(second.encoded))
    }

    @Test
    fun `different salts derive different keys for the same pin`() {
        val first = kdf.deriveKey("482913".toCharArray(), kdf.newSalt())
        val second = kdf.deriveKey("482913".toCharArray(), kdf.newSalt())
        assertFalse(first.encoded.contentEquals(second.encoded))
    }

    @Test
    fun `derived key is 256 bits of AES material`() {
        val key = kdf.deriveKey("482913".toCharArray(), kdf.newSalt())
        assertEquals(PinKdf.KEY_BITS / Byte.SIZE_BITS, key.encoded.size)
        assertEquals("AES", key.algorithm)
    }

    @Test
    fun `salts are random and correctly sized`() {
        val first = kdf.newSalt()
        val second = kdf.newSalt()
        assertEquals(PinKdf.SALT_BYTES, first.size)
        assertFalse(first.contentEquals(second))
    }

    private companion object {
        const val TEST_ITERATIONS = 1_000
    }
}
