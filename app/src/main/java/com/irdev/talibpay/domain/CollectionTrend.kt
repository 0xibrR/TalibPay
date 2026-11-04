package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Student

/** المحصّل (collected) والمستحقات (billed) لشهر واحد في الـ chart. */
data class TrendPoint(val month: YearMonth, val collected: Double, val billed: Double)

/**
 * بيانات chart التحصيل: آخر عدة أشهر، الأقدم أولًا.
 * الـ window تنتهي بالشهر الحالي ما دام الشهر المختار ضمنها (فلا تتحرك الأعمدة عند الضغط على شهر)،
 * وإذا اختير شهر أقدم تنتهي به.
 */
object CollectionTrend {

    const val MONTHS = 6

    fun window(selected: YearMonth, current: YearMonth, months: Int = MONTHS): List<YearMonth> {
        val recent = lastMonths(current, months)
        return if (selected in recent) recent else lastMonths(selected, months)
    }

    fun build(students: List<Pair<Student, StudentLedger>>, months: List<YearMonth>): List<TrendPoint> =
        months.map { month ->
            val report = MonthlyReport.build(students, month)
            TrendPoint(month, report.collected, report.billed)
        }

    private fun lastMonths(end: YearMonth, count: Int): List<YearMonth> =
        generateSequence(end) { it.previous() }.take(count).toList().asReversed()
}
