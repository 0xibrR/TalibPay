package com.irdev.talibpay.security

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** BiometricPrompt مرتبط بمفتاح Keystore ([BiometricKey]). */
object BiometricUnlock {

    sealed interface Outcome {
        data object Success : Outcome

        /** ألغاها المستخدم أو اختار "استخدام الرمز". */
        data object Cancelled : Outcome

        /** محاولات fingerprint خاطئة كثيرة (lockout من النظام): الـ PIN هو البديل. */
        data object LockedOut : Outcome

        /** تغيّرت الـ fingerprints المسجلة: يجب إعادة تفعيلها بعد التحقق بالـ PIN. */
        data object Invalidated : Outcome
        data object Unavailable : Outcome
    }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        negativeText: String,
        onOutcome: (Outcome) -> Unit
    ) {
        val crypto = when (val prepared = BiometricKey.prepare(activity)) {
            is BiometricKey.Prepared.Ready -> prepared.cryptoObject
            BiometricKey.Prepared.Invalidated -> return onOutcome(Outcome.Invalidated)
            BiometricKey.Prepared.Unavailable -> return onOutcome(Outcome.Unavailable)
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onOutcome(if (BiometricKey.confirm(result)) Outcome.Success else Outcome.Cancelled)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onOutcome(
                    when (errorCode) {
                        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> Outcome.LockedOut
                        BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE -> Outcome.Unavailable
                        else -> Outcome.Cancelled
                    }
                )
            }
            // onAuthenticationFailed: fingerprint غير مطابقة؛ الـ prompt يبقى مفتوحًا والنظام يعرض "حاول مرة أخرى"
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info, crypto)
    }
}
