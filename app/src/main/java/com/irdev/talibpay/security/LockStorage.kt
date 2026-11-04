package com.irdev.talibpay.security

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * مفتاح HMAC سري لا يمكن استخراجه من الجهاز.
 * يربط قيمة التحقق من الـ PIN بالجهاز، ويوقّع prefs الـ lock ضد الـ tampering.
 */
interface KeyedMac {
    fun exists(): Boolean
    fun mac(data: ByteArray): ByteArray
    fun destroy()
}

/** المفتاح داخل Android Keystore (hardware-backed إن توفر TEE/StrongBox)، ولا يغادره أبدًا. */
class KeystoreMac(private val alias: String = ALIAS) : KeyedMac {

    private val keyStore: KeyStore get() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    override fun exists(): Boolean = keyStore.containsAlias(alias)

    override fun mac(data: ByteArray): ByteArray {
        val key = (keyStore.getKey(alias, null) as? SecretKey) ?: generate()
        return Mac.getInstance(ALGORITHM).run {
            init(key)
            doFinal(data)
        }
    }

    override fun destroy() {
        if (exists()) keyStore.deleteEntry(alias)
    }

    private fun generate(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVIDER).run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build())
            generateKey()
        }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "talibpay_app_lock_mac"
        const val ALGORITHM = "HmacSHA256"
    }
}

/** تخزين prefs الـ lock كنصوص؛ التحقق من سلامتها في [AppLock]. */
interface LockStore {
    fun read(): Map<String, String>
    fun write(values: Map<String, String>)
    fun clear()
}

/** SharedPreferences خاصة بالـ lock، مستثناة من الـ backup (انظر backup_rules و data_extraction_rules). */
class PrefsLockStore(context: Context) : LockStore {
    private val prefs = context.applicationContext.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    override fun read(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    // commit وليس apply: عدد المحاولات يجب أن يُحفظ فورًا قبل أن يُغلَق التطبيق
    override fun write(values: Map<String, String>) {
        prefs.edit(commit = true) {
            clear()
            values.forEach { (key, value) -> putString(key, value) }
        }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }
}

/** elapsedRealtime لا يتأثر بتغيير وقت الجهاز، و boot count لمعرفة الـ reboot. */
interface LockClock {
    fun elapsedRealtime(): Long
    fun bootCount(): Int
}

class SystemLockClock(context: Context) : LockClock {
    private val resolver = context.applicationContext.contentResolver

    override fun elapsedRealtime(): Long = android.os.SystemClock.elapsedRealtime()

    override fun bootCount(): Int =
        Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, 0)
}
