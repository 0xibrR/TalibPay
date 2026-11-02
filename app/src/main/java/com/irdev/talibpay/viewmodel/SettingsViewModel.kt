package com.irdev.talibpay.viewmodel

import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.irdev.talibpay.R
import com.irdev.talibpay.data.AccountProvider
import com.irdev.talibpay.data.AuthRepository
import com.irdev.talibpay.data.Reauthentication
import com.google.firebase.auth.FirebaseUser

class SettingsViewModel : BaseViewModel() {

    private val repository = AuthRepository()

    val user: FirebaseUser? get() = repository.currentUser

    fun accountProvider(): AccountProvider = repository.accountProvider()

    fun hasPassword(): Boolean = repository.hasPassword()

    fun signOut() {
        repository.signOut()
        send(UiEvent.Message(R.string.signed_out_successfully))
        send(UiEvent.SignedOut)
    }

    /** تغيير كلمة المرور، أو ربط كلمة مرور لأول مرة لحساب Google. */
    fun changePassword(oldPassword: String?, newPassword: String) = runAction {
        if (repository.hasPassword()) {
            try {
                repository.changePassword(oldPassword.orEmpty(), newPassword)
            } catch (_: FirebaseAuthInvalidCredentialsException) {
                send(UiEvent.Message(R.string.wrong_old_password))
                return@runAction
            }
            send(UiEvent.Message(R.string.password_changed_success))
        } else {
            val verified = repository.linkPassword(newPassword)
            send(
                UiEvent.Message(
                    if (verified) R.string.password_changed_success
                    else R.string.check_your_email_to_verify
                )
            )
        }
        send(UiEvent.Finished)
    }

    fun deleteAccount(reauthentication: Reauthentication) = runAction {
        repository.deleteAccount(reauthentication)
        repository.signOut()
        send(UiEvent.Message(R.string.data_deleted_successfully))
        send(UiEvent.SignedOut)
    }
}
