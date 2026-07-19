package com.cardwallet.features.cards.form

import com.cardwallet.domain.CardColorToken
import com.cardwallet.domain.CardType

/** One editable field row; labels are as editable as values (F4.10). */
data class FieldDraft(
    val label: String,
    val value: String,
    val isMasked: Boolean,
)

sealed interface CardFormUiState {
    /** Edit mode waiting for the session cache. */
    data object Loading : CardFormUiState

    data class Editing(
        val isEdit: Boolean,
        val type: CardType,
        val title: String,
        val fields: List<FieldDraft>,
        val color: CardColorToken,
        val isFavorite: Boolean,
        val hasTitleError: Boolean = false,
        /** F4.12: at least one field must carry a value. */
        val hasFieldsError: Boolean = false,
        val isSaving: Boolean = false,
        val isSaved: Boolean = false,
    ) : CardFormUiState
}
