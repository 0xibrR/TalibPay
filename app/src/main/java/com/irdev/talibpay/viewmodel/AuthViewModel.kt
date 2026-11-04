package com.irdev.talibpay.viewmodel

import com.irdev.talibpay.R
import com.irdev.talibpay.data.AuthRepository
import com.irdev.talibpay.data.EmailSignInResult
import com.irdev.talibpay.util.ToastKind

/** ViewModel لشاشات Login و Register و Forgot password. */
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
        send(UiEvent.SignedIn(user.displayName ?: user.email))
    }

    fun register(email: String, password: String) = runAction {
        repository.register(email, password)
        send(UiEvent.Message(R.string.verification_email))
        send(UiEvent.Finished)
    }

    fun sendPasswordReset(email: String) = runAction {
        repository.sendPasswordReset(email)
        send(UiEvent.Message(R.string.email_sent, kind = ToastKind.SUCCESS))
        send(UiEvent.Finished)
    }
}
