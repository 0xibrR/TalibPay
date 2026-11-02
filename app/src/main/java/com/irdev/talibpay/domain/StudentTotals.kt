package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Charge
import com.irdev.talibpay.model.Payment

enum class DueStatus { PAID, PARTIAL, UNPAID }

/** مستحق مع دفعاته (من الأقدم للأحدث) وما دُفع منه وما بقي. */
data class DueSummary(
    val charge: Charge,
    val payments: List<Payment>,
    val paid: Double,
    val remaining: Double
) {
    val status: DueStatus
        get() = when {
            remaining <= 0 -> DueStatus.PAID
            paid > 0 -> DueStatus.PARTIAL
            else -> DueStatus.UNPAID
        }

    /** المستحق مشتق من بيانات قديمة ولم يُحفظ بعد كمستحق حقيقي. */
    val isLegacy: Boolean get() = charge.id == StudentLedger.LEGACY_CHARGE_ID
}

data class StudentTotals(
    val paidCash: Double,
    val paidBank: Double,
    val paid: Double,
    val total: Double,
    val remaining: Double
)

/**
 * دفتر حساب الطالب: المستحقات ودفعات كل منها.
 * - المستحقات مرتبة حسب الإنشاء، والدفعات حسب المفتاح (زمنيًا).
 * - "المتبقي" في كل دفعة = ما بقي من مستحقها بعدها (رصيد تراكمي داخل المستحق).
 * - المجموع: الرسوم = مجموع المستحقات، المتبقي = مجموع ما بقي من كل مستحق.
 */
data class StudentLedger(val dues: List<DueSummary>) {

    val totals: StudentTotals by lazy {
        val payments = dues.flatMap { it.payments }
        StudentTotals(
            paidCash = Money.sum(payments.map { it.cash }),
            paidBank = Money.sum(payments.map { it.bank }),
            paid = Money.sum(dues.map { it.paid }),
            total = Money.sum(dues.map { it.charge.amount }),
            remaining = Money.sum(dues.map { it.remaining })
        )
    }

    /** كل الدفعات بعد حساب الرصيد، مرتبة زمنيًا. */
    val payments: List<Payment> by lazy { dues.flatMap { it.payments }.sortedBy { it.id } }

    /** المستحقات التي لم تُسدَّد بالكامل، الأقدم أولًا. */
    val openDues: List<DueSummary> get() = dues.filter { it.remaining > 0 }

    fun due(chargeId: String): DueSummary? = dues.firstOrNull { it.charge.id == chargeId }

    companion object {
        /** معرّف المستحق الذي تُجمع فيه دفعات النسخ القديمة (قبل نظام المستحقات). */
        const val LEGACY_CHARGE_ID = "legacy"

        fun build(charges: List<Charge>, payments: List<Payment>): StudentLedger {
            val known = charges.map { it.id }.toSet()
            // دفعات قديمة بلا مستحق: تُجمع في مستحق "الرسوم" قيمته آخر رسوم سُجلت معها
            val orphans = payments.filter { it.chargeId !in known }
            val allCharges = if (orphans.isNotEmpty() && LEGACY_CHARGE_ID !in known) {
                val paidByOrphans = Money.sum(orphans.map { Money.sum(listOf(it.cash, it.bank)) })
                val legacyAmount = maxOf(orphans.last().total, paidByOrphans)
                listOf(Charge(id = LEGACY_CHARGE_ID, amount = Money.round(legacyAmount))) + charges
            } else {
                charges
            }
            val byCharge = payments.groupBy { if (it.chargeId in known) it.chargeId else LEGACY_CHARGE_ID }

            val dues = allCharges.sortedWith(compareBy({ it.createdAt }, { it.id })).map { charge ->
                var paidSoFar = 0.0
                val duePayments = byCharge[charge.id].orEmpty().sortedBy { it.id }.map { p ->
                    val paid = Money.sum(listOf(p.cash, p.bank))
                    paidSoFar = Money.sum(listOf(paidSoFar, paid))
                    p.copy(
                        chargeId = charge.id,
                        paid = paid,
                        total = charge.amount,
                        remaining = Money.subtract(charge.amount, paidSoFar).coerceAtLeast(0.0),
                        paymentMethod = PaymentMethod.fromStored(p.paymentMethod)?.code
                            ?: methodFor(p.cash, p.bank).code
                    )
                }
                DueSummary(
                    charge = charge,
                    payments = duePayments,
                    paid = paidSoFar,
                    remaining = Money.subtract(charge.amount, paidSoFar).coerceAtLeast(0.0)
                )
            }
            return StudentLedger(dues)
        }
    }
}

/** طريقة الدفع تُستنتج من المبالغ، فلا يمكن أن تتعارض معها. */
fun methodFor(cash: Double, bank: Double): PaymentMethod = when {
    cash > 0 && bank > 0 -> PaymentMethod.MIXED
    bank > 0 -> PaymentMethod.BANK
    cash > 0 -> PaymentMethod.CASH
    else -> PaymentMethod.UNPAID
}

// ---------------- التحقق من المدخلات ----------------

sealed interface PaymentValidation {
    data class Valid(val cash: Double, val bank: Double) : PaymentValidation
    data object InvalidAmount : PaymentValidation
    data object EmptyPayment : PaymentValidation

    /** الدفعة أكبر من المتبقي على المستحق؛ [maxAllowed] أقصى مبلغ ممكن. */
    data class ExceedsDue(val maxAllowed: Double) : PaymentValidation
}

/**
 * @param dueAmount قيمة المستحق
 * @param paidByOtherPayments ما دُفع لهذا المستحق في دفعاته الأخرى (عدا الدفعة المعدَّلة)
 */
fun validatePayment(
    cashText: String?,
    bankText: String?,
    dueAmount: Double,
    paidByOtherPayments: Double
): PaymentValidation {
    val cash = Money.parse(cashText) ?: return PaymentValidation.InvalidAmount
    val bank = Money.parse(bankText) ?: return PaymentValidation.InvalidAmount
    val amount = Money.sum(listOf(cash, bank))
    if (amount <= 0) return PaymentValidation.EmptyPayment

    val maxAllowed = Money.subtract(dueAmount, paidByOtherPayments).coerceAtLeast(0.0)
    if (amount > maxAllowed) return PaymentValidation.ExceedsDue(maxAllowed)
    return PaymentValidation.Valid(cash, bank)
}

sealed interface ChargeValidation {
    data class Valid(val title: String, val amount: Double) : ChargeValidation
    data object MissingTitle : ChargeValidation
    data object InvalidAmount : ChargeValidation

    /** لا يمكن أن تقل قيمة المستحق عمّا دُفع منه. */
    data class BelowPaid(val alreadyPaid: Double) : ChargeValidation
}

fun validateCharge(title: String?, amountText: String?, alreadyPaid: Double = 0.0): ChargeValidation {
    val cleanTitle = title?.trim().orEmpty()
    if (cleanTitle.isEmpty()) return ChargeValidation.MissingTitle
    val amount = Money.parse(amountText) ?: return ChargeValidation.InvalidAmount
    if (amount <= 0) return ChargeValidation.InvalidAmount
    if (amount < alreadyPaid) return ChargeValidation.BelowPaid(alreadyPaid)
    return ChargeValidation.Valid(cleanTitle, amount)
}
