package com.cardwallet.features.cards.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardwallet.data.repo.CardRepository
import com.cardwallet.domain.CardColorToken
import com.cardwallet.domain.CardField
import com.cardwallet.domain.CardType
import com.cardwallet.domain.NewCard
import com.cardwallet.domain.templateFields
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CardFormViewModel
    @Inject
    constructor(
        private val repository: CardRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        // See CardDetailViewModel: direct arg read keeps unit tests JVM-pure.
        private val cardId: String? = savedStateHandle["cardId"]

        private val _state =
            MutableStateFlow<CardFormUiState>(
                if (cardId == null) newCardState() else CardFormUiState.Loading,
            )
        val state: StateFlow<CardFormUiState> = _state.asStateFlow()

        init {
            if (cardId != null) loadForEdit(cardId)
        }

        /** New cards only. Re-templating replaces fields ONLY while they are
         *  untouched — typed values are never thrown away by a type switch. */
        fun onTypeSelect(type: CardType) {
            update { current ->
                if (current.isEdit || current.type == type) return@update current
                val untouched = current.fields.all { it.value.isBlank() }
                current.copy(
                    type = type,
                    fields = if (untouched) type.templateFields().toDrafts() else current.fields,
                )
            }
        }

        fun onTitleChange(value: String) = update { it.copy(title = value, hasTitleError = false) }

        fun onFieldLabelChange(
            index: Int,
            value: String,
        ) = updateField(index) { it.copy(label = value) }

        fun onFieldValueChange(
            index: Int,
            value: String,
        ) = updateField(index) { it.copy(value = value) }

        fun onFieldMaskToggle(index: Int) = updateField(index) { it.copy(isMasked = !it.isMasked) }

        fun onAddField() = update { it.copy(fields = it.fields + FieldDraft(label = "", value = "", isMasked = false)) }

        fun onRemoveField(index: Int) = update { it.copy(fields = it.fields.filterIndexed { i, _ -> i != index }) }

        fun onColorSelect(color: CardColorToken) = update { it.copy(color = color) }

        fun onFavoriteToggle() = update { it.copy(isFavorite = !it.isFavorite) }

        fun onSave() {
            val current = _state.value as? CardFormUiState.Editing ?: return
            // Valueless fields (untouched template rows) are dropped, not saved.
            val kept = current.fields.filter { it.value.isNotBlank() }
            val titleError = current.title.isBlank()
            val fieldsError = kept.isEmpty()
            if (titleError || fieldsError) {
                _state.value = current.copy(hasTitleError = titleError, hasFieldsError = fieldsError)
                return
            }
            _state.value = current.copy(isSaving = true)
            viewModelScope.launch {
                val fields = kept.map { CardField(it.label.trim(), it.value, it.isMasked) }
                if (cardId == null) {
                    repository.create(
                        NewCard(
                            type = current.type,
                            title = current.title,
                            fields = fields,
                            color = current.color,
                            isFavorite = current.isFavorite,
                        ),
                    )
                } else {
                    val existing = repository.get(cardId)
                    if (existing != null) {
                        repository.update(
                            existing.copy(
                                title = current.title,
                                fields = fields,
                                color = current.color,
                                isFavorite = current.isFavorite,
                            ),
                        )
                    }
                }
                _state.value = current.copy(isSaving = false, isSaved = true)
            }
        }

        private fun loadForEdit(id: String) {
            viewModelScope.launch {
                repository.entries.first { it != null }
                val card = repository.get(id)
                _state.value =
                    if (card == null) {
                        // Card vanished (deleted elsewhere): treat as saved → close.
                        newCardState().copy(isSaved = true)
                    } else {
                        CardFormUiState.Editing(
                            isEdit = true,
                            type = card.type,
                            title = card.title,
                            fields = card.fields.toDrafts(),
                            color = card.color,
                            isFavorite = card.isFavorite,
                        )
                    }
            }
        }

        private fun newCardState() =
            CardFormUiState.Editing(
                isEdit = false,
                type = CardType.PAYMENT,
                title = "",
                fields = CardType.PAYMENT.templateFields().toDrafts(),
                color = CardColorToken.BLUE,
                isFavorite = false,
            )

        private fun update(transform: (CardFormUiState.Editing) -> CardFormUiState.Editing) {
            val current = _state.value as? CardFormUiState.Editing ?: return
            _state.value = transform(current)
        }

        private fun updateField(
            index: Int,
            transform: (FieldDraft) -> FieldDraft,
        ) = update { current ->
            val fields = current.fields.toMutableList()
            val field = fields.getOrNull(index) ?: return@update current
            fields[index] = transform(field)
            current.copy(fields = fields, hasFieldsError = false)
        }

        private fun List<CardField>.toDrafts() = map { FieldDraft(it.label, it.value, it.isMasked) }
    }
