package com.irdev.talibpay.viewmodel

import com.irdev.talibpay.R
import com.irdev.talibpay.data.AuthRepository
import com.irdev.talibpay.data.EmailSignInResult

/** يخدم شاشات تسجيل الدخول وإنشاء الحساب واستعادة كلمة المرور. */
class AuthViewModel : BaseViewModel() {

    private val repository = AuthRepository()

    fun hasActiveSession(): Boolean = repository.hasActiveSession()

    fun signInWithEmail(email: String, password: String) = runAction {
        when (repository.signInWithEmail(email, password)) {
            EmailSignInResult.SUCCESS -> send(UiEvent.SignedIn(null))
            EmailSignInResult.NOT_VERIFIED ->
                send(UiEvent.Message(R.string.check_verification_email))
        }
    }

    fun signInWithGoogle(idToken: String) = runAction {
        val user = repository.signInWithGoogle(idToken)
        send(UiEvent.SignedIn(user?.displayName))
    }

    fun register(email: String, password: String) = runAction {
        repository.register(email, password)
        send(UiEvent.Message(R.string.verification_email))
        send(UiEvent.Finished)
    }

    fun sendPasswordReset(email: String) = runAction {
        repository.sendPasswordReset(email)
        send(UiEvent.Message(R.string.email_sent))
        send(UiEvent.Finished)
    }
}
