package com.cardwallet.features.settings.changepin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardwallet.data.crypto.PinUnlockResult
import com.cardwallet.data.crypto.VaultKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PIN_LENGTH = 6

enum class ChangePinStage { CURRENT, NEW, CONFIRM }

enum class ChangePinMessage { WRONG_CURRENT, PINS_DONT_MATCH, BACKED_OFF }

data class ChangePinUiState(
    val stage: ChangePinStage = ChangePinStage.CURRENT,
    val enteredDigits: Int = 0,
    val message: ChangePinMessage? = null,
    val isWorking: Boolean = false,
    val isDone: Boolean = false,
)

/**
 * Three-stage PIN change (F6.4): verify the current PIN (via a real Wrap B
 * open, so a wrong PIN is caught by the crypto and rate-limited), enter a new
 * one, confirm it, then re-seal Wrap B.
 */
@HiltViewModel
class ChangePinViewModel
    @Inject
    constructor(
        private val vaultKeys: VaultKeyManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ChangePinUiState())
        val state: StateFlow<ChangePinUiState> = _state.asStateFlow()

        private val buffer = StringBuilder()
        private var currentPin: CharArray? = null
        private var newPin: CharArray? = null

        fun onDigit(digit: Char) {
            if (buffer.length >= PIN_LENGTH || _state.value.isWorking) return
            buffer.append(digit)
            _state.value = _state.value.copy(enteredDigits = buffer.length, message = null)
            if (buffer.length == PIN_LENGTH) advance()
        }

        fun onBackspace() {
            if (buffer.isNotEmpty()) buffer.deleteCharAt(buffer.length - 1)
            _state.value = _state.value.copy(enteredDigits = buffer.length)
        }

        private fun advance() {
            when (_state.value.stage) {
                ChangePinStage.CURRENT -> {
                    currentPin = drain()
                    resetTo(ChangePinStage.NEW)
                }
                ChangePinStage.NEW -> {
                    newPin = drain()
                    resetTo(ChangePinStage.CONFIRM)
                }
                ChangePinStage.CONFIRM -> confirm()
            }
        }

        private fun confirm() {
            val confirmPin = drain()
            val chosen = newPin
            if (chosen == null || !chosen.contentEquals(confirmPin)) {
                confirmPin.fill('0')
                newPin?.fill('0')
                newPin = null
                _state.value =
                    ChangePinUiState(stage = ChangePinStage.NEW, message = ChangePinMessage.PINS_DONT_MATCH)
                return
            }
            confirmPin.fill('0')
            val current = currentPin ?: return
            _state.value = _state.value.copy(isWorking = true, enteredDigits = 0)
            viewModelScope.launch {
                when (vaultKeys.changePin(current, chosen)) {
                    is PinUnlockResult.Success -> _state.value = ChangePinUiState(isDone = true)
                    is PinUnlockResult.WrongPin ->
                        restartWith(ChangePinMessage.WRONG_CURRENT)
                    is PinUnlockResult.TooManyAttempts ->
                        restartWith(ChangePinMessage.BACKED_OFF)
                    PinUnlockResult.VaultCorrupt ->
                        restartWith(ChangePinMessage.WRONG_CURRENT)
                }
                wipe()
            }
        }

        private fun restartWith(message: ChangePinMessage) {
            _state.value = ChangePinUiState(stage = ChangePinStage.CURRENT, message = message)
        }

        private fun resetTo(stage: ChangePinStage) {
            _state.value = ChangePinUiState(stage = stage)
        }

        private fun drain(): CharArray {
            val chars = CharArray(buffer.length) { buffer[it] }
            buffer.clear()
            return chars
        }

        private fun wipe() {
            currentPin?.fill('0')
            currentPin = null
            newPin?.fill('0')
            newPin = null
        }

        override fun onCleared() {
            wipe()
            super.onCleared()
        }
    }
