package com.cardwallet.features.cards.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardwallet.R
import com.cardwallet.domain.Card
import com.cardwallet.domain.CardField
import com.cardwallet.features.cards.list.components.labelRes
import com.cardwallet.ui.theme.WalletTheme
import com.cardwallet.ui.theme.color
import com.cardwallet.ui.theme.onColor

private const val MASK = "••••••••"

/**
 * Card detail (F4.5–F4.7). Deliberately renders NO glass: revealed secrets must
 * never sit under a backdrop capture (plan §3 rule 4).
 */
@Composable
fun CardDetailScreen(
    onEdit: (String) -> Unit,
    onClose: () -> Unit,
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is CardDetailUiState.Missing) onClose()
    }

    CardDetailContent(
        state = state,
        onToggleReveal = viewModel::toggleReveal,
        onCopyField = viewModel::copyField,
        onRequestDelete = viewModel::requestDelete,
        onDismissDelete = viewModel::dismissDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onEdit = onEdit,
        onClose = onClose,
    )
}

@Composable
fun CardDetailContent(
    state: CardDetailUiState,
    onToggleReveal: (Int) -> Unit,
    onCopyField: (Int) -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onEdit: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = WalletTheme.tokens.spacing
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = spacing.md),
    ) {
        when (state) {
            CardDetailUiState.Loading, CardDetailUiState.Missing ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            is CardDetailUiState.Loaded ->
                LoadedBody(
                    state,
                    onToggleReveal,
                    onCopyField,
                    onRequestDelete,
                    onEdit,
                    onClose,
                )
        }
    }

    if (state is CardDetailUiState.Loaded && state.isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.delete_card_title)) },
            text = { Text(stringResource(R.string.delete_card_body, state.card.title)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(
                        stringResource(R.string.delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LoadedBody(
    state: CardDetailUiState.Loaded,
    onToggleReveal: (Int) -> Unit,
    onCopyField: (Int) -> Unit,
    onRequestDelete: () -> Unit,
    onEdit: (String) -> Unit,
    onClose: () -> Unit,
) {
    val spacing = WalletTheme.tokens.spacing
    val card = state.card

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.back)) }
            TextButton(onClick = { onEdit(card.id) }) { Text(stringResource(R.string.edit)) }
        }

        HeaderBanner(card)

        Column(
            Modifier.padding(top = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            card.fields.forEachIndexed { index, field ->
                FieldRow(
                    field = field,
                    isRevealed = index in state.revealedIndices,
                    onToggleReveal = { onToggleReveal(index) },
                    onCopy = { onCopyField(index) },
                )
            }
        }

        OutlinedButton(
            onClick = onRequestDelete,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.lg),
        ) {
            Text(
                stringResource(R.string.delete_card),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HeaderBanner(card: Card) {
    val spacing = WalletTheme.tokens.spacing
    val accent = card.color.color()
    val content = card.color.onColor()
    Column(
        Modifier
            .fillMaxWidth()
            .background(accent, RoundedCornerShape(spacing.lg))
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        if (card.isFavorite) {
            Text(
                text = stringResource(R.string.favorite_marker),
                style = MaterialTheme.typography.titleMedium,
                color = content,
            )
        }
        Text(
            text = card.title,
            style = MaterialTheme.typography.headlineMedium,
            color = content,
        )
        Text(
            text = stringResource(card.type.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun FieldRow(
    field: CardField,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopy: () -> Unit,
) {
    val spacing = WalletTheme.tokens.spacing
    val showValue = !field.isMasked || isRevealed
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(spacing.md))
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (showValue) field.value else MASK,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row {
                if (field.isMasked) {
                    TextButton(onClick = onToggleReveal) {
                        Text(
                            stringResource(if (isRevealed) R.string.hide else R.string.show),
                        )
                    }
                }
                TextButton(onClick = onCopy) { Text(stringResource(R.string.copy)) }
            }
        }
    }
}
