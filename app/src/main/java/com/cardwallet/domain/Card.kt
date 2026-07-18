package com.cardwallet.domain

import kotlinx.serialization.Serializable

/** Card categories; each drives a field template in the form (F4.9). */
enum class CardType {
    PAYMENT,
    IDENTITY,
    TRAVEL,
    LOYALTY,
    OTHER,
}

/**
 * The color a user assigns to a card (F4.11). Stored as this stable token key —
 * never a raw hex — so the palette can change in one place (ui/theme resolves
 * tokens to actual colors; domain knows nothing about rendering).
 */
enum class CardColorToken {
    BLUE,
    GREEN,
    ROSE,
    AMBER,
    AQUA,
    ORANGE,
    VIOLET,
    RED,
}

@Serializable
data class CardField(
    val label: String,
    val value: String,
    val isMasked: Boolean,
)

/**
 * The decrypted domain type the whole app works with (plan §4). The FULL card —
 * including id and timestamps — lives inside the ciphertext; the repository
 * verifies payload id == row id on read, so a swapped row fails closed.
 */
@Serializable
data class Card(
    val id: String,
    val type: CardType,
    val title: String,
    val fields: List<CardField>,
    val color: CardColorToken,
    val isFavorite: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

/** What the add-card form produces; the repository assigns id + timestamps. */
data class NewCard(
    val type: CardType,
    val title: String,
    val fields: List<CardField>,
    val color: CardColorToken,
    val isFavorite: Boolean = false,
)
