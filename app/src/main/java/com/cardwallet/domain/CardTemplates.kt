package com.cardwallet.domain

/**
 * Per-type starting fields for the add-card form (F4.9). These are suggestions,
 * not schema: every label is editable and fields can be added/removed (F4.10).
 * English labels are acceptable v1 — once saved they are user data.
 */
fun CardType.templateFields(): List<CardField> =
    when (this) {
        CardType.PAYMENT ->
            listOf(
                CardField(label = "Number", value = "", isMasked = true),
                CardField(label = "Expiry", value = "", isMasked = false),
                CardField(label = "CVV", value = "", isMasked = true),
                CardField(label = "Cardholder name", value = "", isMasked = false),
                CardField(label = "Notes", value = "", isMasked = false),
            )
        CardType.IDENTITY ->
            listOf(
                CardField(label = "ID number", value = "", isMasked = true),
                CardField(label = "Full name", value = "", isMasked = false),
                CardField(label = "Issued", value = "", isMasked = false),
                CardField(label = "Expires", value = "", isMasked = false),
                CardField(label = "Notes", value = "", isMasked = false),
            )
        CardType.TRAVEL ->
            listOf(
                CardField(label = "Document no.", value = "", isMasked = true),
                CardField(label = "Full name", value = "", isMasked = false),
                CardField(label = "Nationality", value = "", isMasked = false),
                CardField(label = "Issued", value = "", isMasked = false),
                CardField(label = "Expires", value = "", isMasked = false),
                CardField(label = "Notes", value = "", isMasked = false),
            )
        CardType.LOYALTY ->
            listOf(
                CardField(label = "Member ID", value = "", isMasked = false),
                CardField(label = "Program name", value = "", isMasked = false),
                CardField(label = "Notes", value = "", isMasked = false),
            )
        CardType.OTHER -> emptyList()
    }
