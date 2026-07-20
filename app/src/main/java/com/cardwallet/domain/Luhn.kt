package com.cardwallet.domain

private const val LUHN_RADIX = 10
private const val DOUBLED_MAX = 9
private const val MIN_CARD_DIGITS = 12
private const val MAX_CARD_DIGITS = 19

/**
 * The Luhn checksum used by payment card numbers (ISO/IEC 7812).
 *
 * Deliberately a *warning* signal, not a validator: loyalty and ID numbers are
 * not Luhn, so a failure means "check this for a typo", never "refuse to save".
 * Blocking on it would eventually stop someone storing a legitimate card.
 */
fun isLuhnValid(input: String): Boolean {
    val digits = input.filter { it.isDigit() }
    if (digits.length !in MIN_CARD_DIGITS..MAX_CARD_DIGITS) return false

    var sum = 0
    // Walk right to left, doubling every second digit.
    digits.reversed().forEachIndexed { index, char ->
        val digit = char - '0'
        sum +=
            if (index % 2 == 1) {
                val doubled = digit * 2
                if (doubled > DOUBLED_MAX) doubled - DOUBLED_MAX else doubled
            } else {
                digit
            }
    }
    return sum % LUHN_RADIX == 0
}

/**
 * True when [field] looks like a payment card number that fails Luhn — i.e.
 * worth warning about. Only applies to PAYMENT cards, and only to a field
 * whose label names a card number, so a "Notes" field of digits stays quiet.
 */
fun shouldWarnLuhn(
    type: CardType,
    field: CardField,
): Boolean {
    val digits = field.value.filter { it.isDigit() }
    return type == CardType.PAYMENT &&
        field.label.isCardNumberLabel() &&
        // Stay silent until enough digits are typed to judge, so the warning
        // does not flash while the user is still entering the number.
        digits.length >= MIN_CARD_DIGITS &&
        !isLuhnValid(digits)
}

private fun String.isCardNumberLabel(): Boolean {
    val normalized = trim().lowercase()
    return normalized == "number" || normalized == "card number"
}
