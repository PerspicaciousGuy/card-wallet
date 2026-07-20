package com.cardwallet.features.lock

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardwallet.R
import com.cardwallet.ui.components.PinDots
import com.cardwallet.ui.components.PinPad
import com.cardwallet.ui.glass.LiquidButton
import com.cardwallet.ui.theme.WalletTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

private val MESSAGE_SLOT_HEIGHT = 48.dp

/** Stateful wrapper: owns the BiometricPrompt launches; the ViewModel never sees the activity. */
@Composable
fun LockScreen(viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    var autoFiredBiometric by remember { mutableStateOf(false) }

    val unlockTitle = stringResource(R.string.lock_unlock_prompt_title)
    val unlockSubtitle = stringResource(R.string.lock_unlock_prompt_subtitle)
    val sealTitle = stringResource(R.string.lock_seal_prompt_title)
    val sealSubtitle = stringResource(R.string.lock_seal_prompt_subtitle)

    // Auto-fire the system sheet at most ONCE per lock (the composition is
    // recreated on every lock), and never while the user is typing a PIN —
    // popping the sheet mid-entry would swallow their taps.
    LaunchedEffect(state) {
        when (val current = state) {
            is LockUiState.Locked ->
                if (!autoFiredBiometric && current.canAutoFireBiometric()) {
                    autoFiredBiometric = true
                    runUnlockAuth(activity, viewModel, unlockTitle, unlockSubtitle)
                }
            is LockUiState.AwaitCreateAuth ->
                if (current.message == null) {
                    runCreateAuth(activity, viewModel, sealTitle, sealSubtitle)
                }
            else -> Unit
        }
    }

    LockScreenContent(
        state = state,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onRetryBiometric = {
            scope.launch { runUnlockAuth(activity, viewModel, unlockTitle, unlockSubtitle) }
        },
        onRetryCreateAuth = {
            scope.launch { runCreateAuth(activity, viewModel, sealTitle, sealSubtitle) }
        },
        onOpenSecuritySettings = {
            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        },
        onRecheckSecureLock = viewModel::refresh,
    )
}

private fun LockUiState.Locked.canAutoFireBiometric(): Boolean =
    isBiometricEnabled && backoffRemainingSeconds == 0 && message == null && enteredDigits == 0

private suspend fun runUnlockAuth(
    activity: FragmentActivity?,
    viewModel: LockViewModel,
    title: String,
    subtitle: String,
) {
    val fragmentActivity = activity ?: return
    val cipher = viewModel.acquireUnlockCipher() ?: return
    when (val outcome = authenticateWithBiometrics(fragmentActivity, title, subtitle, cipher)) {
        is BiometricOutcome.Authorized -> viewModel.onUnlockAuthorized(outcome.cipher)
        BiometricOutcome.Dismissed, BiometricOutcome.Unavailable -> viewModel.onAuthDismissed()
    }
}

private suspend fun runCreateAuth(
    activity: FragmentActivity?,
    viewModel: LockViewModel,
    title: String,
    subtitle: String,
) {
    val fragmentActivity = activity ?: return
    val cipher = viewModel.acquireCreateCipher() ?: return
    when (val outcome = authenticateWithBiometrics(fragmentActivity, title, subtitle, cipher)) {
        is BiometricOutcome.Authorized -> viewModel.onCreateAuthorized(outcome.cipher)
        BiometricOutcome.Dismissed, BiometricOutcome.Unavailable -> viewModel.onAuthDismissed()
    }
}

@Composable
fun LockScreenContent(
    state: LockUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onRetryBiometric: () -> Unit,
    onRetryCreateAuth: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onRecheckSecureLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = WalletTheme.tokens.spacing
    val backgroundColor = MaterialTheme.colorScheme.background
    // Sibling capture, not ancestor capture — see SettingsContent for why.
    // (Capturing content would also be safe here privacy-wise: this screen
    // shows a dot COUNT, never PIN digits. The cycle is the reason, not privacy.)
    val backdrop = rememberLayerBackdrop()

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(backdrop)
                .background(backgroundColor),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state) {
                LockUiState.Loading, LockUiState.Unlocking -> CircularProgressIndicator()

                LockUiState.SecureLockMissing -> {
                    Title(stringResource(R.string.lock_secure_missing_title))
                    Body(stringResource(R.string.lock_secure_missing_body))
                    Spacer(Modifier.height(spacing.lg))
                    LiquidButton(
                        onClick = onOpenSecuritySettings,
                        backdrop = backdrop,
                        tint = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            stringResource(R.string.lock_open_settings),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.height(spacing.sm))
                    LiquidButton(onClick = onRecheckSecureLock, backdrop = backdrop) {
                        Text(
                            stringResource(R.string.lock_recheck),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                is LockUiState.CreatePin -> {
                    val title =
                        when (state.stage) {
                            CreateStage.ENTER -> stringResource(R.string.lock_create_title)
                            CreateStage.CONFIRM -> stringResource(R.string.lock_confirm_title)
                        }
                    Title(title)
                    Body(stringResource(R.string.lock_create_subtitle))
                    Spacer(Modifier.height(spacing.lg))
                    PinDots(filled = state.enteredDigits)
                    MessageSlot { MessageText(state.message) }
                    Spacer(Modifier.height(spacing.lg))
                    PinPad(onDigit = onDigit, onBackspace = onBackspace, backdrop = backdrop)
                }

                is LockUiState.AwaitCreateAuth -> {
                    Title(stringResource(R.string.lock_seal_prompt_title))
                    Body(stringResource(R.string.lock_seal_prompt_subtitle))
                    MessageText(state.message)
                    Spacer(Modifier.height(spacing.lg))
                    LiquidButton(
                        onClick = onRetryCreateAuth,
                        backdrop = backdrop,
                        tint = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            stringResource(R.string.lock_retry_auth),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                is LockUiState.Locked -> {
                    Title(stringResource(R.string.lock_title))
                    Spacer(Modifier.height(spacing.lg))
                    PinDots(filled = state.enteredDigits)
                    MessageSlot {
                        if (state.backoffRemainingSeconds > 0) {
                            Body(
                                stringResource(R.string.lock_backoff, state.backoffRemainingSeconds),
                            )
                        } else {
                            MessageText(state.message)
                        }
                    }
                    Spacer(Modifier.height(spacing.lg))
                    PinPad(
                        onDigit = onDigit,
                        onBackspace = onBackspace,
                        backdrop = backdrop,
                        isEnabled = state.backoffRemainingSeconds == 0,
                    )
                    // Fixed-height slot: the pad must not jump when the button appears.
                    MessageSlot {
                        if (state.isBiometricEnabled) {
                            LiquidButton(onClick = onRetryBiometric, backdrop = backdrop) {
                                Text(
                                    stringResource(R.string.lock_use_biometric),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Reserves constant height so the pad never shifts as messages come and go. */
@Composable
private fun MessageSlot(content: @Composable () -> Unit) {
    Box(
        Modifier.height(MESSAGE_SLOT_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun MessageText(message: LockMessage?) {
    if (message == null) return
    val text =
        when (message) {
            LockMessage.WRONG_PIN -> stringResource(R.string.lock_wrong_pin)
            LockMessage.PINS_DONT_MATCH -> stringResource(R.string.lock_pins_dont_match)
            LockMessage.AUTH_CANCELED -> stringResource(R.string.lock_auth_canceled)
            LockMessage.BIOMETRIC_KEY_INVALIDATED ->
                stringResource(R.string.lock_biometric_invalidated)
            LockMessage.VAULT_CORRUPT -> stringResource(R.string.lock_vault_corrupt)
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? =
    when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
