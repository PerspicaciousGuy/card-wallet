package com.cardwallet.features.cards.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardwallet.R
import com.cardwallet.domain.CardColorToken
import com.cardwallet.domain.CardType
import com.cardwallet.features.cards.list.components.labelRes
import com.cardwallet.ui.theme.WalletTheme
import com.cardwallet.ui.theme.color

private val COLOR_DOT_SIZE = 40.dp
private val COLOR_RING_WIDTH = 3.dp

/** Add/edit form (F4.9–F4.12): per-type templates, editable custom fields,
 *  token color picker, inline validation, IME-aware. */
@Composable
fun CardFormScreen(
    onClose: () -> Unit,
    viewModel: CardFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if ((state as? CardFormUiState.Editing)?.isSaved == true) onClose()
    }

    CardFormContent(
        state = state,
        onTypeSelect = viewModel::onTypeSelect,
        onTitleChange = viewModel::onTitleChange,
        onFieldLabelChange = viewModel::onFieldLabelChange,
        onFieldValueChange = viewModel::onFieldValueChange,
        onFieldMaskToggle = viewModel::onFieldMaskToggle,
        onAddField = viewModel::onAddField,
        onRemoveField = viewModel::onRemoveField,
        onColorSelect = viewModel::onColorSelect,
        onFavoriteToggle = viewModel::onFavoriteToggle,
        onSave = viewModel::onSave,
        onClose = onClose,
    )
}

@Composable
fun CardFormContent(
    state: CardFormUiState,
    onTypeSelect: (CardType) -> Unit,
    onTitleChange: (String) -> Unit,
    onFieldLabelChange: (Int, String) -> Unit,
    onFieldValueChange: (Int, String) -> Unit,
    onFieldMaskToggle: (Int) -> Unit,
    onAddField: () -> Unit,
    onRemoveField: (Int) -> Unit,
    onColorSelect: (CardColorToken) -> Unit,
    onFavoriteToggle: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = WalletTheme.tokens.spacing
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = spacing.md),
    ) {
        when (state) {
            CardFormUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            is CardFormUiState.Editing ->
                EditingBody(
                    state = state,
                    onTypeSelect = onTypeSelect,
                    onTitleChange = onTitleChange,
                    onFieldLabelChange = onFieldLabelChange,
                    onFieldValueChange = onFieldValueChange,
                    onFieldMaskToggle = onFieldMaskToggle,
                    onAddField = onAddField,
                    onRemoveField = onRemoveField,
                    onColorSelect = onColorSelect,
                    onFavoriteToggle = onFavoriteToggle,
                    onSave = onSave,
                    onClose = onClose,
                )
        }
    }
}

@Composable
private fun EditingBody(
    state: CardFormUiState.Editing,
    onTypeSelect: (CardType) -> Unit,
    onTitleChange: (String) -> Unit,
    onFieldLabelChange: (Int, String) -> Unit,
    onFieldValueChange: (Int, String) -> Unit,
    onFieldMaskToggle: (Int) -> Unit,
    onAddField: () -> Unit,
    onRemoveField: (Int) -> Unit,
    onColorSelect: (CardColorToken) -> Unit,
    onFavoriteToggle: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val spacing = WalletTheme.tokens.spacing
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                Text(stringResource(R.string.save))
            }
        }

        Text(
            text =
                stringResource(
                    if (state.isEdit) R.string.edit_card_title else R.string.add_card,
                ),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = spacing.md),
        )

        if (!state.isEdit) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.padding(bottom = spacing.sm),
            ) {
                items(CardType.entries, key = { it.name }) { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onTypeSelect(type) },
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.card_title_label)) },
            isError = state.hasTitleError,
            supportingText =
                if (state.hasTitleError) {
                    { Text(stringResource(R.string.error_title_required)) }
                } else {
                    null
                },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ColorPicker(
            selected = state.color,
            onSelect = onColorSelect,
            modifier = Modifier.padding(vertical = spacing.md),
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.mark_favorite),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Switch(checked = state.isFavorite, onCheckedChange = { onFavoriteToggle() })
        }

        if (state.hasFieldsError) {
            Text(
                text = stringResource(R.string.error_fields_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = spacing.xs),
            )
        }

        state.fields.forEachIndexed { index, field ->
            FieldEditor(
                field = field,
                onLabelChange = { onFieldLabelChange(index, it) },
                onValueChange = { onFieldValueChange(index, it) },
                onMaskToggle = { onFieldMaskToggle(index) },
                onRemove = { onRemoveField(index) },
                modifier = Modifier.padding(top = spacing.sm),
            )
        }

        OutlinedButton(
            onClick = onAddField,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.lg),
        ) {
            Text(stringResource(R.string.add_field))
        }
    }
}

@Composable
private fun ColorPicker(
    selected: CardColorToken,
    onSelect: (CardColorToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = WalletTheme.tokens.spacing
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        CardColorToken.entries.forEach { token ->
            val isSelected = token == selected
            val description = stringResource(R.string.card_color, token.name)
            Box(
                Modifier
                    .size(COLOR_DOT_SIZE)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                COLOR_RING_WIDTH,
                                MaterialTheme.colorScheme.onBackground,
                                CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    ).padding(COLOR_RING_WIDTH + 2.dp)
                    .background(token.color(), CircleShape)
                    .clickable(role = Role.Button) { onSelect(token) }
                    .semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun FieldEditor(
    field: FieldDraft,
    onLabelChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onMaskToggle: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = WalletTheme.tokens.spacing
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(spacing.md))
            .padding(spacing.sm),
    ) {
        OutlinedTextField(
            value = field.label,
            onValueChange = onLabelChange,
            label = { Text(stringResource(R.string.field_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = field.value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.field_value)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = field.isMasked, onCheckedChange = { onMaskToggle() })
                Text(
                    text = stringResource(R.string.field_masked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = spacing.xs),
                )
            }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_field)) }
        }
    }
}
