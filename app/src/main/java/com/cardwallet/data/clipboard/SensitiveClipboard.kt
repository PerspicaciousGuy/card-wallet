package com.cardwallet.data.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** The copy operation ViewModels see; implemented by [SensitiveClipboard]. */
fun interface SecretClipboard {
    fun copy(
        label: String,
        value: String,
    )
}

/**
 * Clipboard hygiene (plan §3 rule 5, F4.6): copies are flagged sensitive so the
 * system keyboard/overlay won't preview them, and the clipboard is cleared
 * [CLIPBOARD_CLEAR_MS] after our copy — a later copy resets the timer instead
 * of being wiped early by a stale one.
 */
@Singleton
class SensitiveClipboard
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Named("app") private val appScope: CoroutineScope,
    ) : SecretClipboard {
        private var clearJob: Job? = null

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
            clearJob?.cancel()
            clearJob =
                appScope.launch {
                    delay(CLIPBOARD_CLEAR_MS)
                    manager.clearPrimaryClip()
                }
        }

        companion object {
            const val CLIPBOARD_CLEAR_MS = 30_000L
        }
    }
