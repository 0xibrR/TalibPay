package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Student
import org.junit.Assert.assertEquals
import org.junit.Test

class NextBillingTest {

    private fun student(
        registration: String,
        fee: Double = 100.0,
        billingStart: String = registration,
        lastBilled: String = "",
        archived: Boolean = false
    ) = Student(
        registrationDate = registration,
        monthlyFee = fee,
        billingStart = billingStart,
        lastBilledPeriod = lastBilled,
        archived = archived
    )

    /** كل أيام السنة من 2026-01-01 إلى 2027-02-28. */
    private val days: List<SimpleDate> = buildList {
        for (year in 2026..2027) for (month in 1..12) {
            if (year == 2027 && month > 2) break
            for (day in 1..daysInMonth(year, month)) add(SimpleDate(year, month, day))
        }
    }

    /**
     * الاستعلام يعتمد على هذا التكافؤ: الطالب يُجلب فقط إن كان مفتاحه ≤ اليوم،
     * ويجب أن يحدث ذلك تمامًا عندما يكون عليه رسوم يجب إنشاؤها اليوم — لا أقل (رسوم فائتة) ولا أكثر.
     */
    private fun assertMatchesPeriodsDue(student: Student) {
        val key = student.nextBillingKey()
        days.forEach { today ->
            val dueNow = student.periodsDue(today).isNotEmpty()
            val fetched = key.isNotEmpty() && key <= today.sortKey()
            assertEquals("$student on ${today.format()}", dueNow, fetched)
        }
    }

    @Test
    fun `query condition matches dues to create, for every day`() {
        listOf(
            student("15/03/2026"),
            student("31/01/2026"), // يوم غير موجود في بعض الأشهر
            student("29/01/2026"), // 29 لا يوجد في فبراير 2026
            student("10/01/2026", billingStart = "20/06/2026"), // تفعيل الرسوم لاحقًا
            student("05/02/2026", lastBilled = "2026-07"), // أُنشئت حتى يوليو
            student("05/02/2026", lastBilled = "2027-01"),
            student("01/12/2026"), // التسجيل في المستقبل
            student("15/03/2026", fee = 0.0),
            student("15/03/2026", archived = true)
        ).forEach(::assertMatchesPeriodsDue)
    }

    @Test
    fun `key is the first unbilled due date`() {
        assertEquals("2026-08-05", student("05/02/2026", lastBilled = "2026-07").nextBillingKey())
        // تفعيل الرسوم في 20/06 ويوم الاستحقاق 10: أول موعد 10/07
        assertEquals("2026-07-10", student("10/01/2026", billingStart = "20/06/2026").nextBillingKey())
        // يوم 31 في فبراير يصبح آخر يوم في الشهر
        assertEquals("2026-02-28", student("31/01/2026", lastBilled = "2026-01").nextBillingKey())
    }

    @Test
    fun `no key without monthly billing`() {
        assertEquals("", student("15/03/2026", fee = 0.0).nextBillingKey())
        assertEquals("", student("15/03/2026", archived = true).nextBillingKey())
        assertEquals("", student("").nextBillingKey())
    }

    @Test
    fun `sort key orders like dates`() {
        assertEquals("2026-03-05", SimpleDate(2026, 3, 5).sortKey())
        val dates = listOf(SimpleDate(2026, 12, 1), SimpleDate(2026, 2, 28), SimpleDate(2027, 1, 1))
        assertEquals(dates.sorted().map { it.sortKey() }, dates.map { it.sortKey() }.sorted())
    }
}
