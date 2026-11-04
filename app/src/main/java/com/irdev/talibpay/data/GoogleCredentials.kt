package com.irdev.talibpay.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.irdev.talibpay.R

class GoogleCredentialException(cause: Throwable? = null) :
    IllegalStateException("No Google ID token credential returned", cause)

/** التطبيق غير مُعدّ في Google Cloud (الـ SHA-1 أو الـ package name غير مضاف في Firebase). */
class GoogleSignInNotConfiguredException(cause: Throwable) :
    IllegalStateException("Google Sign-In is not configured for this app signature", cause)

private const val GOOGLE_ACCOUNT_TYPE = "com.google"

/**
 * Google sign-in عبر Credential Manager (بديل GoogleSignIn الـ deprecated).
 * يحتاج Activity لأنه يعرض UI اختيار الحساب.
 *
 * @param authorizedOnly true للـ re-authentication (مثل حذف الحساب): يختار بصمت من الحسابات المستخدمة سابقًا.
 *  false (زر "تسجيل الدخول عبر Google"): يعرض كل حسابات Google على الجهاز مع خيار إضافة حساب،
 *  ويطلب إضافة حساب إذا لم يوجد أي حساب.
 */
suspend fun requestGoogleIdToken(activity: Activity, authorizedOnly: Boolean = false): String {
    val serverClientId = activity.getString(R.string.default_web_client_id)
    val option: CredentialOption = if (authorizedOnly) {
        GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
    } else {
        GetSignInWithGoogleOption.Builder(serverClientId).build()
    }
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = try {
        CredentialManager.create(activity).getCredential(activity, request).credential
    } catch (e: GetCredentialException) {
        throw if (e.isNotConfiguredError()) GoogleSignInNotConfiguredException(e) else e
    }

    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        try {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw GoogleCredentialException(e)
        }
    }
    throw GoogleCredentialException()
}

/** Error 28444 من Play Services: "Developer console is not set up correctly". */
internal fun GetCredentialException.isNotConfiguredError(): Boolean {
    val text = "$type ${message.orEmpty()}"
    return "28444" in text || "Developer console" in text
}

/** System screen لإضافة حساب Google إلى الجهاز. */
fun addGoogleAccountIntent(): Intent =
    Intent(Settings.ACTION_ADD_ACCOUNT)
        .putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf(GOOGLE_ACCOUNT_TYPE))

/** يمسح الحساب المختار من Credential Manager حتى لا يُختار تلقائيًا بعد الـ sign out. */
suspend fun clearGoogleCredentialState(context: Context) {
    runCatching {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
}
