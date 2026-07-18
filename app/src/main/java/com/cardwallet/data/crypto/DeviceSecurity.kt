package com.cardwallet.data.crypto

import android.app.KeyguardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F1.2: the Keystore VMK requires user-authentication gating, which cannot
 * exist without a secure lock screen. Onboarding blocks until this is true.
 */
@Singleton
class DeviceSecurity
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        fun isDeviceSecure(): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }
