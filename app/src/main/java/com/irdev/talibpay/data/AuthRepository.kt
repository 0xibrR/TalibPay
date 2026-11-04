package com.irdev.talibpay.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.irdev.talibpay.util.canAccessApp
import kotlinx.coroutines.tasks.await

/** البريد مسجّل بطريقة sign-in أخرى، ولا يمكن ربط Google به تلقائيًا. */
class GoogleAccountCollisionException(cause: Throwable) :
    IllegalStateException("Account exists with a different sign-in method", cause)

enum class EmailSignInResult { SUCCESS, NOT_VERIFIED }

enum class AccountProvider { GOOGLE, PASSWORD, UNKNOWN }

/** طريقة الـ re-authentication قبل العمليات الحساسة (مثل حذف الحساب). */
sealed interface Reauthentication {
    data class Password(val password: String) : Reauthentication
    data class Google(val idToken: String) : Reauthentication
}

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** مستخدم مسجّل ومسموح له يدخل (بريده verified أو حساب Google). */
    fun hasActiveSession(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.canAccessApp()) return true
        // Session قديمة لحساب غير verified: ننهيها
        auth.signOut()
        return false
    }

    fun accountProvider(): AccountProvider {
        val providers = auth.currentUser?.providerData?.map { it.providerId }.orEmpty()
        return when {
            GoogleAuthProvider.PROVIDER_ID in providers -> AccountProvider.GOOGLE
            EmailAuthProvider.PROVIDER_ID in providers -> AccountProvider.PASSWORD
            else -> AccountProvider.UNKNOWN
        }
    }

    fun hasPassword(): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    suspend fun signInWithEmail(email: String, password: String): EmailSignInResult {
        val user = auth.signInWithEmailAndPassword(email, password).await().user
        if (user == null || !user.isEmailVerified) {
            // لا نترك session لحساب غير verified، وإلا سيدخل تلقائيًا عند فتح التطبيق
            auth.signOut()
            return EmailSignInResult.NOT_VERIFIED
        }
        return EmailSignInResult.SUCCESS
    }

    /**
     * يحوّل أخطاء Firebase الخاصة بـ Google لأخطاء واضحة، حتى لا تظهر رسالة
     * "البريد أو كلمة المرور غير صحيحة" لمستخدم لم يكتب كلمة مرور أصلًا.
     */
    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val user = try {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
        } catch (e: FirebaseAuthUserCollisionException) {
            throw GoogleAccountCollisionException(e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw GoogleCredentialException(e)
        }
        return user ?: throw GoogleCredentialException()
    }

    /** ينشئ الحساب ويرسل verification email، ثم sign out حتى يؤكد المستخدم بريده. */
    suspend fun register(email: String, password: String) {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
        try {
            user?.sendEmailVerification()?.await()
        } finally {
            auth.signOut()
        }
    }

    /** نفس النتيجة سواء البريد مسجّل أو لا، حتى لا نكشف الحسابات الموجودة (account enumeration). */
    suspend fun sendPasswordReset(email: String) {
        try {
            auth.sendPasswordResetEmail(email).await()
        } catch (_: FirebaseAuthInvalidUserException) {
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val user = requireUser()
        val email = user.email ?: throw NotSignedInException()
        user.reauthenticate(EmailAuthProvider.getCredential(email, oldPassword)).await()
        user.updatePassword(newPassword).await()
    }

    /**
     * ربط password بحساب Google لأول مرة.
     * @return true إذا البريد verified، و false إذا أُرسل verification email.
     */
    suspend fun linkPassword(newPassword: String): Boolean {
        val user = requireUser()
        val email = user.email ?: throw NotSignedInException()
        user.linkWithCredential(EmailAuthProvider.getCredential(email, newPassword)).await()
        user.reload().await()
        if (!user.isEmailVerified) {
            user.sendEmailVerification().await()
            return false
        }
        return true
    }

    /**
     * Re-authentication أولًا، ثم حذف بيانات المستخدم، ثم الحساب.
     * الـ recent login يضمن إن حذف الحساب لا يفشل بعد حذف البيانات.
     */
    suspend fun deleteAccount(reauthentication: Reauthentication) {
        val user = requireUser()
        val credential = when (reauthentication) {
            is Reauthentication.Password ->
                EmailAuthProvider.getCredential(
                    user.email ?: throw NotSignedInException(),
                    reauthentication.password
                )

            is Reauthentication.Google ->
                GoogleAuthProvider.getCredential(reauthentication.idToken, null)
        }
        user.reauthenticate(credential).await()
        database.getReference("TalibPay").child(user.uid).removeValue().await()
        user.delete().await()
    }

    fun signOut() {
        auth.signOut()
    }

    private fun requireUser(): FirebaseUser = auth.currentUser ?: throw NotSignedInException()
}
