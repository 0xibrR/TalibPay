package com.irdev.talibpay.util

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.irdev.talibpay.R
import com.irdev.talibpay.data.GoogleAccountCollisionException
import com.irdev.talibpay.data.GoogleCredentialException
import com.irdev.talibpay.data.GoogleSignInNotConfiguredException
import com.irdev.talibpay.data.isNotConfiguredError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorsTest {

    @Test
    fun `missing SHA-1 registration is detected from the Play services error`() {
        assertTrue(GetCredentialUnknownException("[28444] Developer console is not set up correctly.").isNotConfiguredError())
        assertFalse(GetCredentialUnknownException("network error").isNotConfiguredError())
        assertFalse(NoCredentialException().isNotConfiguredError())
    }

    @Test
    fun `google sign-in errors get clear messages`() {
        assertEquals(R.string.no_google_account_found, errorMessageRes(NoCredentialException()))
        assertEquals(
            R.string.google_not_configured,
            errorMessageRes(GoogleSignInNotConfiguredException(GetCredentialUnknownException("[28444]")))
        )
        assertEquals(R.string.google_account_conflict, errorMessageRes(GoogleAccountCollisionException(Exception())))
        // رمز Google مرفوض من Firebase لا يُعرض كـ "كلمة مرور خاطئة"
        assertEquals(R.string.google_sign_in_failed, errorMessageRes(GoogleCredentialException(Exception())))
        assertEquals(R.string.google_sign_in_failed, errorMessageRes(GetCredentialUnknownException("x")))
    }

    @Test
    fun `closing the account picker is not an error`() {
        assertTrue(GetCredentialCancellationException().isUserCancellation())
        assertFalse(NoCredentialException().isUserCancellation())
    }
}
