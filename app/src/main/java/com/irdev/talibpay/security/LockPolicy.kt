package com.irdev.talibpay.security

/** طرق فتح التطبيق. الرمز (PIN) موجود دائمًا كبديل حتى في وضع البصمة فقط. */
enum class LockMode {
    /** البصمة فقط؛ يُستخدم الرمز تلقائيًا إن لم تتوفر البصمة. */
    BIOMETRIC,
    PIN,

    /** أيٌّ منهما يفتح التطبيق. */
    BOTH;

    val usesBiometric: Boolean get() = this != PIN

    companion object {
        fun fromName(name: String?): LockMode? = entries.firstOrNull { it.name == name }
    }
}

/** ما تعرضه شاشة القفل، حسب الوضع المختار وتوفّر البصمة الآن. */
data class UnlockMethods(val biometric: Boolean, val pin: Boolean)

fun unlockMethodsFor(mode: LockMode, biometricAvailable: Boolean): UnlockMethods = when {
    // البصمة غير متوفرة (أُزيلت أو تعطلت): الرمز بديل دائمًا حتى لا يُحبس المستخدم خارج التطبيق
    !biometricAvailable -> UnlockMethods(biometric = false, pin = true)
    mode == LockMode.BIOMETRIC -> UnlockMethods(biometric = true, pin = false)
    mode == LockMode.PIN -> UnlockMethods(biometric = false, pin = true)
    else -> UnlockMethods(biometric = true, pin = true)
}

/** قواعد الرمز: 6 أرقام، ويُرفض الضعيف الذي يُخمَّن بسهولة. */
object PinRules {
    const val LENGTH = 6

    sealed interface Validation {
        data object Valid : Validation
        data object WrongLength : Validation
        data object NotDigits : Validation
        data object TooSimple : Validation
    }

    fun validate(pin: String): Validation = when {
        pin.length != LENGTH -> Validation.WrongLength
        !pin.all { it in '0'..'9' } -> Validation.NotDigits
        isTooSimple(pin) -> Validation.TooSimple
        else -> Validation.Valid
    }

    /** رقم مكرر (111111) أو متسلسل صعودًا/نزولًا (123456، 987654). */
    private fun isTooSimple(pin: String): Boolean {
        if (pin.toSet().size == 1) return true
        val steps = pin.zipWithNext { a, b -> b - a }.toSet()
        return steps == setOf(1) || steps == setOf(-1)
    }
}

/**
 * سياسة المحاولات الخاطئة:
 * - أول [FREE_ATTEMPTS] محاولات بلا انتظار.
 * - بعدها انتظار يتضاعف مع كل خطأ: 30 ث، 1 د، 2 د، 4 د...
 * - عند [MAX_ATTEMPTS] تُعاد تهيئة القفل ويُطلب تسجيل الدخول للحساب من جديد.
 */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 5
    const val MAX_ATTEMPTS = 10
    private const val BASE_DELAY_MS = 30_000L
    private const val MAX_DELAY_MS = 60 * 60_000L

    /** مدة الانتظار بعد [failedAttempts] محاولة خاطئة متتالية. */
    fun delayAfter(failedAttempts: Int): Long {
        if (failedAttempts < FREE_ATTEMPTS) return 0
        val doublings = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(20)
        return (BASE_DELAY_MS shl doublings).coerceAtMost(MAX_DELAY_MS)
    }

    fun attemptsBeforeLockout(failedAttempts: Int): Int = (FREE_ATTEMPTS - failedAttempts).coerceAtLeast(0)
}
