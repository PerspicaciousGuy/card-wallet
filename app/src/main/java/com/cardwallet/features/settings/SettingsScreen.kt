package com.cardwallet.features.settings

import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardwallet.R
import com.cardwallet.data.settings.AutoLockTimeout
import com.cardwallet.data.settings.ThemeMode
import com.cardwallet.features.lock.BiometricOutcome
import com.cardwallet.features.lock.authenticateWithBiometrics
import com.cardwallet.features.settings.components.SettingRow
import com.cardwallet.features.settings.components.SettingsSection
import com.cardwallet.features.settings.components.SingleChoiceRow
import com.cardwallet.ui.theme.WalletTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onChangePin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    val enableTitle = stringResource(R.string.settings_biometric_prompt_title)
    val enableSubtitle = stringResource(R.string.settings_biometric_prompt_subtitle)

    SettingsContent(
        state = state,
        onAutoLockChange = viewModel::onAutoLockChange,
        onThemeChange = viewModel::onThemeChange,
        onBiometricToggle = { enable ->
            if (!enable) {
                viewModel.onBiometricDisable()
            } else {
                val fragmentActivity = activity ?: return@SettingsContent
                scope.launch {
                    val cipher = viewModel.acquireEnableCipher() ?: return@launch
                    val outcome =
                        authenticateWithBiometrics(fragmentActivity, enableTitle, enableSubtitle, cipher)
                    if (outcome is BiometricOutcome.Authorized) {
                        viewModel.onBiometricEnableAuthorized(outcome.cipher)
                    }
                }
            }
        },
        onChangePin = onChangePin,
        onEraseRequest = viewModel::onEraseRequest,
        onEraseDismiss = viewModel::onEraseDismiss,
        onEraseConfirmed = viewModel::onEraseConfirmed,
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onAutoLockChange: (AutoLockTimeout) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onChangePin: () -> Unit,
    onEraseRequest: () -> Unit,
    onEraseDismiss: () -> Unit,
    onEraseConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = WalletTheme.tokens.spacing
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = spacing.md),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = spacing.lg),
        )

        if (state.isDeviceRooted) {
            RootWarning()
        }

        SettingsSection(stringResource(R.string.settings_section_security)) {
            SingleChoiceRow(
                title = stringResource(R.string.settings_auto_lock),
                options = AutoLockTimeout.entries,
                selected = state.autoLockTimeout,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = onAutoLockChange,
            )
            SettingRow(
                title = stringResource(R.string.settings_biometric),
                trailing = {
                    Switch(checked = state.isBiometricEnabled, onCheckedChange = onBiometricToggle)
                },
            )
            SettingRow(
                title = stringResource(R.string.settings_change_pin),
                onClick = onChangePin,
            )
            SettingRow(
                title = stringResource(R.string.settings_screenshot_protection),
                subtitle = stringResource(R.string.settings_screenshot_protection_note),
            )
        }

        SettingsSection(stringResource(R.string.settings_section_appearance)) {
            SingleChoiceRow(
                title = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                selected = state.themeMode,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = onThemeChange,
            )
        }

        SettingsSection(stringResource(R.string.settings_section_about)) {
            SettingRow(
                title = stringResource(R.string.settings_version),
                subtitle = state.appVersion,
            )
            SettingRow(
                title = stringResource(R.string.settings_privacy),
                subtitle = stringResource(R.string.settings_privacy_note),
            )
        }

        SettingsSection(stringResource(R.string.settings_section_danger)) {
            SettingRow(
                title = stringResource(R.string.settings_erase),
                subtitle = stringResource(R.string.settings_erase_note),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = onEraseRequest,
            )
        }
    }

    if (state.isConfirmingErase) {
        EraseDialog(onDismiss = onEraseDismiss, onConfirm = onEraseConfirmed)
    }
}

@Composable
private fun RootWarning() {
    val spacing = WalletTheme.tokens.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(spacing.md))
            .padding(spacing.md),
    ) {
        Text(
            text = stringResource(R.string.settings_root_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun EraseDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val confirmWord = stringResource(R.string.settings_erase_confirm_word)
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_erase_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_erase_body, confirmWord))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = typed.trim().equals(confirmWord, ignoreCase = true),
            ) {
                Text(
                    stringResource(R.string.settings_erase_confirm),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun AutoLockTimeout.labelRes() =
    when (this) {
        AutoLockTimeout.IMMEDIATELY -> R.string.auto_lock_immediately
        AutoLockTimeout.THIRTY_SECONDS -> R.string.auto_lock_30s
        AutoLockTimeout.ONE_MINUTE -> R.string.auto_lock_1m
        AutoLockTimeout.FIVE_MINUTES -> R.string.auto_lock_5m
    }

private fun ThemeMode.labelRes() =
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

private tailrec fun android.content.Context.findFragmentActivity(): FragmentActivity? =
    when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
