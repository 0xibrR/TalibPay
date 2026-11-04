package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Student

/** طالب لم يسدّد كامل charges الشهر، مع هذه الـ charges والمتبقي منها. */
data class UnpaidStudent(
    val student: Student,
    val dues: List<DueSummary>,
    val remaining: Double
)

/**
 * تقرير شهر واحد:
 * - Collected: المال المستلم فعلًا في الشهر (حسب تاريخ الدفعة)، نقدي وبنكي، ناقص الـ refunds فيه.
 * - Billed: الـ charges التي تخص هذا الشهر، وكم سُدّد منها حتى الآن وكم بقي.
 * - Unpaid: من بقي عليه شيء من charges هذا الشهر، الأعلى مبلغًا أولًا.
 *
 * المحصّل في شهر قد يكون عن charges أشهر أخرى، لذلك لا يساوي بالضرورة المسدّد من charges الشهر.
 */
data class MonthlyReport(
    val month: YearMonth,
    val collectedCash: Double,
    val collectedBank: Double,
    val refunded: Double,
    val paymentCount: Int,
    val billed: Double,
    val billedRemaining: Double,
    /** مجموع الخصومات على charges هذا الشهر. */
    val discounts: Double,
    val unpaid: List<UnpaidStudent>
) {
    val collected: Double get() = Money.subtract(Money.sum(listOf(collectedCash, collectedBank)), refunded)
    val billedPaid: Double get() = Money.subtract(billed, billedRemaining)

    companion object {

        /** @param students كل الطلاب، ومنهم المؤرشفون (ما دفعوه مال حقيقي استُلم في ذلك الشهر). */
        fun build(students: List<Pair<Student, StudentLedger>>, month: YearMonth): MonthlyReport {
            val payments = students.flatMap { (_, ledger) -> ledger.payments }
                .filter { SimpleDate.parse(it.paymentDate)?.yearMonth == month }
            val refunds = students.flatMap { (_, ledger) -> ledger.walletEntries }
                .filter { it.entryType == WalletEntryType.REFUND && todaySimpleDate(it.createdAt).yearMonth == month }

            val monthDues = students.map { (student, ledger) ->
                student to ledger.dues.filter { monthOf(it) == month }
            }
            val unpaid = monthDues.mapNotNull { (student, dues) ->
                val open = dues.filter { it.remaining > 0 }
                if (open.isEmpty()) null else UnpaidStudent(student, open, Money.sum(open.map { it.remaining }))
            }.sortedByDescending { it.remaining }

            val allDues = monthDues.flatMap { it.second }
            return MonthlyReport(
                month = month,
                collectedCash = Money.sum(payments.map { it.cash }),
                collectedBank = Money.sum(payments.map { it.bank }),
                refunded = Money.sum(refunds.map { it.amount }),
                paymentCount = payments.size,
                billed = Money.sum(allDues.map { it.charge.amount }),
                billedRemaining = Money.sum(allDues.map { it.remaining }),
                discounts = Money.sum(allDues.map { it.charge.discount }),
                unpaid = unpaid
            )
        }

        /** شهر الـ charge: الـ period للرسوم الشهرية، وإلا شهر الـ dueDate. الـ charge بلا تاريخ لا يُنسب لشهر. */
        fun monthOf(due: DueSummary): YearMonth? =
            YearMonth.parse(due.charge.period) ?: SimpleDate.parse(due.charge.dueDate)?.yearMonth
    }
}
