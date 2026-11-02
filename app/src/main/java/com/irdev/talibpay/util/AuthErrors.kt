package com.irdev.talibpay.util

import android.content.Context
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.irdev.talibpay.R

const val MIN_PASSWORD_LENGTH = 8

// حساب البريد/كلمة المرور لا يدخل التطبيق قبل تأكيد البريد، وحساب Google مؤكَّد مسبقًا
fun FirebaseUser.canAccessApp(): Boolean =
    isEmailVerified || providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }

// رسالة آمنة ومترجمة بدل عرض رسالة Firebase الخام للمستخدم
fun Context.authErrorMessage(e: Exception?): String = getString(
    when (e) {
        is FirebaseAuthWeakPasswordException -> R.string.error_weak_password
        is FirebaseAuthUserCollisionException -> R.string.error_email_in_use
        // لا نفرّق بين "المستخدم غير موجود" و"كلمة المرور خاطئة" لمنع كشف الحسابات
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> R.string.error_invalid_credentials
        is FirebaseNetworkException -> R.string.error_network
        is FirebaseTooManyRequestsException -> R.string.error_too_many_requests
        else -> R.string.error_generic
    }
)
