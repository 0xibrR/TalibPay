package com.irdev.talibpay.security

import java.security.MessageDigest
import java.security.SecureRandom

/** حالة القفل للمستخدم الحالي. */
sealed interface LockState {
    data object NotEnabled : LockState
    data class Enabled(val mode: LockMode, val biometricEnrolled: Boolean) : LockState

    /** الإعدادات عُدّلت أو حُذفت من خارج التطبيق: لا يُوثق بها. */
    data object Tampered : LockState
}

sealed interface PinResult {
    data object Success : PinResult

    /** @param attemptsBeforeLockout محاولات متبقية قبل بدء الانتظار. @param lockoutMs الانتظار الذي بدأ الآن (0 إن لم يبدأ). */
    data class Wrong(val attemptsBeforeLockout: Int, val lockoutMs: Long) : PinResult

    /** ممنوع المحاولة الآن؛ لم يُفحص الرمز أصلًا. */
    data class LockedOut(val remainingMs: Long) : PinResult

    /** تجاوز الحد الأقصى: أُلغي القفل ويجب تسجيل الدخول للحساب من جديد. */
    data object TooManyAttempts : PinResult

    data object Tampered : PinResult
}

/**
 * قفل التطبيق: رمز من 6 أرقام وبصمة اختيارية.
 *
 * الأمان:
 * - الرمز لا يُخزَّن أبدًا: نخزّن ملحًا عشوائيًا و HMAC_Keystore(PBKDF2(الرمز، الملح)).
 *   مفتاح HMAC في Android Keystore ولا يغادر الجهاز، فنسخ ملف الإعدادات لا يسمح بتخمين الرمز خارجه.
 * - كل الإعدادات (ومنها عدد المحاولات ووقت الانتظار) موقّعة بنفس المفتاح؛ أي تعديل يُكتشف ([LockState.Tampered]).
 * - حذف الإعدادات مع بقاء المفتاح يُعد تلاعبًا أيضًا (لا يمكن تعطيل القفل بحذف الملف).
 * - الانتظار يُحسب بساعة الإقلاع لا بساعة الجهاز، فتغيير الوقت لا يختصره؛ وإعادة التشغيل تعيد عدّه من البداية.
 */
class AppLock(
    private val store: LockStore,
    private val keyedMac: KeyedMac,
    private val clock: LockClock,
    private val random: SecureRandom = SecureRandom(),
    private val iterations: Int = DEFAULT_ITERATIONS
) {

    /** إعدادات القفل المحفوظة (بعد التحقق من توقيعها). */
    private data class Config(
        val ownerUid: String,
        val mode: LockMode,
        val salt: ByteArray,
        val verifier: ByteArray,
        val iterations: Int,
        val failedAttempts: Int = 0,
        val lockoutStartElapsed: Long = 0,
        val lockoutDurationMs: Long = 0,
        val bootCount: Int = 0,
        val biometricEnrolled: Boolean = false
    )

    private sealed interface Loaded {
        data object Missing : Loaded
        data object Tampered : Loaded
        data class Ok(val config: Config) : Loaded
    }

    // ---------------- الحالة ----------------

    /**
     * @param uid المستخدم المسجّل حاليًا؛ null إن لم يكن هناك مستخدم (لا قفل قبل تسجيل الدخول).
     * قفل مستخدم آخر (سجّل خروجه ودخل غيره) يُلغى تلقائيًا.
     */
    fun state(uid: String?): LockState = when (val loaded = load()) {
        Loaded.Missing -> LockState.NotEnabled
        Loaded.Tampered -> LockState.Tampered
        is Loaded.Ok -> when {
            uid == null -> LockState.NotEnabled
            loaded.config.ownerUid != uid -> {
                reset()
                LockState.NotEnabled
            }
            else -> LockState.Enabled(loaded.config.mode, loaded.config.biometricEnrolled)
        }
    }

    // ---------------- التفعيل والتعديل ----------------

    fun enable(uid: String, pin: String, mode: LockMode) {
        require(PinRules.validate(pin) == PinRules.Validation.Valid) { "Invalid PIN" }
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        save(
            Config(
                ownerUid = uid,
                mode = mode,
                salt = salt,
                verifier = verifierFor(pin, salt, iterations),
                iterations = iterations,
                bootCount = clock.bootCount()
            )
        )
    }

    /** يغيّر الرمز بعد التحقق من الرمز الحالي؛ يخضع لنفس حدود المحاولات. */
    fun changePin(currentPin: String, newPin: String): PinResult {
        require(PinRules.validate(newPin) == PinRules.Validation.Valid) { "Invalid PIN" }
        val result = verifyPin(currentPin)
        if (result != PinResult.Success) return result
        val config = (load() as? Loaded.Ok)?.config ?: return PinResult.Tampered
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        save(config.copy(salt = salt, verifier = verifierFor(newPin, salt, iterations), iterations = iterations))
        return PinResult.Success
    }

    /** يُستدعى بعد تحقق المستخدم من هويته (رمز أو بصمة). */
    fun setMode(mode: LockMode) {
        val config = (load() as? Loaded.Ok)?.config ?: return
        save(config.copy(mode = mode))
    }

    fun setBiometricEnrolled(enrolled: Boolean) {
        val config = (load() as? Loaded.Ok)?.config ?: return
        save(config.copy(biometricEnrolled = enrolled))
    }

    /** يلغي القفل ويحذف المفتاح؛ يُستدعى بعد تحقق المستخدم، أو عند التلاعب، أو تجاوز المحاولات. */
    fun reset() {
        store.clear()
        keyedMac.destroy()
    }

    // ---------------- التحقق ----------------

    fun verifyPin(pin: String): PinResult {
        val config = when (val loaded = load()) {
            is Loaded.Ok -> loaded.config
            else -> return PinResult.Tampered
        }
        val remaining = remainingLockout(config)
        if (remaining > 0) return PinResult.LockedOut(remaining)

        val candidate = verifierFor(pin, config.salt, config.iterations)
        // مقارنة بزمن ثابت حتى لا يكشف التوقيت عدد البايتات المطابقة
        if (MessageDigest.isEqual(candidate, config.verifier)) {
            save(config.copy(failedAttempts = 0, lockoutStartElapsed = 0, lockoutDurationMs = 0))
            return PinResult.Success
        }

        val failed = config.failedAttempts + 1
        if (failed >= LockoutPolicy.MAX_ATTEMPTS) {
            reset()
            return PinResult.TooManyAttempts
        }
        val delay = LockoutPolicy.delayAfter(failed)
        save(
            config.copy(
                failedAttempts = failed,
                lockoutStartElapsed = clock.elapsedRealtime(),
                lockoutDurationMs = delay,
                bootCount = clock.bootCount()
            )
        )
        return PinResult.Wrong(LockoutPolicy.attemptsBeforeLockout(failed), delay)
    }

    /** نجاح البصمة يعادل نجاح الرمز: تُصفّر المحاولات الخاطئة. */
    fun onBiometricSuccess() {
        val config = (load() as? Loaded.Ok)?.config ?: return
        if (config.failedAttempts != 0 || config.lockoutDurationMs != 0L) {
            save(config.copy(failedAttempts = 0, lockoutStartElapsed = 0, lockoutDurationMs = 0))
        }
    }

    /** الانتظار المتبقي قبل السماح بمحاولة رمز جديدة. */
    fun remainingLockoutMs(): Long = (load() as? Loaded.Ok)?.config?.let(::remainingLockout) ?: 0

    fun failedAttempts(): Int = (load() as? Loaded.Ok)?.config?.failedAttempts ?: 0

    private fun remainingLockout(config: Config): Long {
        if (config.lockoutDurationMs <= 0) return 0
        val now = clock.elapsedRealtime()
        val boot = clock.bootCount()
        if (boot != config.bootCount || now < config.lockoutStartElapsed) {
            // أُعيد تشغيل الجهاز (ساعة الإقلاع بدأت من الصفر): يبدأ الانتظار من جديد بدل أن يُتجاوز
            save(config.copy(lockoutStartElapsed = now, bootCount = boot))
            return config.lockoutDurationMs
        }
        return (config.lockoutStartElapsed + config.lockoutDurationMs - now).coerceAtLeast(0)
    }

    // ---------------- التخزين الموقّع ----------------

    private fun verifierFor(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        val derived = Pbkdf2.deriveKey(pinBytes, salt, iterations, DERIVED_LENGTH)
        pinBytes.fill(0)
        return keyedMac.mac(VERIFIER_LABEL + derived).also { derived.fill(0) }
    }

    private fun save(config: Config) {
        val values = linkedMapOf(
            K_VERSION to VERSION.toString(),
            K_UID to config.ownerUid,
            K_MODE to config.mode.name,
            K_SALT to config.salt.toHex(),
            K_VERIFIER to config.verifier.toHex(),
            K_ITERATIONS to config.iterations.toString(),
            K_FAILED to config.failedAttempts.toString(),
            K_LOCK_START to config.lockoutStartElapsed.toString(),
            K_LOCK_DURATION to config.lockoutDurationMs.toString(),
            K_BOOT to config.bootCount.toString(),
            K_BIOMETRIC to config.biometricEnrolled.toString()
        )
        values[K_MAC] = keyedMac.mac(canonical(values)).toHex()
        store.write(values)
    }

    private fun load(): Loaded {
        val values = store.read()
        if (values.isEmpty()) {
            // المفتاح موجود والإعدادات محذوفة: حُذف الملف لتعطيل القفل
            return if (keyedMac.exists()) Loaded.Tampered else Loaded.Missing
        }
        if (!keyedMac.exists()) return Loaded.Tampered
        val storedMac = values[K_MAC]?.hexToBytes() ?: return Loaded.Tampered
        val signed = values.filterKeys { it != K_MAC }
        if (!MessageDigest.isEqual(keyedMac.mac(canonical(signed)), storedMac)) return Loaded.Tampered

        return try {
            Loaded.Ok(
                Config(
                    ownerUid = values.getValue(K_UID),
                    mode = LockMode.fromName(values[K_MODE]) ?: return Loaded.Tampered,
                    salt = values.getValue(K_SALT).hexToBytes() ?: return Loaded.Tampered,
                    verifier = values.getValue(K_VERIFIER).hexToBytes() ?: return Loaded.Tampered,
                    iterations = values.getValue(K_ITERATIONS).toInt(),
                    failedAttempts = values.getValue(K_FAILED).toInt(),
                    lockoutStartElapsed = values.getValue(K_LOCK_START).toLong(),
                    lockoutDurationMs = values.getValue(K_LOCK_DURATION).toLong(),
                    bootCount = values.getValue(K_BOOT).toInt(),
                    biometricEnrolled = values.getValue(K_BIOMETRIC).toBooleanStrict()
                )
            )
        } catch (_: NoSuchElementException) {
            Loaded.Tampered
        } catch (_: IllegalArgumentException) {
            Loaded.Tampered
        }
    }

    /** نص ثابت الترتيب للتوقيع: المفاتيح مرتبة، وكل قيمة مسبوقة بطولها حتى لا تتداخل الحقول. */
    private fun canonical(values: Map<String, String>): ByteArray =
        values.toSortedMap().entries.joinToString("\n") { (k, v) -> "$k=${v.length}:$v" }.toByteArray(Charsets.UTF_8)

    companion object {
        /** عدد تكرارات PBKDF2: يجعل كل محاولة تخمين مكلفة (~ عُشر ثانية على هاتف حديث). */
        const val DEFAULT_ITERATIONS = 120_000
        private const val SALT_LENGTH = 16
        private const val DERIVED_LENGTH = 32
        private const val VERSION = 1
        private val VERIFIER_LABEL = "talibpay-pin-v1".toByteArray(Charsets.UTF_8)

        private const val K_VERSION = "v"
        private const val K_UID = "uid"
        private const val K_MODE = "mode"
        private const val K_SALT = "salt"
        private const val K_VERIFIER = "verifier"
        private const val K_ITERATIONS = "iterations"
        private const val K_FAILED = "failed"
        private const val K_LOCK_START = "lockStart"
        private const val K_LOCK_DURATION = "lockDuration"
        private const val K_BOOT = "boot"
        private const val K_BIOMETRIC = "biometric"
        private const val K_MAC = "mac"
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.hexToBytes(): ByteArray? {
    if (length % 2 != 0) return null
    return ByteArray(length / 2) { i ->
        substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: return null
    }
}
