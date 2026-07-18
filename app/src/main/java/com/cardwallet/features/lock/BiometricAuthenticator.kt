package com.cardwallet.features.lock

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

/**
 * Suspend wrapper over the system auth sheet (F2.7): BIOMETRIC_STRONG with the
 * device credential as built-in fallback, authorizing the given VMK [Cipher]
 * via CryptoObject. Lives in the UI layer because BiometricPrompt requires a
 * [FragmentActivity]; the ViewModel never sees the activity.
 */
suspend fun authenticateWithBiometrics(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    cipher: Cipher,
): BiometricOutcome =
    suspendCancellableCoroutine { continuation ->
        val callback =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authorized = result.cryptoObject?.cipher
                    continuation.resume(
                        if (authorized != null) {
                            BiometricOutcome.Authorized(authorized)
                        } else {
                            BiometricOutcome.Unavailable
                        },
                    )
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    val dismissed =
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                    continuation.resume(
                        if (dismissed) BiometricOutcome.Dismissed else BiometricOutcome.Unavailable,
                    )
                }
            }

        val prompt =
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()

        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
