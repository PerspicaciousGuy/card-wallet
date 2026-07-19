package com.cardwallet.features.cards.detail

import com.cardwallet.domain.Card

sealed interface CardDetailUiState {
    data object Loading : CardDetailUiState

    /** Card gone (deleted elsewhere) or unreadable — the screen closes itself. */
    data object Missing : CardDetailUiState

    data class Loaded(
        val card: Card,
        /** Indices of masked fields currently revealed; re-masked when the
         *  screen exits (ViewModel death guarantees it, F4.5). */
        val revealedIndices: Set<Int>,
        val isConfirmingDelete: Boolean,
    ) : CardDetailUiState
}
