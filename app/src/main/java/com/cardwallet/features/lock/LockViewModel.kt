package com.cardwallet.features.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardwallet.data.crypto.DeviceSecurity
import com.cardwallet.data.crypto.PinUnlockResult
import com.cardwallet.data.crypto.VaultKeyManager
import com.cardwallet.data.crypto.VmkState
import com.cardwallet.data.session.SessionStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

private const val PIN_LENGTH = 6
private const val ONE_SECOND_MILLIS = 1_000L

@HiltViewModel
class LockViewModel
    @Inject
    constructor(
        private val vaultKeys: VaultKeyManager,
        private val session: SessionStateHolder,
        private val deviceSecurity: DeviceSecurity,
    ) : ViewModel() {
        private val _state = MutableStateFlow<LockUiState>(LockUiState.Loading)
        val state: StateFlow<LockUiState> = _state.asStateFlow()

        private val pinBuffer = StringBuilder()
        private var firstEntry: CharArray? = null
        private var pendingCreatePin: CharArray? = null
        private var backoffJob: Job? = null

        init {
            refresh()
        }

        /** Re-evaluated on entry and when returning from system settings (F1.2). */
        fun refresh() {
            viewModelScope.launch {
                pinBuffer.clear()
                if (!deviceSecurity.isDeviceSecure()) {
                    _state.value = LockUiState.SecureLockMissing
                    return@launch
                }
                if (!vaultKeys.isVaultCreated()) {
                    _state.value = LockUiState.CreatePin(CreateStage.ENTER, 0)
                    return@launch
                }
                enterLocked(message = null)
            }
        }

        fun onDigit(digit: Char) {
            if (pinBuffer.length >= PIN_LENGTH) return
            pinBuffer.append(digit)
            updateDigitCount()
            if (pinBuffer.length == PIN_LENGTH) submitPin()
        }

        fun onBackspace() {
            if (pinBuffer.isNotEmpty()) pinBuffer.deleteCharAt(pinBuffer.length - 1)
            updateDigitCount()
        }

        /** Screen-side biometric launcher asks for the cipher to authorize (F2.7). */
        suspend fun acquireUnlockCipher(): Cipher? =
            when (val vmk = vaultKeys.prepareBiometricUnlockCipher()) {
                is VmkState.Ready -> vmk.cipher
                VmkState.PermanentlyInvalidated -> {
                    // F2.5: hardware wrap gone; PIN remains the way in.
                    vaultKeys.disableHardwareWrap()
                    enterLocked(message = LockMessage.BIOMETRIC_KEY_INVALIDATED)
                    null
                }
                VmkState.Missing, null -> null
            }

        suspend fun acquireCreateCipher(): Cipher? =
            when (val vmk = vaultKeys.prepareCreateCipher()) {
                is VmkState.Ready -> vmk.cipher
                else -> null
            }

        fun onUnlockAuthorized(cipher: Cipher) {
            _state.value = LockUiState.Unlocking
            viewModelScope.launch {
                val dek = vaultKeys.unlockWithBiometric(cipher)
                if (dek != null) {
                    session.unlock(dek)
                } else {
                    enterLocked(message = LockMessage.VAULT_CORRUPT)
                }
            }
        }

        fun onCreateAuthorized(cipher: Cipher) {
            val pin = pendingCreatePin ?: return
            _state.value = LockUiState.Unlocking
            viewModelScope.launch {
                val dek = vaultKeys.createVault(pin, cipher)
                wipePendingPins()
                session.unlock(dek)
            }
        }

        fun onAuthDismissed() {
            when (_state.value) {
                is LockUiState.AwaitCreateAuth ->
                    _state.value = LockUiState.AwaitCreateAuth(LockMessage.AUTH_CANCELED)
                else -> Unit
            }
        }

        private fun submitPin() {
            when (val current = _state.value) {
                is LockUiState.CreatePin ->
                    when (current.stage) {
                        CreateStage.ENTER -> {
                            firstEntry = drainBuffer()
                            _state.value = LockUiState.CreatePin(CreateStage.CONFIRM, 0)
                        }
                        CreateStage.CONFIRM -> confirmCreatePin()
                    }
                is LockUiState.Locked -> unlockWithPin()
                else -> Unit
            }
        }

        private fun confirmCreatePin() {
            val second = drainBuffer()
            val first = firstEntry
            if (first != null && first.contentEquals(second)) {
                pendingCreatePin = first
                second.fill('0')
                _state.value = LockUiState.AwaitCreateAuth()
            } else {
                wipePendingPins()
                second.fill('0')
                _state.value =
                    LockUiState.CreatePin(CreateStage.ENTER, 0, LockMessage.PINS_DONT_MATCH)
            }
        }

        private fun unlockWithPin() {
            val pin = drainBuffer()
            _state.value = LockUiState.Unlocking
            viewModelScope.launch {
                when (val result = vaultKeys.unlockWithPin(pin)) {
                    is PinUnlockResult.Success -> session.unlock(result.dek)
                    is PinUnlockResult.WrongPin -> enterLocked(message = LockMessage.WRONG_PIN)
                    is PinUnlockResult.TooManyAttempts ->
                        enterLocked(message = null, backoffMillis = result.remainingMillis)
                    PinUnlockResult.VaultCorrupt ->
                        enterLocked(message = LockMessage.VAULT_CORRUPT)
                }
                pin.fill('0')
            }
        }

        private suspend fun enterLocked(
            message: LockMessage?,
            backoffMillis: Long = 0L,
        ) {
            pinBuffer.clear()
            val biometricEnabled = vaultKeys.isBiometricEnabled()
            _state.value =
                LockUiState.Locked(
                    enteredDigits = 0,
                    isBiometricEnabled = biometricEnabled,
                    backoffRemainingSeconds = (backoffMillis / ONE_SECOND_MILLIS).toInt(),
                    message = message,
                )
            if (backoffMillis > 0L) startBackoffTicker()
        }

        private fun startBackoffTicker() {
            backoffJob?.cancel()
            backoffJob =
                viewModelScope.launch {
                    var current = _state.value as? LockUiState.Locked ?: return@launch
                    while (current.backoffRemainingSeconds > 0) {
                        delay(ONE_SECOND_MILLIS)
                        current = current.copy(backoffRemainingSeconds = current.backoffRemainingSeconds - 1)
                        _state.value = current
                    }
                }
        }

        private fun updateDigitCount() {
            _state.value =
                when (val current = _state.value) {
                    is LockUiState.CreatePin -> current.copy(enteredDigits = pinBuffer.length, message = null)
                    is LockUiState.Locked -> current.copy(enteredDigits = pinBuffer.length, message = null)
                    else -> current
                }
        }

        private fun drainBuffer(): CharArray {
            val chars = CharArray(pinBuffer.length) { pinBuffer[it] }
            pinBuffer.clear()
            return chars
        }

        private fun wipePendingPins() {
            firstEntry?.fill('0')
            firstEntry = null
            pendingCreatePin?.fill('0')
            pendingCreatePin = null
        }

        override fun onCleared() {
            wipePendingPins()
            super.onCleared()
        }
    }
