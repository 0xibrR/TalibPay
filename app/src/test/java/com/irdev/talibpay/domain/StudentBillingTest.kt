package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Charge
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.model.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentBillingTest {

    private fun d(text: String) = SimpleDate.parse(text)!!

    @Test
    fun `student without a monthly fee is never billed`() {
        val student = Student(registrationDate = "05/09/2026", monthlyFee = 0.0)
        assertFalse(student.hasMonthlyBilling)
        assertTrue(student.periodsDue(d("05/12/2026")).isEmpty())
        assertNull(student.nextMonthlyDue(d("05/12/2026")))
    }

    @Test
    fun `student without a registration date is never billed`() {
        val student = Student(registrationDate = "", monthlyFee = 100.0)
        assertFalse(student.hasMonthlyBilling)
        assertTrue(student.periodsDue(d("05/12/2026")).isEmpty())
    }

    @Test
    fun `new student is billed from the registration date`() {
        assertEquals("05/09/2026", billingStartFor(null, 100.0, "05/09/2026", d("20/11/2026")))
    }

    @Test
    fun `enabling the fee on an existing student starts billing today, not retroactively`() {
        val existing = Student(registrationDate = "05/05/2026", monthlyFee = 0.0)
        val start = billingStartFor(existing, 100.0, "05/05/2026", d("20/11/2026"))
        assertEquals("20/11/2026", start)

        val enabled = existing.copy(monthlyFee = 100.0, billingStart = start)
        // أول رسوم في 05/12 وليس منذ أيار
        assertEquals(listOf("05/12/2026"), enabled.periodsDue(d("06/12/2026")).map { it.dueDate.format() })
    }

    @Test
    fun `editing an already billed student keeps its billing start`() {
        val existing = Student(registrationDate = "05/09/2026", monthlyFee = 100.0, billingStart = "05/09/2026")
        assertEquals("05/09/2026", billingStartFor(existing, 150.0, "20/09/2026", d("20/11/2026")))
    }

    @Test
    fun `student without billing start falls back to the registration date`() {
        val student = Student(registrationDate = "05/09/2026", monthlyFee = 100.0, lastBilledPeriod = "2026-10")
        assertEquals(listOf("05/11/2026"), student.periodsDue(d("06/11/2026")).map { it.dueDate.format() })
        assertEquals("05/12/2026", student.nextMonthlyDue(d("06/11/2026"))!!.format())
    }

    @Test
    fun `auto dues sit alongside manual dues without changing them`() {
        // مستحق يدوي مع دفعة جزئية، ثم أُضيف مستحق شهري تلقائي
        val manual = Charge(id = "c1", title = "Registration", amount = 50.0, createdAt = 1)
        val auto = Charge(id = "auto-2026-11", amount = 100.0, period = "2026-11", createdAt = 2)
        val payments = listOf(Payment(id = "p1", chargeId = "c1", cash = 20.0))

        val before = StudentLedger.build(listOf(manual), payments)
        val after = StudentLedger.build(listOf(manual, auto), payments)

        assertEquals(before.due("c1"), after.due("c1"))
        assertEquals(DueStatus.UNPAID, after.due("auto-2026-11")!!.status)
        assertEquals(130.0, after.totals.remaining, 0.0)
    }
}
