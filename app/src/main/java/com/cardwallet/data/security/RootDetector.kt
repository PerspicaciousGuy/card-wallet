package com.cardwallet.data.security

import android.os.Build
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort root heuristics (plan §3 rule 8): warn, never hard-block. A
 * motivated attacker defeats any in-app check; the value is warning honest
 * users that the OS guarantees the vault relies on are weakened.
 */
@Singleton
class RootDetector
    @Inject
    constructor() {
        fun isLikelyRooted(): Boolean = hasTestKeys() || hasSuBinary()

        private fun hasTestKeys(): Boolean = Build.TAGS?.contains("test-keys") == true

        private fun hasSuBinary(): Boolean =
            SU_PATHS.any { path ->
                try {
                    File(path).exists()
                } catch (_: SecurityException) {
                    false
                }
            }

        private companion object {
            val SU_PATHS =
                listOf(
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/sbin/su",
                    "/system/sd/xbin/su",
                    "/vendor/bin/su",
                    "/su/bin/su",
                )
        }
    }
