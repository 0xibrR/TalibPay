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
 * مفتاح AES في Android Keystore لا يُستخدم إلا بعد بصمة قوية (Class 3)، ويُلغى تلقائيًا
 * عند إضافة بصمة جديدة للجهاز. الفتح يتطلب تشفيرًا فعليًا بالمفتاح بعد البصمة ([confirm])،
 * فلا يكفي استدعاء onAuthenticationSucceeded وحده (مثلًا عبر أداة تلاعب) لفتح التطبيق.
 */
object BiometricKey {

    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "talibpay_app_lock_biometric"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val CHALLENGE = "talibpay-unlock".toByteArray(Charsets.UTF_8)

    sealed interface Prepared {
        data class Ready(val cryptoObject: BiometricPrompt.CryptoObject) : Prepared

        /** أُضيفت بصمة جديدة أو أُزيلت كل البصمات: المفتاح لم يعد صالحًا. */
        data object Invalidated : Prepared
        data object Unavailable : Prepared
    }

    /** الجهاز فيه حساس بصمة قوي، والمستخدم سجّل بصمة واحدة على الأقل. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** هل يدعم الجهاز البصمة أصلًا (حتى لو لم تُسجَّل بصمة بعد). */
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
            // كل استخدام يحتاج بصمة جديدة (لا مهلة)، وبصمة قوية فقط
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

    /** يجهّز Cipher مرتبطًا بالمفتاح ليُمرَّر لـ BiometricPrompt. */
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

    /** بعد نجاح البصمة: تشفير حقيقي بالمفتاح؛ يفشل إن لم تكن البصمة قد فتحت المفتاح فعلًا. */
    fun confirm(result: BiometricPrompt.AuthenticationResult): Boolean = try {
        val cipher = result.cryptoObject?.cipher ?: return false
        cipher.doFinal(CHALLENGE).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
}
