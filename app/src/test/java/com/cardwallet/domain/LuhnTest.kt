package com.cardwallet.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuhnTest {
    @Test
    fun `accepts well-known valid test numbers`() {
        // Standard issuer test numbers — valid checksums, not real accounts.
        assertTrue(isLuhnValid("4111111111111111"))
        assertTrue(isLuhnValid("5500005555555559"))
        assertTrue(isLuhnValid("378282246310005"))
    }

    @Test
    fun `rejects a single-digit typo`() {
        assertFalse(isLuhnValid("4111111111111112"))
    }

    @Test
    fun `ignores spaces and dashes in formatting`() {
        assertTrue(isLuhnValid("4111 1111 1111 1111"))
        assertTrue(isLuhnValid("4111-1111-1111-1111"))
    }

    @Test
    fun `rejects numbers outside plausible card length`() {
        assertFalse(isLuhnValid("42"))
        assertFalse(isLuhnValid("41111111111111111111111"))
    }

    @Test
    fun `warns for a payment card number that fails the checksum`() {
        val field = CardField(label = "Number", value = "4111111111111112", isMasked = true)
        assertTrue(shouldWarnLuhn(CardType.PAYMENT, field))
    }

    @Test
    fun `stays quiet for a valid payment card number`() {
        val field = CardField(label = "Number", value = "4111111111111111", isMasked = true)
        assertFalse(shouldWarnLuhn(CardType.PAYMENT, field))
    }

    @Test
    fun `stays quiet while the number is still being typed`() {
        val field = CardField(label = "Number", value = "41111", isMasked = true)
        assertFalse(shouldWarnLuhn(CardType.PAYMENT, field))
    }

    @Test
    fun `does not warn on non-payment cards`() {
        // Loyalty and ID numbers are not Luhn — warning on them would be noise.
        val field = CardField(label = "Number", value = "123456789012", isMasked = true)
        assertFalse(shouldWarnLuhn(CardType.LOYALTY, field))
        assertFalse(shouldWarnLuhn(CardType.IDENTITY, field))
    }

    @Test
    fun `does not warn on other fields of a payment card`() {
        // A long digit string in Notes is not a card number.
        val notes = CardField(label = "Notes", value = "123456789012", isMasked = false)
        assertFalse(shouldWarnLuhn(CardType.PAYMENT, notes))
    }
}
