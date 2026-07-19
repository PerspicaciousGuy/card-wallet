package com.cardwallet.features.cards.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardwallet.R
import com.cardwallet.domain.CardType
import com.cardwallet.features.cards.list.components.CardTile
import com.cardwallet.features.cards.list.components.labelRes
import com.cardwallet.ui.theme.WalletTheme

/** Room for the floating glass bar so the last tile scrolls clear of it. */
private val LIST_BOTTOM_INSET = 120.dp

@Composable
fun CardListScreen(
    onOpenCard: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CardListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CardListContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onOpenCard = onOpenCard,
        modifier = modifier,
    )
}

@Composable
fun CardListContent(
    state: CardListUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (CardType?) -> Unit,
    onOpenCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = WalletTheme.tokens.spacing
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.md),
    ) {
        Text(
            text = stringResource(R.string.cards_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = spacing.lg, bottom = spacing.sm),
        )
        when (state) {
            CardListUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            is CardListUiState.Content ->
                if (state.isVaultEmpty) {
                    EmptyVault()
                } else {
                    CardListBody(state, onQueryChange, onFilterChange, onOpenCard)
                }
        }
    }
}

@Composable
private fun CardListBody(
    state: CardListUiState.Content,
    onQueryChange: (String) -> Unit,
    onFilterChange: (CardType?) -> Unit,
    onOpenCard: (String) -> Unit,
) {
    val spacing = WalletTheme.tokens.spacing

    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_cards)) },
        singleLine = true,
        shape = RoundedCornerShape(spacing.lg),
        modifier = Modifier.fillMaxWidth(),
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = Modifier.padding(vertical = spacing.sm),
    ) {
        item(key = "all") {
            FilterChip(
                selected = state.filter == null,
                onClick = { onFilterChange(null) },
                label = { Text(stringResource(R.string.filter_all)) },
            )
        }
        items(CardType.entries, key = { it.name }) { type ->
            FilterChip(
                selected = state.filter == type,
                onClick = { onFilterChange(if (state.filter == type) null else type) },
                label = { Text(stringResource(type.labelRes())) },
            )
        }
    }

    if (state.cards.isEmpty() && state.unreadableIds.isEmpty()) {
        NoMatches()
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        contentPadding = PaddingValues(top = spacing.xs, bottom = LIST_BOTTOM_INSET),
    ) {
        items(state.cards, key = { it.id }) { card ->
            CardTile(card = card, onClick = { onOpenCard(card.id) })
        }
        items(state.unreadableIds, key = { it }) { id ->
            UnreadableRow()
        }
    }
}

@Composable
private fun UnreadableRow() {
    val spacing = WalletTheme.tokens.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(spacing.md))
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(spacing.sm)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
        Text(
            text = stringResource(R.string.unreadable_card),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyVault() {
    val spacing = WalletTheme.tokens.spacing
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.empty_vault_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.empty_vault_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.sm, bottom = LIST_BOTTOM_INSET),
        )
    }
}

@Composable
private fun NoMatches() {
    val spacing = WalletTheme.tokens.spacing
    Text(
        text = stringResource(R.string.no_matching_cards),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = spacing.xl),
    )
}
