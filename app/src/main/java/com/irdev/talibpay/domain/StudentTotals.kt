package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Charge
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.model.WalletEntry

enum class DueStatus { PAID, PARTIAL, UNPAID }

/**
 * Charge مع دفعاته (من الأقدم للأحدث) وما سُدِّد منه وما بقي.
 * @param paid المسدَّد من الـ charge: الجزء المطبَّق من الدفعات + ما خُصم من الـ wallet
 * @param walletUsed المسدَّد من الـ wallet (ليس دفعة نقدية)
 */
data class DueSummary(
    val charge: Charge,
    val payments: List<Payment>,
    val paid: Double,
    val remaining: Double,
    val walletUsed: Double = 0.0
) {
    /** للـ charge دفعات أو خصم من الـ wallet: لا يُحذف حتى لا تضيع حركات مالية. */
    val hasActivity: Boolean get() = payments.isNotEmpty() || walletUsed > 0

    val status: DueStatus
        get() = when {
            remaining <= 0 -> DueStatus.PAID
            paid > 0 -> DueStatus.PARTIAL
            else -> DueStatus.UNPAID
        }

    /** Legacy charge: مشتق من بيانات قديمة ولم يُحفظ بعد كـ charge حقيقي. */
    val isLegacy: Boolean get() = charge.id == StudentLedger.LEGACY_CHARGE_ID
}

data class StudentTotals(
    val paidCash: Double,
    val paidBank: Double,
    val paid: Double,
    val total: Double,
    val remaining: Double,
    val walletBalance: Double = 0.0
)

/**
 * Ledger الطالب: الـ charges ودفعات كل منها، وحركات الـ wallet.
 * - الـ charges مرتبة حسب الإنشاء، والدفعات حسب الـ key (زمنيًا).
 * - "remaining" في كل دفعة = ما بقي من الـ charge بعدها (running balance داخل الـ charge).
 * - Totals: الرسوم = مجموع الـ charges، المتبقي = مجموع ما بقي من كل charge،
 *   المدفوع = المال المستلم فعلًا (نقدي + بنكي، ومنه ما أُضيف للـ wallet).
 */
data class StudentLedger(
    val dues: List<DueSummary>,
    val walletEntries: List<WalletEntry> = emptyList()
) {

    val walletBalance: Double get() = Wallet.balance(walletEntries)

    val totals: StudentTotals by lazy {
        val payments = dues.flatMap { it.payments }
        StudentTotals(
            paidCash = Money.sum(payments.map { it.cash }),
            paidBank = Money.sum(payments.map { it.bank }),
            paid = Money.sum(payments.map { it.paid }),
            total = Money.sum(dues.map { it.charge.amount }),
            remaining = Money.sum(dues.map { it.remaining }),
            walletBalance = walletBalance
        )
    }

    /** كل الدفعات بعد حساب الرصيد، مرتبة زمنيًا. */
    val payments: List<Payment> by lazy { dues.flatMap { it.payments }.sortedBy { it.id } }

    /** الـ charges غير المسدّدة بالكامل، الأقدم أولًا. */
    val openDues: List<DueSummary> get() = dues.filter { it.remaining > 0 }

    fun due(chargeId: String): DueSummary? = dues.firstOrNull { it.charge.id == chargeId }

    companion object {
        /** id الـ charge الذي تُجمع فيه دفعات النسخ القديمة (قبل نظام الـ charges). */
        const val LEGACY_CHARGE_ID = "legacy"

        fun build(
            charges: List<Charge>,
            payments: List<Payment>,
            walletEntries: List<WalletEntry> = emptyList()
        ): StudentLedger {
            val known = charges.map { it.id }.toSet()
            // دفعات legacy بلا charge: تُجمع في charge "الرسوم" وقيمته آخر رسوم سُجلت معها
            val orphans = payments.filter { it.chargeId !in known }
            val allCharges = if (orphans.isNotEmpty() && LEGACY_CHARGE_ID !in known) {
                val paidByOrphans = Money.sum(orphans.map { Money.sum(listOf(it.cash, it.bank)) })
                val legacyAmount = maxOf(orphans.last().total, paidByOrphans)
                listOf(Charge(id = LEGACY_CHARGE_ID, amount = Money.round(legacyAmount))) + charges
            } else {
                charges
            }
            val byCharge = payments.groupBy { if (it.chargeId in known) it.chargeId else LEGACY_CHARGE_ID }
            // ما خُصم من الـ wallet لكل charge (يُطبَّق أولًا لأنه يحدث عند إنشاء الـ charge)
            val walletUsedByCharge = walletEntries
                .filter { it.entryType == WalletEntryType.USE }
                .groupBy { it.chargeId }
                .mapValues { (_, uses) -> Money.sum(uses.map { it.amount }) }

            val dues = allCharges.sortedWith(compareBy({ it.createdAt }, { it.id })).map { charge ->
                val walletUsed = walletUsedByCharge[charge.id] ?: 0.0
                var paidSoFar = walletUsed
                val duePayments = byCharge[charge.id].orEmpty().sortedBy { it.id }.map { p ->
                    val paid = Money.sum(listOf(p.cash, p.bank))
                    // الزيادة التي ذهبت للـ wallet لا تُحسب على الـ charge
                    val applied = Money.subtract(paid, p.credited.coerceIn(0.0, paid))
                    paidSoFar = Money.sum(listOf(paidSoFar, applied))
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
                    remaining = Money.subtract(charge.amount, paidSoFar).coerceAtLeast(0.0),
                    walletUsed = walletUsed
                )
            }
            return StudentLedger(dues, walletEntries.sortedWith(compareBy({ it.createdAt }, { it.id })))
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

// ---------------- Validation ----------------

sealed interface PaymentValidation {
    data class Valid(val cash: Double, val bank: Double) : PaymentValidation
    data object InvalidAmount : PaymentValidation
    data object EmptyPayment : PaymentValidation

    /** الدفعة أكبر من المتبقي على الـ charge؛ [maxAllowed] أقصى مبلغ ممكن. */
    data class ExceedsDue(val maxAllowed: Double) : PaymentValidation
}

/**
 * @param dueAmount قيمة الـ charge
 * @param paidByOtherPayments المدفوع لهذا الـ charge في دفعاته الأخرى (عدا الدفعة التي تُعدَّل)
 */
fun validatePayment(
    cashText: String?,
    bankText: String?,
    dueAmount: Double,
    paidByOtherPayments: Double,
    allowCredit: Boolean = false
): PaymentValidation {
    val cash = Money.parse(cashText) ?: return PaymentValidation.InvalidAmount
    val bank = Money.parse(bankText) ?: return PaymentValidation.InvalidAmount
    val amount = Money.sum(listOf(cash, bank))
    if (amount <= 0) return PaymentValidation.EmptyPayment

    val maxAllowed = Money.subtract(dueAmount, paidByOtherPayments).coerceAtLeast(0.0)
    // allowCredit: في الدفعة الجديدة تذهب الزيادة للـ wallet بدل رفضها
    if (!allowCredit && amount > maxAllowed) return PaymentValidation.ExceedsDue(maxAllowed)
    return PaymentValidation.Valid(cash, bank)
}

sealed interface ChargeValidation {
    data class Valid(val title: String, val amount: Double) : ChargeValidation
    data object MissingTitle : ChargeValidation
    data object InvalidAmount : ChargeValidation

    /** قيمة الـ charge لا تقل عن المدفوع منه. */
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
