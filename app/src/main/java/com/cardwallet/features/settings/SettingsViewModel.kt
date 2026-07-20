package com.cardwallet.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardwallet.data.crypto.VaultKeyManager
import com.cardwallet.data.crypto.VmkState
import com.cardwallet.data.repo.CardRepository
import com.cardwallet.data.security.RootDetector
import com.cardwallet.data.session.SessionStateHolder
import com.cardwallet.data.settings.AutoLockTimeout
import com.cardwallet.data.settings.ClipboardTimeout
import com.cardwallet.data.settings.SettingsStore
import com.cardwallet.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Named

private const val SUBSCRIPTION_STOP_MILLIS = 5_000L

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settings: SettingsStore,
        private val vaultKeys: VaultKeyManager,
        private val session: SessionStateHolder,
        private val repository: CardRepository,
        rootDetector: RootDetector,
        @param:Named("appVersion") private val appVersion: String,
    ) : ViewModel() {
        private val biometricEnabled = MutableStateFlow(false)
        private val confirmingErase = MutableStateFlow(false)
        private val erased = MutableStateFlow(false)
        private val rooted = rootDetector.isLikelyRooted()

        private val preferences =
            combine(
                settings.autoLockTimeout,
                settings.themeMode,
                settings.clipboardTimeout,
                ::StoredPreferences,
            )

        val state: StateFlow<SettingsUiState> =
            combine(
                preferences,
                biometricEnabled,
                confirmingErase,
                erased,
            ) { prefs, biometric, confirming, isErased ->
                SettingsUiState(
                    autoLockTimeout = prefs.autoLockTimeout,
                    themeMode = prefs.themeMode,
                    clipboardTimeout = prefs.clipboardTimeout,
                    isBiometricEnabled = biometric,
                    isDeviceRooted = rooted,
                    appVersion = appVersion,
                    isConfirmingErase = confirming,
                    isErased = isErased,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_MILLIS),
                SettingsUiState.initial(appVersion),
            )

        init {
            refreshBiometricState()
        }

        fun onAutoLockChange(value: AutoLockTimeout) {
            viewModelScope.launch { settings.setAutoLockTimeout(value) }
        }

        fun onThemeChange(value: ThemeMode) {
            viewModelScope.launch { settings.setThemeMode(value) }
        }

        fun onClipboardTimeoutChange(value: ClipboardTimeout) {
            viewModelScope.launch { settings.setClipboardTimeout(value) }
        }

        /** Screen provides the freshly authorized VMK cipher (F6.3 enable). */
        suspend fun acquireEnableCipher(): Cipher? =
            when (val vmk = vaultKeys.prepareCreateCipher()) {
                is VmkState.Ready -> vmk.cipher
                else -> null
            }

        fun onBiometricEnableAuthorized(cipher: Cipher) {
            val dek = session.dekOrNull()?.encoded ?: return
            viewModelScope.launch {
                vaultKeys.enableBiometric(dek, cipher)
                refreshBiometricState()
            }
        }

        fun onBiometricDisable() {
            viewModelScope.launch {
                vaultKeys.disableHardwareWrap()
                refreshBiometricState()
            }
        }

        fun onEraseRequest() {
            confirmingErase.value = true
        }

        fun onEraseDismiss() {
            confirmingErase.value = false
        }

        fun onEraseConfirmed() {
            confirmingErase.value = false
            viewModelScope.launch {
                repository.eraseAll()
                vaultKeys.eraseKeys()
                session.lock()
                erased.value = true
            }
        }

        private fun refreshBiometricState() {
            viewModelScope.launch { biometricEnabled.value = vaultKeys.isBiometricEnabled() }
        }
    }
