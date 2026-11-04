package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Student
import java.math.BigDecimal
import java.math.RoundingMode

enum class DiscountType { PERCENT, AMOUNT }

/**
 * خصم دائم على الـ monthly fee للطالب (مثل الإخوة أو منحة).
 * يُطبَّق عند إنشاء كل monthly charge، فتغييره لا يمس الـ charges السابقة.
 */
data class StudentDiscount(
    val type: DiscountType? = null,
    val value: Double = 0.0,
    val reason: String = ""
) {
    val isActive: Boolean get() = type != null && value > 0

    /** قيمة الخصم من fee قيمتها [fee]؛ لا يتجاوز الـ fee نفسها. */
    fun amountOff(fee: Double): Double {
        if (!isActive || fee <= 0) return 0.0
        val off = when (type) {
            DiscountType.PERCENT -> BigDecimal.valueOf(fee)
                .multiply(BigDecimal.valueOf(value.coerceAtMost(100.0)))
                .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
                .toDouble()
            DiscountType.AMOUNT -> value
            null -> 0.0
        }
        return off.coerceAtMost(fee)
    }

    /** الـ fee بعد الخصم. */
    fun netFee(fee: Double): Double = Money.subtract(fee, amountOff(fee))

    companion object {
        val NONE = StudentDiscount()
        const val MAX_REASON_LENGTH = 100
    }
}

/** خصم الطالب من حقوله المحفوظة في Firebase. */
val Student.discount: StudentDiscount
    get() = StudentDiscount(
        DiscountType.entries.firstOrNull { it.name == discountType },
        discountValue,
        discountReason
    )

sealed interface DiscountValidation {
    data class Valid(val discount: StudentDiscount) : DiscountValidation
    data object InvalidValue : DiscountValidation
    data object PercentOver100 : DiscountValidation
    data object ExceedsFee : DiscountValidation
}

/** @param type null = بدون خصم (القيمة تُتجاهل). */
fun validateDiscount(type: DiscountType?, valueText: String, reason: String, monthlyFee: Double): DiscountValidation {
    if (type == null) return DiscountValidation.Valid(StudentDiscount.NONE)
    val value = Money.parse(valueText)
    if (value == null || value <= 0) return DiscountValidation.InvalidValue
    return when {
        type == DiscountType.PERCENT && value > 100 -> DiscountValidation.PercentOver100
        type == DiscountType.AMOUNT && monthlyFee > 0 && value > monthlyFee -> DiscountValidation.ExceedsFee
        else -> DiscountValidation.Valid(
            StudentDiscount(type, value, reason.trim().take(StudentDiscount.MAX_REASON_LENGTH))
        )
    }
}
