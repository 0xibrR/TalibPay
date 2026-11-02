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
         * مجاميع الطالب تُشتق دائمًا من دفعاته (مرتبة من الأقدم للأحدث):
         * - المدفوع = مجموع كل الدفعات (نقدي + بنكي).
         * - الرسوم = الرسوم المسجلة في آخر دفعة، فتعديل الرسوم (زيادة أو خصم) يسري من آخر دفعة.
         * - المتبقي = الرسوم − المدفوع، ولا يقل عن صفر.
         */
        fun from(payments: List<Payment>): StudentTotals {
            val cash = Money.sum(payments.map { it.cash })
            val bank = Money.sum(payments.map { it.bank })
            val paid = Money.sum(listOf(cash, bank))
            val total = Money.round(payments.lastOrNull()?.total ?: 0.0)
            return StudentTotals(
                paidCash = cash,
                paidBank = bank,
                paid = paid,
                total = total,
                remaining = Money.subtract(total, paid).coerceAtLeast(0.0)
            )
        }

        /**
         * رصيد تراكمي لكل دفعة: "المتبقي" في الدفعة هو ما بقي على الطالب بعدها،
         * أي رسومها − كل ما دُفع حتى هذه الدفعة (وليس رسومها − هذه الدفعة وحدها).
         */
        fun withRunningBalance(payments: List<Payment>): List<Payment> {
            var paidSoFar = 0.0
            return payments.map { payment ->
                val paid = Money.sum(listOf(payment.cash, payment.bank))
                paidSoFar = Money.sum(listOf(paidSoFar, paid))
                payment.copy(
                    paid = paid,
                    remaining = Money.subtract(payment.total, paidSoFar).coerceAtLeast(0.0)
                )
            }
        }
    }
}

/** نتيجة التحقق من مدخلات الدفعة قبل الحفظ. */
sealed interface PaymentValidation {
    data class Valid(val cash: Double, val bank: Double, val total: Double) : PaymentValidation
    data object InvalidAmount : PaymentValidation
    data object MissingFees : PaymentValidation
    data object MethodMismatch : PaymentValidation

    /** مجموع المدفوع سيتجاوز الرسوم؛ [maxAllowed] أقصى ما يمكن دفعه في هذه الدفعة. */
    data class PaidExceedsTotal(val maxAllowed: Double) : PaymentValidation
}

/**
 * @param paidByOtherPayments مجموع ما دفعه الطالب في دفعاته الأخرى
 * (كل الدفعات عند الإضافة، أو كلها عدا الدفعة المعدَّلة عند التعديل).
 */
fun validatePayment(
    cashText: String?,
    bankText: String?,
    totalText: String?,
    method: PaymentMethod,
    paidByOtherPayments: Double = 0.0
): PaymentValidation {
    val cash = Money.parse(cashText) ?: return PaymentValidation.InvalidAmount
    val bank = Money.parse(bankText) ?: return PaymentValidation.InvalidAmount
    val total = Money.parse(totalText) ?: return PaymentValidation.InvalidAmount
    if (total <= 0) return PaymentValidation.MissingFees
    if (!method.matches(cash, bank)) return PaymentValidation.MethodMismatch

    // التحقق تراكمي: ما دُفع سابقًا + هذه الدفعة لا يتجاوز الرسوم
    val paidAfter = Money.sum(listOf(paidByOtherPayments, cash, bank))
    if (paidAfter > total) {
        return PaymentValidation.PaidExceedsTotal(
            Money.subtract(total, paidByOtherPayments).coerceAtLeast(0.0)
        )
    }
    return PaymentValidation.Valid(cash, bank, total)
}

/** طريقة الدفع يجب أن تطابق المبالغ المدخلة. */
fun PaymentMethod.matches(cash: Double, bank: Double): Boolean = when (this) {
    PaymentMethod.CASH -> cash > 0 && bank == 0.0
    PaymentMethod.BANK -> bank > 0 && cash == 0.0
    PaymentMethod.MIXED -> cash > 0 && bank > 0
    PaymentMethod.UNPAID -> cash == 0.0 && bank == 0.0
}
