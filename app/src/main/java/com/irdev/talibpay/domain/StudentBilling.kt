package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Student
import java.util.Calendar

/** تاريخ اليوم حسب ساعة الجهاز والـ timezone. */
fun todaySimpleDate(nowMillis: Long = System.currentTimeMillis()): SimpleDate {
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    return SimpleDate(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
}

/** للطالب monthly fee تلقائية: قيمة أكبر من صفر وتاريخ تسجيل صالح. */
val Student.hasMonthlyBilling: Boolean
    get() = monthlyFee > 0 && SimpleDate.parse(registrationDate) != null

/** الأشهر التي حان موعدها ولم يُنشأ لها charge بعد. */
fun Student.periodsDue(today: SimpleDate): List<BillingPeriod> {
    if (archived) return emptyList()
    if (monthlyFee <= 0) return emptyList()
    val registration = SimpleDate.parse(registrationDate) ?: return emptyList()
    val start = SimpleDate.parse(billingStart) ?: registration
    return MonthlyBilling.periodsDue(registration, start, YearMonth.parse(lastBilledPeriod), today)
}

/** موعد الاستحقاق القادم، أو null إذا لا monthly fee للطالب. */
fun Student.nextMonthlyDue(today: SimpleDate): SimpleDate? {
    if (archived) return null
    if (monthlyFee <= 0) return null
    val registration = SimpleDate.parse(registrationDate) ?: return null
    val start = SimpleDate.parse(billingStart) ?: registration
    return MonthlyBilling.nextDueDate(registration, start, YearMonth.parse(lastBilledPeriod), today)
}

/**
 * قيمة billingStart عند حفظ الطالب:
 * - طالب جديد: من تاريخ تسجيله (رسومه مستحقة منذ التسجيل).
 * - تفعيل الـ monthly fee لطالب موجود: من اليوم، حتى لا تتكرر أشهر سُجّلت يدويًا سابقًا.
 * - تعديل طالب مفعّلة رسومه: تبقى كما هي، فتغيير القيمة أو تاريخ التسجيل يسري على القادم فقط.
 */
fun billingStartFor(
    existing: Student?,
    newMonthlyFee: Double,
    registrationDate: String,
    today: SimpleDate,
    backdate: Boolean = false
): String =
    when {
        newMonthlyFee <= 0 -> existing?.billingStart.orEmpty()
        existing == null -> registrationDate
        isEnablingMonthlyFee(existing, newMonthlyFee) -> if (backdate) registrationDate else today.format()
        else -> existing.billingStart
    }

/** تفعيل الـ monthly fee لطالب موجود (لم تكن له من قبل). */
fun isEnablingMonthlyFee(existing: Student?, newMonthlyFee: Double): Boolean =
    existing != null && newMonthlyFee > 0 &&
            (existing.monthlyFee <= 0 || existing.billingStart.isBlank())

/**
 * سؤال الـ backdate يظهر فقط عند إدخال الـ monthly fee لأول مرة لطالب موجود،
 * وفقط إذا وُجدت أشهر سابقة يمكن احتسابها (تاريخ التسجيل في الماضي).
 * تعديل مبلغ موجود لا يُظهر السؤال.
 */
fun shouldAskToBackdate(existing: Student?, newMonthlyFee: Double, registrationDate: String, today: SimpleDate): Boolean =
    existing != null && existing.monthlyFee <= 0 && newMonthlyFee > 0 &&
            pastDuesSinceRegistration(registrationDate, today).isNotEmpty()

/**
 * الأشهر السابقة (قبل اليوم) من تاريخ التسجيل، لعرض عددها في السؤال.
 * استحقاق اليوم نفسه لا يُعد سابقًا: يُنشأ في الحالتين.
 */
fun pastDuesSinceRegistration(registrationDate: String, today: SimpleDate): List<BillingPeriod> {
    val registration = SimpleDate.parse(registrationDate) ?: return emptyList()
    return MonthlyBilling.periodsDue(registration, registration, null, today).filter { it.dueDate < today }
}

/**
 * billingStart عند الـ unarchive: الرسوم تُستأنف من اليوم،
 * فلا تُحتسب الأشهر التي قضاها في الأرشيف.
 */
fun billingStartOnUnarchive(student: Student, today: SimpleDate): String =
    if (student.monthlyFee > 0) today.format() else student.billingStart

/**
 * nextBillingDate: موعد الـ monthly charge القادم كـ key بصيغة yyyy-MM-dd يُخزَّن مع الطالب،
 * لنعمل query على المستحقين فقط بدل تحميل كل الطلاب. فارغ = لا رسوم تلقائية (بلا رسوم، أو مؤرشف).
 */
fun Student.nextBillingKey(): String {
    if (archived || monthlyFee <= 0) return ""
    val registration = SimpleDate.parse(registrationDate) ?: return ""
    val start = SimpleDate.parse(billingStart) ?: registration
    return MonthlyBilling.firstUnbilledDue(registration, start, YearMonth.parse(lastBilledPeriod)).sortKey()
}
