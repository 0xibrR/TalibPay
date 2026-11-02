package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Payment

data class StudentTotals(
    val paidCash: Double,
    val paidBank: Double,
    val paid: Double,
    val total: Double,
    val remaining: Double
) {
    companion object {
        /**
         * مجاميع الطالب تُشتق دائمًا من دفعاته:
         * الإجمالي المطلوب = أكبر رسوم مسجلة في أي دفعة، والمتبقي لا يقل عن صفر.
         */
        fun from(payments: Collection<Payment>): StudentTotals {
            val cash = Money.sum(payments.map { it.cash })
            val bank = Money.sum(payments.map { it.bank })
            val paid = Money.sum(listOf(cash, bank))
            val total = Money.round(payments.maxOfOrNull { it.total } ?: 0.0)
            return StudentTotals(
                paidCash = cash,
                paidBank = bank,
                paid = paid,
                total = total,
                remaining = Money.subtract(total, paid).coerceAtLeast(0.0)
            )
        }
    }
}

/** نتيجة التحقق من مدخلات الدفعة قبل الحفظ. */
sealed interface PaymentValidation {
    data class Valid(val cash: Double, val bank: Double, val total: Double) : PaymentValidation
    data object InvalidAmount : PaymentValidation
    data object PaidExceedsTotal : PaymentValidation
}

fun validatePayment(cashText: String?, bankText: String?, totalText: String?): PaymentValidation {
    val cash = Money.parse(cashText) ?: return PaymentValidation.InvalidAmount
    val bank = Money.parse(bankText) ?: return PaymentValidation.InvalidAmount
    val total = Money.parse(totalText) ?: return PaymentValidation.InvalidAmount
    if (Money.sum(listOf(cash, bank)) > total) return PaymentValidation.PaidExceedsTotal
    return PaymentValidation.Valid(cash, bank, total)
}
