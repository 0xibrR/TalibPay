package com.irdev.talibpay.viewmodel

import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.irdev.talibpay.R
import com.irdev.talibpay.data.AccountProvider
import com.irdev.talibpay.data.AuthRepository
import com.irdev.talibpay.data.Reauthentication
import com.irdev.talibpay.util.ToastKind
import com.google.firebase.auth.FirebaseUser

class SettingsViewModel : BaseViewModel() {

    private val repository = AuthRepository()

    val user: FirebaseUser? get() = repository.currentUser

    fun accountProvider(): AccountProvider = repository.accountProvider()

    fun hasPassword(): Boolean = repository.hasPassword()

    fun signOut() {
        repository.signOut()
        send(UiEvent.Message(R.string.signed_out_successfully, kind = ToastKind.SUCCESS))
        send(UiEvent.SignedOut)
    }

    /** تغيير كلمة المرور، أو ربط password لأول مرة لحساب Google. */
    fun changePassword(oldPassword: String?, newPassword: String) = runAction {
        if (repository.hasPassword()) {
            try {
                repository.changePassword(oldPassword.orEmpty(), newPassword)
            } catch (_: FirebaseAuthInvalidCredentialsException) {
                send(UiEvent.Message(R.string.wrong_old_password, kind = ToastKind.ERROR))
                return@runAction
            }
            send(UiEvent.Message(R.string.password_changed_success, kind = ToastKind.SUCCESS))
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
        send(UiEvent.Message(R.string.account_deleted, kind = ToastKind.SUCCESS))
        send(UiEvent.SignedOut)
    }
}
