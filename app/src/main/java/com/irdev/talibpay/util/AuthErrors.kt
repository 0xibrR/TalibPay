package com.irdev.talibpay.util

import androidx.annotation.StringRes
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.irdev.talibpay.R
import com.irdev.talibpay.data.GoogleAccountCollisionException
import com.irdev.talibpay.data.GoogleCredentialException
import com.irdev.talibpay.data.GoogleSignInNotConfiguredException
import com.irdev.talibpay.data.InsufficientWalletBalanceException
import com.irdev.talibpay.data.NotSignedInException
import com.irdev.talibpay.data.PaymentLockedByWalletException

const val MIN_PASSWORD_LENGTH = 8

// حساب email/password لا يدخل قبل الـ verification، وحساب Google verified مسبقًا
fun FirebaseUser.canAccessApp(): Boolean =
    isEmailVerified || providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }

/** المستخدم أغلق اختيار حساب Google بنفسه: لا نعرض خطأ. */
fun Throwable.isUserCancellation(): Boolean = this is GetCredentialCancellationException

/** رسالة آمنة ومترجمة بدل رسالة Firebase الخام. */
@StringRes
fun errorMessageRes(e: Throwable?): Int = when (e) {
    is FirebaseAuthWeakPasswordException -> R.string.error_weak_password
    is FirebaseAuthUserCollisionException -> R.string.error_email_in_use
    // لا نفرّق بين "user not found" و "wrong password" لمنع كشف الحسابات (account enumeration)
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException -> R.string.error_invalid_credentials
    is FirebaseNetworkException -> R.string.error_network
    is FirebaseTooManyRequestsException -> R.string.error_too_many_requests
    is NoCredentialException -> R.string.no_google_account_found
    is GoogleSignInNotConfiguredException -> R.string.google_not_configured
    is GoogleAccountCollisionException -> R.string.google_account_conflict
    is GetCredentialException, is GoogleCredentialException -> R.string.google_sign_in_failed
    is NotSignedInException -> R.string.user_not_found
    is InsufficientWalletBalanceException -> R.string.refund_balance_changed
    is PaymentLockedByWalletException -> R.string.payment_locked_by_wallet
    else -> R.string.error_generic
}
