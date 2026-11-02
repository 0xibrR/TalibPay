package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Student
import java.util.Calendar

/** تاريخ اليوم حسب ساعة الجهاز ومنطقته الزمنية. */
fun todaySimpleDate(nowMillis: Long = System.currentTimeMillis()): SimpleDate {
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    return SimpleDate(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
}

/** الطالب له رسوم شهرية تلقائية: قيمة أكبر من صفر وتاريخ تسجيل صالح. */
val Student.hasMonthlyBilling: Boolean
    get() = monthlyFee > 0 && SimpleDate.parse(registrationDate) != null

/** الأشهر التي حان موعد رسومها ولم تُنشأ بعد. */
fun Student.periodsDue(today: SimpleDate): List<BillingPeriod> {
    if (monthlyFee <= 0) return emptyList()
    val registration = SimpleDate.parse(registrationDate) ?: return emptyList()
    val start = SimpleDate.parse(billingStart) ?: registration
    return MonthlyBilling.periodsDue(registration, start, YearMonth.parse(lastBilledPeriod), today)
}

/** موعد الاستحقاق القادم، أو null إن لم تكن للطالب رسوم شهرية. */
fun Student.nextMonthlyDue(today: SimpleDate): SimpleDate? {
    if (monthlyFee <= 0) return null
    val registration = SimpleDate.parse(registrationDate) ?: return null
    val start = SimpleDate.parse(billingStart) ?: registration
    return MonthlyBilling.nextDueDate(registration, start, YearMonth.parse(lastBilledPeriod), today)
}

/**
 * متى تبدأ الفوترة عند حفظ الطالب:
 * - طالب جديد: من تاريخ تسجيله (رسومه مستحقة منذ التسجيل).
 * - تفعيل الرسوم لطالب موجود: من اليوم، حتى لا تُكرَّر أشهر سُجّلت رسومها يدويًا سابقًا.
 * - تعديل طالب مفعّلة رسومه: يبقى كما هو، فتغيير القيمة أو تاريخ التسجيل يسري على القادم فقط.
 */
fun billingStartFor(existing: Student?, newMonthlyFee: Double, registrationDate: String, today: SimpleDate): String =
    when {
        newMonthlyFee <= 0 -> existing?.billingStart.orEmpty()
        existing == null -> registrationDate
        existing.monthlyFee <= 0 || existing.billingStart.isBlank() -> today.format()
        else -> existing.billingStart
    }
