package com.cardwallet.features.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cardwallet.ui.glass.LiquidButton
import com.cardwallet.ui.theme.WalletTheme
import com.kyant.backdrop.Backdrop

private val CHIP_HEIGHT = 40.dp

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val spacing = WalletTheme.tokens.spacing
    Column(Modifier.padding(bottom = spacing.lg)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = spacing.sm, bottom = spacing.xs),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(spacing.md))
                // Clip so a row's press ripple stays inside the section's corners
                // instead of painting a square over them.
                .clip(RoundedCornerShape(spacing.md)),
        ) {
            content()
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val spacing = WalletTheme.tokens.spacing
    val clickModifier =
        if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.padding(end = spacing.sm)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * A labeled row of single-choice glass chips — used for auto-lock and theme.
 * The selected chip is tinted with the accent; the rest are plain glass.
 */
@Composable
fun <T> SingleChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val spacing = WalletTheme.tokens.spacing
    Column(modifier.padding(horizontal = spacing.md, vertical = spacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = spacing.xs),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            contentPadding = PaddingValues(vertical = spacing.xs),
        ) {
            items(options) { option ->
                val isSelected = option == selected
                LiquidButton(
                    onClick = { onSelect(option) },
                    backdrop = backdrop,
                    tint =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Unspecified
                        },
                    height = CHIP_HEIGHT,
                    contentPadding = spacing.md,
                ) {
                    Text(
                        text = optionLabel(option),
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        }
    }
}
