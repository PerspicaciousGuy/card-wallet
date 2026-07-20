package com.cardwallet.data.session

import com.cardwallet.data.crypto.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Locked : SessionState

    data object Unlocked : SessionState
}

/**
 * The single owner of the in-memory DEK and the locked/unlocked state
 * (plan §3 rule 2). Cold start is Locked by construction; [lock] zeroes the
 * DEK bytes. The auto-lock timeout is checked against an injected clock so
 * the logic is unit-testable.
 */
@Singleton
class SessionStateHolder
    @Inject
    constructor(
        private val time: TimeSource,
    ) {
        private val _state = MutableStateFlow<SessionState>(SessionState.Locked)
        val state: StateFlow<SessionState> = _state.asStateFlow()

        private var dek: ByteArray? = null
        private var backgroundedAtMillis: Long? = null

        /** F6.1: written by the settings observer in CardWalletApp; 0 = lock
         *  on any backgrounding. Volatile — written from a collector coroutine,
         *  read on the main thread's lifecycle callbacks. */
        @Volatile
        var autoLockTimeoutMillis: Long = DEFAULT_AUTO_LOCK_TIMEOUT_MILLIS

        fun unlock(dekBytes: ByteArray) {
            dek = dekBytes
            _state.value = SessionState.Unlocked
        }

        fun lock() {
            dek?.fill(0)
            dek = null
            backgroundedAtMillis = null
            _state.value = SessionState.Locked
        }

        /** The vault key for repository crypto; null while locked. */
        fun dekOrNull(): SecretKey? = dek?.let { SecretKeySpec(it, "AES") }

        fun onAppBackgrounded() {
            if (_state.value == SessionState.Unlocked) {
                backgroundedAtMillis = time.nowMillis()
            }
        }

        fun onAppForegrounded() {
            val since = backgroundedAtMillis ?: return
            backgroundedAtMillis = null
            if (time.nowMillis() - since >= autoLockTimeoutMillis) {
                lock()
            }
        }

        companion object {
            const val DEFAULT_AUTO_LOCK_TIMEOUT_MILLIS = 60_000L
        }
    }
