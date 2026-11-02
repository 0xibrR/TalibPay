package com.irdev.talibpay.data

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.irdev.talibpay.R

class GoogleCredentialException : IllegalStateException("No Google ID token credential returned")

/**
 * تسجيل الدخول بـ Google عبر Credential Manager (بديل GoogleSignIn المهجور).
 * يحتاج Activity لأنه يعرض واجهة اختيار الحساب.
 *
 * @param authorizedOnly true لإعادة التحقق (مثل حذف الحساب): يعرض فقط الحسابات المستخدمة سابقًا.
 */
suspend fun requestGoogleIdToken(activity: Activity, authorizedOnly: Boolean = false): String {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(activity.getString(R.string.default_web_client_id))
        .setFilterByAuthorizedAccounts(authorizedOnly)
        .setAutoSelectEnabled(authorizedOnly)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(activity).getCredential(activity, request).credential

    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    throw GoogleCredentialException()
}

/** يمسح الحساب المختار من Credential Manager حتى لا يُختار تلقائيًا بعد تسجيل الخروج. */
suspend fun clearGoogleCredentialState(context: Context) {
    runCatching {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
}
