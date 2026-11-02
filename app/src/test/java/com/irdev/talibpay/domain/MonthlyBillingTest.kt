package com.irdev.talibpay.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyBillingTest {

    private fun d(text: String) = SimpleDate.parse(text)!!
    private fun dates(periods: List<BillingPeriod>) = periods.map { it.dueDate.format() }

    @Test
    fun `due date repeats on the registration day each month`() {
        val periods = MonthlyBilling.periodsDue(
            registration = d("05/09/2026"), billingStart = d("05/09/2026"),
            lastBilled = null, today = d("20/11/2026")
        )
        assertEquals(listOf("05/09/2026", "05/10/2026", "05/11/2026"), dates(periods))
        assertEquals(listOf("auto-2026-09", "auto-2026-10", "auto-2026-11"), periods.map { it.chargeId })
    }

    @Test
    fun `missing day uses the last day of the month then returns to the original day`() {
        val periods = MonthlyBilling.periodsDue(
            registration = d("31/01/2027"), billingStart = d("31/01/2027"),
            lastBilled = null, today = d("31/05/2027")
        )
        assertEquals(
            listOf("31/01/2027", "28/02/2027", "31/03/2027", "30/04/2027", "31/05/2027"),
            dates(periods)
        )
    }

    @Test
    fun `leap year february uses the 29th`() {
        val due = MonthlyBilling.dueDateFor(YearMonth(2028, 2), anchorDay = 30)
        assertEquals("29/02/2028", due.format())
    }

    @Test
    fun `nothing is created before the due day arrives`() {
        val periods = MonthlyBilling.periodsDue(
            registration = d("15/09/2026"), billingStart = d("15/09/2026"),
            lastBilled = YearMonth(2026, 10), today = d("14/11/2026")
        )
        assertTrue(periods.isEmpty())
    }

    @Test
    fun `already billed months are never created again`() {
        val periods = MonthlyBilling.periodsDue(
            registration = d("05/09/2026"), billingStart = d("05/09/2026"),
            lastBilled = YearMonth(2026, 11), today = d("20/11/2026")
        )
        assertTrue(periods.isEmpty())
    }

    @Test
    fun `new dues are created even when earlier ones are unpaid (catch-up of missed months)`() {
        // آخر رسوم أُنشئت لشهر 9 والتطبيق لم يُفتح منذ ذلك: تُنشأ 10 و11 و12
        val periods = MonthlyBilling.periodsDue(
            registration = d("10/09/2026"), billingStart = d("10/09/2026"),
            lastBilled = YearMonth(2026, 9), today = d("10/12/2026")
        )
        assertEquals(listOf("10/10/2026", "10/11/2026", "10/12/2026"), dates(periods))
    }

    @Test
    fun `changing the registration day only affects future months`() {
        // سُجّلت رسوم حتى شهر 11 على يوم 5؛ تغيّر يوم التسجيل إلى 20
        val periods = MonthlyBilling.periodsDue(
            registration = d("20/09/2026"), billingStart = d("05/09/2026"),
            lastBilled = YearMonth(2026, 11), today = d("25/01/2027")
        )
        assertEquals(listOf("20/12/2026", "20/01/2027"), dates(periods))
    }

    @Test
    fun `moving the registration date forward does not bill before it`() {
        val periods = MonthlyBilling.periodsDue(
            registration = d("10/01/2027"), billingStart = d("05/09/2026"),
            lastBilled = YearMonth(2026, 10), today = d("15/02/2027")
        )
        assertEquals(listOf("10/01/2027", "10/02/2027"), dates(periods))
    }

    @Test
    fun `billing start skips months before the fee was enabled`() {
        // طالب مسجل منذ أيار، فُعّلت الرسوم الشهرية يوم 20/11: أول استحقاق 05/12
        val periods = MonthlyBilling.periodsDue(
            registration = d("05/05/2026"), billingStart = d("20/11/2026"),
            lastBilled = null, today = d("06/12/2026")
        )
        assertEquals(listOf("05/12/2026"), dates(periods))
    }

    @Test
    fun `next due date is the first one after today`() {
        val next = MonthlyBilling.nextDueDate(
            registration = d("31/01/2027"), billingStart = d("31/01/2027"),
            lastBilled = YearMonth(2027, 1), today = d("10/02/2027")
        )
        assertEquals("28/02/2027", next.format())
    }

    @Test
    fun `date parsing rejects invalid dates`() {
        assertNull(SimpleDate.parse("31/02/2026"))
        assertNull(SimpleDate.parse("2026-01-01"))
        assertNull(SimpleDate.parse(""))
        assertEquals(SimpleDate(2026, 2, 28), SimpleDate.parse("28/02/2026"))
    }
}
