package com.cardwallet.features.lock

/** User-facing messages; the screen maps these to string resources (no raw errors in UI). */
enum class LockMessage {
    WRONG_PIN,
    PINS_DONT_MATCH,
    AUTH_CANCELED,
    BIOMETRIC_KEY_INVALIDATED,
    VAULT_CORRUPT,
}

enum class CreateStage { ENTER, CONFIRM }

sealed interface LockUiState {
    data object Loading : LockUiState

    /** F1.2 — no secure lock screen; onboarding is blocked until one exists. */
    data object SecureLockMissing : LockUiState

    /** F1.3 — first-run PIN creation (enter, then confirm). */
    data class CreatePin(
        val stage: CreateStage,
        val enteredDigits: Int,
        val message: LockMessage? = null,
    ) : LockUiState

    /** PIN accepted; waiting for the one-time system auth that seals Wrap A. */
    data class AwaitCreateAuth(
        val message: LockMessage? = null,
    ) : LockUiState

    /** F2.1 — the everyday lock screen. */
    data class Locked(
        val enteredDigits: Int,
        val isBiometricEnabled: Boolean,
        val backoffRemainingSeconds: Int = 0,
        val message: LockMessage? = null,
    ) : LockUiState

    data object Unlocking : LockUiState
}
