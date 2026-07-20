package com.cardwallet.data.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import com.cardwallet.data.settings.ClipboardTimeout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** The copy operation ViewModels see; implemented by [SensitiveClipboard]. */
interface SecretClipboard {
    fun copy(
        label: String,
        value: String,
    )

    /** Drops any copy we made — called when the vault locks. */
    fun clearNow()
}

/**
 * Clipboard hygiene (plan §3 rule 5, F4.6): copies are flagged sensitive so the
 * system keyboard/overlay won't preview them, and are cleared after the
 * user's configured [ClipboardTimeout] — a later copy resets the timer instead
 * of being wiped early by a stale one.
 *
 * The clear runs on the application scope, not a ViewModel's, because copying
 * and immediately switching apps is the normal case rather than the edge case.
 * It does not survive process death; the lock-time clear covers that.
 */
@Singleton
class SensitiveClipboard
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Named("app") private val appScope: CoroutineScope,
    ) : SecretClipboard {
        private var clearJob: Job? = null

        /** Written by the settings observer in CardWalletApp (see [ClipboardTimeout]). */
        @Volatile
        var timeout: ClipboardTimeout = ClipboardTimeout.THIRTY_SECONDS

        /** True only while a clip WE wrote may still be on the clipboard, so
         *  locking never wipes something the user copied from another app. */
        @Volatile
        private var ownsClip = false

        override fun copy(
            label: String,
            value: String,
        ) {
            val manager = context.getSystemService(ClipboardManager::class.java) ?: return
            val clip =
                ClipData.newPlainText(label, value).apply {
                    description.extras =
                        PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                }
            manager.setPrimaryClip(clip)
            ownsClip = true

            clearJob?.cancel()
            val delayMillis = timeout.millis
            if (delayMillis <= 0L) return
            clearJob =
                appScope.launch {
                    delay(delayMillis)
                    clear(manager)
                }
        }

        override fun clearNow() {
            clearJob?.cancel()
            val manager = context.getSystemService(ClipboardManager::class.java) ?: return
            clear(manager)
        }

        private fun clear(manager: ClipboardManager) {
            if (!ownsClip) return
            manager.clearPrimaryClip()
            ownsClip = false
        }
    }
