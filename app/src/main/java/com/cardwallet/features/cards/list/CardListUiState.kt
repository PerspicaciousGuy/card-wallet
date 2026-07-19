package com.cardwallet.features.cards.list

import com.cardwallet.domain.Card
import com.cardwallet.domain.CardType

sealed interface CardListUiState {
    /** Vault not yet decrypted into the session cache. */
    data object Loading : CardListUiState

    data class Content(
        val query: String,
        val filter: CardType?,
        /** Sorted: favorites first, then most recently updated (F4.2). */
        val cards: List<Card>,
        /** Rows that failed decryption — shown, never hidden (F3.3). */
        val unreadableIds: List<String>,
        /** True when the VAULT is empty (F4.8) — distinct from "no matches". */
        val isVaultEmpty: Boolean,
    ) : CardListUiState
}
