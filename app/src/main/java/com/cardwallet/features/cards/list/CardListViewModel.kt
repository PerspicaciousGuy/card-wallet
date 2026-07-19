package com.cardwallet.features.cards.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardwallet.data.repo.CardRepository
import com.cardwallet.data.repo.VaultEntry
import com.cardwallet.domain.Card
import com.cardwallet.domain.CardType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val SUBSCRIPTION_STOP_MILLIS = 5_000L

@HiltViewModel
class CardListViewModel
    @Inject
    constructor(
        repository: CardRepository,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val filter = MutableStateFlow<CardType?>(null)

        val state: StateFlow<CardListUiState> =
            combine(repository.entries, query, filter) { entries, currentQuery, currentFilter ->
                if (entries == null) {
                    CardListUiState.Loading
                } else {
                    buildContent(entries, currentQuery, currentFilter)
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
                CardListUiState.Loading,
            )

        fun onQueryChange(value: String) {
            query.value = value
        }

        fun onFilterChange(value: CardType?) {
            filter.value = value
        }

        private fun buildContent(
            entries: List<VaultEntry>,
            currentQuery: String,
            currentFilter: CardType?,
        ): CardListUiState.Content {
            val readable = entries.filterIsInstance<VaultEntry.Readable>().map { it.card }
            val visible =
                readable
                    .filter { currentFilter == null || it.type == currentFilter }
                    .filter { it.matches(currentQuery) }
                    .sortedWith(
                        compareByDescending<Card> { it.isFavorite }
                            .thenByDescending { it.updatedAt },
                    )
            return CardListUiState.Content(
                query = currentQuery,
                filter = currentFilter,
                cards = visible,
                unreadableIds = entries.filterIsInstance<VaultEntry.Unreadable>().map { it.id },
                isVaultEmpty = entries.isEmpty(),
            )
        }

        /** F4.3: search titles and field LABELS only — values are secrets. */
        private fun Card.matches(query: String): Boolean {
            if (query.isBlank()) return true
            val needle = query.trim()
            return title.contains(needle, ignoreCase = true) ||
                fields.any { it.label.contains(needle, ignoreCase = true) }
        }
    }
