package com.irdev.talibpay.domain

import java.util.Locale

/** تاريخ يوم بسيط (بدون وقت ولا منطقة زمنية) بصيغة التخزين dd/MM/yyyy. */
data class SimpleDate(val year: Int, val month: Int, val day: Int) : Comparable<SimpleDate> {

    val yearMonth: YearMonth get() = YearMonth(year, month)

    override fun compareTo(other: SimpleDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    fun format(): String = String.format(Locale.US, "%02d/%02d/%04d", day, month, year)

    companion object {
        fun parse(text: String?): SimpleDate? {
            val parts = text?.trim()?.split("/") ?: return null
            if (parts.size != 3) return null
            val day = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val year = parts[2].toIntOrNull() ?: return null
            if (month !in 1..12 || year < 1900 || day !in 1..daysInMonth(year, month)) return null
            return SimpleDate(year, month, day)
        }
    }
}

/** شهر محدد؛ مفتاحه yyyy-MM يُستخدم في معرّف الرسوم ولمنع التكرار. */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {

    val key: String get() = String.format(Locale.US, "%04d-%02d", year, month)

    fun next(): YearMonth = if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)

    override fun compareTo(other: YearMonth): Int =
        compareValuesBy(this, other, { it.year }, { it.month })

    companion object {
        fun parse(key: String?): YearMonth? {
            val parts = key?.split("-") ?: return null
            if (parts.size != 2) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            return if (month in 1..12) YearMonth(year, month) else null
        }
    }
}

fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

/** رسوم شهر واحد: الشهر وتاريخ استحقاقه. */
data class BillingPeriod(val month: YearMonth, val dueDate: SimpleDate) {
    /** معرّف ثابت لكل شهر: يمنع إنشاء رسوم الشهر نفسه مرتين حتى من جهازين. */
    val chargeId: String get() = chargeIdFor(month)

    companion object {
        const val PREFIX = "auto-"
        fun chargeIdFor(month: YearMonth) = PREFIX + month.key
    }
}

/**
 * جدول الرسوم الشهرية لكل طالب:
 * - يوم الاستحقاق = يوم التسجيل من كل شهر، وإن لم يوجد في الشهر (مثل 31 في نيسان)
 *   يُستخدم آخر يوم فيه، ويعود اليوم الأصلي في الأشهر التالية (لأنه يُحسب دائمًا من يوم التسجيل).
 * - لا تُنشأ رسوم قبل تاريخ التسجيل ولا قبل بداية الفوترة [billingStart].
 * - لا تُنشأ رسوم لشهر سبق إنشاؤه أو تجاوزه ([lastBilled])، فتعديل تاريخ التسجيل
 *   يغيّر الاستحقاقات القادمة فقط، والرسوم المحذوفة لا تُعاد.
 */
object MonthlyBilling {

    /** حد أمان لعدد الأشهر المنشأة دفعة واحدة. */
    private const val MAX_PERIODS = 120

    fun dueDateFor(month: YearMonth, anchorDay: Int): SimpleDate =
        SimpleDate(month.year, month.month, minOf(anchorDay, daysInMonth(month.year, month.month)))

    /** الأشهر التي حان موعد رسومها حتى [today] ولم تُنشأ بعد، الأقدم أولًا. */
    fun periodsDue(
        registration: SimpleDate,
        billingStart: SimpleDate,
        lastBilled: YearMonth?,
        today: SimpleDate
    ): List<BillingPeriod> {
        val result = mutableListOf<BillingPeriod>()
        var month = startMonth(registration, billingStart, lastBilled)
        while (result.size < MAX_PERIODS) {
            val due = dueDateFor(month, registration.day)
            if (due > today) break
            if (due >= registration && due >= billingStart) result += BillingPeriod(month, due)
            month = month.next()
        }
        return result
    }

    /** أقرب تاريخ استحقاق قادم بعد [today] (للعرض في ملف الطالب). */
    fun nextDueDate(
        registration: SimpleDate,
        billingStart: SimpleDate,
        lastBilled: YearMonth?,
        today: SimpleDate
    ): SimpleDate {
        var month = startMonth(registration, billingStart, lastBilled)
        repeat(MAX_PERIODS) {
            val due = dueDateFor(month, registration.day)
            if (due > today && due >= registration && due >= billingStart) return due
            month = month.next()
        }
        return dueDateFor(month, registration.day)
    }

    private fun startMonth(registration: SimpleDate, billingStart: SimpleDate, lastBilled: YearMonth?): YearMonth =
        listOfNotNull(registration.yearMonth, billingStart.yearMonth, lastBilled?.next()).max()
}
