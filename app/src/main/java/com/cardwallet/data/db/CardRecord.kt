package com.cardwallet.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The at-rest shape of a card (plan §4): everything the user typed lives inside
 * [ciphertext] (AES-256-GCM over the Card JSON, keyed by the session DEK); only
 * non-identifying metadata needed for ordering and migrations is plaintext.
 */
@Entity(tableName = "cards")
data class CardRecord(
    @PrimaryKey val id: String,
    val schemaVersion: Int,
    val createdAt: String,
    val updatedAt: String,
    /** Base64, unique per write — never reused across encryptions. */
    val nonce: String,
    /** Base64 AES-256-GCM(Card JSON). */
    val ciphertext: String,
)
