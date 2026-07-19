package com.cardwallet.features.cards.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardwallet.data.clipboard.SecretClipboard
import com.cardwallet.data.repo.CardRepository
import com.cardwallet.data.repo.VaultEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIPTION_STOP_MILLIS = 5_000L

@HiltViewModel
class CardDetailViewModel
    @Inject
    constructor(
        private val repository: CardRepository,
        private val clipboard: SecretClipboard,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        // Read by route-arg name (typed routes store args in the handle by property
        // name); toRoute() would drag android.os.Bundle into JVM unit tests.
        private val cardId: String = checkNotNull(savedStateHandle["cardId"]) { "cardId arg missing" }
        private val revealedIndices = MutableStateFlow<Set<Int>>(emptySet())
        private val isConfirmingDelete = MutableStateFlow(false)

        /** [CardDetailUiState.Missing] doubles as the close signal: after a
         *  delete the entry vanishes from the cache and the screen pops. */
        val state: StateFlow<CardDetailUiState> =
            combine(
                repository.entries,
                revealedIndices,
                isConfirmingDelete,
            ) { entries, revealed, confirming ->
                if (entries == null) return@combine CardDetailUiState.Loading
                val card =
                    entries
                        .filterIsInstance<VaultEntry.Readable>()
                        .firstOrNull { it.card.id == cardId }
                        ?.card
                if (card == null) {
                    CardDetailUiState.Missing
                } else {
                    CardDetailUiState.Loaded(
                        card = card,
                        revealedIndices = revealed,
                        isConfirmingDelete = confirming,
                    )
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
                CardDetailUiState.Loading,
            )

        fun toggleReveal(index: Int) {
            revealedIndices.value =
                if (index in revealedIndices.value) {
                    revealedIndices.value - index
                } else {
                    revealedIndices.value + index
                }
        }

        /** F4.6: copies the true value with the sensitive flag + 30s auto-clear;
         *  works without revealing — the value still never renders. */
        fun copyField(index: Int) {
            val loaded = state.value as? CardDetailUiState.Loaded ?: return
            val field = loaded.card.fields.getOrNull(index) ?: return
            clipboard.copy(label = field.label, value = field.value)
        }

        fun requestDelete() {
            isConfirmingDelete.value = true
        }

        fun dismissDelete() {
            isConfirmingDelete.value = false
        }

        fun confirmDelete() {
            isConfirmingDelete.value = false
            viewModelScope.launch { repository.remove(cardId) }
        }
    }
