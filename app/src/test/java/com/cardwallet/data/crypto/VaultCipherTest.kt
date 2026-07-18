package com.cardwallet.data.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class VaultCipherTest {
    private val cipher = VaultCipher()
    private val key = newKey()
    private val plaintext = "4111 1111 1111 1111 · exp 12/29 · cvv 123".toByteArray()

    @Test
    fun `roundtrip returns the original plaintext`() {
        val sealed = cipher.encrypt(key, plaintext)
        assertArrayEquals(plaintext, cipher.decrypt(key, sealed))
    }

    @Test
    fun `flipping any ciphertext bit fails the auth tag`() {
        val sealed = cipher.encrypt(key, plaintext)
        sealed.ciphertext[0] = (sealed.ciphertext[0].toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(key, sealed) }
    }

    @Test
    fun `tampering with the nonce fails the auth tag`() {
        val sealed = cipher.encrypt(key, plaintext)
        sealed.nonce[0] = (sealed.nonce[0].toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(key, sealed) }
    }

    @Test
    fun `decrypting with the wrong key fails the auth tag`() {
        val sealed = cipher.encrypt(key, plaintext)
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(newKey(), sealed) }
    }

    @Test
    fun `nonces are fresh on every encryption`() {
        val seen = HashSet<List<Byte>>()
        repeat(NONCE_SAMPLE_COUNT) {
            val sealed = cipher.encrypt(key, plaintext)
            assertEquals(VaultCipher.NONCE_BYTES, sealed.nonce.size)
            assertTrue("duplicate nonce observed", seen.add(sealed.nonce.toList()))
        }
    }

    @Test
    fun `identical plaintexts produce different ciphertexts`() {
        val first = cipher.encrypt(key, plaintext)
        val second = cipher.encrypt(key, plaintext)
        assertTrue(!first.ciphertext.contentEquals(second.ciphertext))
    }

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(KEY_BITS) }.generateKey()

    private companion object {
        const val KEY_BITS = 256
        const val NONCE_SAMPLE_COUNT = 1_000
    }
}
