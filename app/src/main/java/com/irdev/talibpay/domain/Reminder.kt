package com.irdev.talibpay.domain

/**
 * الـ charges المتأخرة التي يُذكَّر بها ولي الأمر:
 * عليها متبقٍ، وتاريخ استحقاقها اليوم أو قبله (أو بلا تاريخ، فهي مستحقة الآن).
 * الـ charge ذو التاريخ المستقبلي لا يُعد متأخرًا.
 */
fun StudentLedger.overdueDues(today: SimpleDate): List<DueSummary> =
    dues.filter { due ->
        due.remaining > 0 && SimpleDate.parse(due.charge.dueDate).let { it == null || it <= today }
    }

/** مجموع المتبقي على الـ charges المتأخرة. */
fun List<DueSummary>.overdueTotal(): Double = Money.sum(map { it.remaining })

/** تحويل رقم الهاتف للصيغة الدولية التي يقبلها WhatsApp (أرقام فقط، بدون + أو 00). */
object PhoneNumbers {

    /** رمز الدولة الافتراضي (فلسطين)؛ يتغير من الإعدادات. */
    const val DEFAULT_COUNTRY_CODE = "970"

    private const val MIN_LENGTH = 8
    private const val MAX_LENGTH = 15 // أقصى طول لرقم دولي (E.164)

    /**
     * @return الرقم بصيغة دولية، أو null إذا لم يكن صالحًا.
     * - "+972 59-123" أو "00972..." → نحذف + و 00 ويبقى الرقم كما هو.
     * - "0591234567" (رقم محلي) → الصفر الأول يُستبدل برمز الدولة.
     * - رقم يبدأ برمز الدولة أصلًا → يبقى كما هو.
     */
    fun toInternational(raw: String, countryCode: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        val code = countryCode.filter { it.isDigit() }
        val result = when {
            trimmed.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            digits.startsWith("0") && code.isNotEmpty() -> code + digits.drop(1)
            else -> digits
        }
        return result.takeIf { it.length in MIN_LENGTH..MAX_LENGTH }
    }

    /**
     * الرقم للعرض مع رمز الدولة، مثل "+970 591234567".
     * الرقم المكتوب بصيغة دولية (+ أو 00) يُعرض كما هو.
     */
    fun display(raw: String, countryCode: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("+") || trimmed.startsWith("00")) return trimmed
        if (!isValidCountryCode(countryCode)) return trimmed
        return "+$countryCode ${trimmed.removePrefix("0")}"
    }

    /** رمز دولة صالح: 1 إلى 3 أرقام، لا يبدأ بصفر. */
    fun isValidCountryCode(code: String): Boolean =
        code.length in 1..3 && code.all { it.isDigit() } && !code.startsWith("0")
}
