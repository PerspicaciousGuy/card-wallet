package com.cardwallet.features.lock.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cardwallet.R

private val KEY_SIZE = 72.dp
private val DOT_SIZE = 14.dp
private const val PIN_LENGTH = 6

/** Six dots showing how many digits are entered — never the digits themselves. */
@Composable
fun PinDots(
    filled: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.semantics {
            contentDescription = "$filled of $PIN_LENGTH digits entered"
        },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(PIN_LENGTH) { index ->
            val color =
                if (index < filled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            Box(
                Modifier
                    .size(DOT_SIZE)
                    .background(color, CircleShape),
            )
        }
    }
}

/** 3×4 digit grid with a backspace key. Targets are 72dp — well over the 48dp floor. */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val rows = listOf("123", "456", "789")
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit ->
                    PinKey(
                        label = digit.toString(),
                        onClick = { onDigit(digit) },
                        isEnabled = isEnabled,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(KEY_SIZE))
            PinKey(label = "0", onClick = { onDigit('0') }, isEnabled = isEnabled)
            PinKey(
                label = "⌫",
                onClick = onBackspace,
                isEnabled = isEnabled,
                description = stringResource(R.string.lock_backspace),
            )
        }
    }
}

@Composable
private fun PinKey(
    label: String,
    onClick: () -> Unit,
    isEnabled: Boolean,
    description: String? = null,
) {
    Box(
        Modifier
            .size(KEY_SIZE)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color =
                if (isEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
