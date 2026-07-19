package com.cardwallet.features.cards.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.cardwallet.R
import com.cardwallet.domain.Card
import com.cardwallet.domain.CardType
import com.cardwallet.ui.theme.WalletTheme
import com.cardwallet.ui.theme.color
import com.cardwallet.ui.theme.onColor

/** Credit-card aspect ratio (85.60mm × 53.98mm). */
private const val CARD_ASPECT_RATIO = 1.586f
private const val SHEEN_ALPHA = 0.12f

/**
 * F4.1: a card-shaped tile carrying title, type and favorite marker ONLY —
 * secret values never render in the list.
 */
@Composable
fun CardTile(
    card: Card,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = card.color.color()
    val content = card.color.onColor()
    val spacing = WalletTheme.tokens.spacing

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(RoundedCornerShape(spacing.lg))
            .background(accent)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = SHEEN_ALPHA), Color.Transparent),
                ),
            ).semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onClick)
            .padding(spacing.md),
    ) {
        if (card.isFavorite) {
            Text(
                text = stringResource(R.string.favorite_marker),
                style = MaterialTheme.typography.titleMedium,
                color = content,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.headlineMedium,
                color = content,
                maxLines = 2,
            )
            Text(
                text = stringResource(card.type.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.8f),
            )
        }
    }
}

fun CardType.labelRes(): Int =
    when (this) {
        CardType.PAYMENT -> R.string.card_type_payment
        CardType.IDENTITY -> R.string.card_type_identity
        CardType.TRAVEL -> R.string.card_type_travel
        CardType.LOYALTY -> R.string.card_type_loyalty
        CardType.OTHER -> R.string.card_type_other
    }
