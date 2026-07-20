package com.cardwallet.features.settings

import com.cardwallet.data.settings.AutoLockTimeout
import com.cardwallet.data.settings.ThemeMode

data class SettingsUiState(
    val autoLockTimeout: AutoLockTimeout,
    val themeMode: ThemeMode,
    val isBiometricEnabled: Boolean,
    val isDeviceRooted: Boolean,
    val appVersion: String,
    /** Drives the type-to-confirm erase dialog (F6.9). */
    val isConfirmingErase: Boolean = false,
    /** Set after erase completes — the shell returns to onboarding. */
    val isErased: Boolean = false,
) {
    companion object {
        fun initial(appVersion: String) =
            SettingsUiState(
                autoLockTimeout = AutoLockTimeout.ONE_MINUTE,
                themeMode = ThemeMode.SYSTEM,
                isBiometricEnabled = false,
                isDeviceRooted = false,
                appVersion = appVersion,
            )
    }
}
