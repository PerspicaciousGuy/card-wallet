package com.cardwallet.features.settings.changepin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardwallet.R
import com.cardwallet.ui.components.PinDots
import com.cardwallet.ui.components.PinPad
import com.cardwallet.ui.theme.WalletTheme

private val MESSAGE_SLOT_HEIGHT = 40.dp

@Composable
fun ChangePinScreen(
    onDone: () -> Unit,
    onClose: () -> Unit,
    viewModel: ChangePinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
    }

    ChangePinContent(
        state = state,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onClose = onClose,
    )
}

@Composable
fun ChangePinContent(
    state: ChangePinUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = WalletTheme.tokens.spacing
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(stringResource(R.string.cancel))
            }
        }
        Spacer(Modifier.weight(1f))

        if (state.isWorking) {
            CircularProgressIndicator()
        } else {
            Text(
                text = stringResource(state.stage.titleRes()),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.lg))
            PinDots(filled = state.enteredDigits)
            Box(Modifier.height(MESSAGE_SLOT_HEIGHT), contentAlignment = Alignment.Center) {
                state.message?.let {
                    Text(
                        text = stringResource(it.messageRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(spacing.md))
            PinPad(onDigit = onDigit, onBackspace = onBackspace)
        }

        Spacer(Modifier.weight(1f))
    }
}

private fun ChangePinStage.titleRes() =
    when (this) {
        ChangePinStage.CURRENT -> R.string.change_pin_current
        ChangePinStage.NEW -> R.string.change_pin_new
        ChangePinStage.CONFIRM -> R.string.change_pin_confirm
    }

private fun ChangePinMessage.messageRes() =
    when (this) {
        ChangePinMessage.WRONG_CURRENT -> R.string.change_pin_wrong_current
        ChangePinMessage.PINS_DONT_MATCH -> R.string.lock_pins_dont_match
        ChangePinMessage.BACKED_OFF -> R.string.change_pin_backed_off
    }
