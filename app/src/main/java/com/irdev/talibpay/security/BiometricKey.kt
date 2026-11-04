package com.irdev.talibpay.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * مفتاح AES في Android Keystore لا يُستخدم إلا بعد fingerprint قوية (Class 3)، ويُلغى تلقائيًا
 * عند إضافة fingerprint جديدة للجهاز. الفتح يتطلب encryption فعلي بالمفتاح بعد الـ fingerprint ([confirm])،
 * فلا يكفي استدعاء onAuthenticationSucceeded وحده (مثلًا عبر hooking tool) لفتح التطبيق.
 */
object BiometricKey {

    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "talibpay_app_lock_biometric"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val CHALLENGE = "talibpay-unlock".toByteArray(Charsets.UTF_8)

    sealed interface Prepared {
        data class Ready(val cryptoObject: BiometricPrompt.CryptoObject) : Prepared

        /** أُضيفت fingerprint جديدة أو أُزيلت كلها: المفتاح لم يعد صالحًا (KeyPermanentlyInvalidated). */
        data object Invalidated : Prepared
        data object Unavailable : Prepared
    }

    /** الجهاز فيه sensor قوي، والمستخدم سجّل fingerprint واحدة على الأقل. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** هل الجهاز يدعم الـ fingerprint أصلًا (حتى لو لم تُسجَّل بعد). */
    fun hasHardware(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE &&
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE

    fun exists(): Boolean = keyStore().containsAlias(ALIAS)

    fun create() {
        delete()
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // كل استخدام يحتاج fingerprint جديدة (بدون timeout)، و strong biometric فقط
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(builder.build())
            generateKey()
        }
    }

    fun delete() {
        if (exists()) keyStore().deleteEntry(ALIAS)
    }

    /** يجهّز Cipher مرتبط بالمفتاح ليُمرَّر لـ BiometricPrompt (CryptoObject). */
    fun prepare(context: Context): Prepared {
        if (!isAvailable(context)) return Prepared.Unavailable
        val key = keyStore().getKey(ALIAS, null) as? SecretKey ?: return Prepared.Invalidated
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
            Prepared.Ready(BiometricPrompt.CryptoObject(cipher))
        } catch (_: KeyPermanentlyInvalidatedException) {
            delete()
            Prepared.Invalidated
        }
    }

    /** بعد نجاح الـ fingerprint: encryption حقيقي بالمفتاح؛ يفشل إن لم تفتح الـ fingerprint المفتاح فعلًا. */
    fun confirm(result: BiometricPrompt.AuthenticationResult): Boolean = try {
        val cipher = result.cryptoObject?.cipher ?: return false
        cipher.doFinal(CHALLENGE).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
}
