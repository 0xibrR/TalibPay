package com.irdev.talibpay.domain

import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.model.Student
import com.irdev.talibpay.viewmodel.archivedStudents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveTest {

    private val today = SimpleDate(2026, 11, 3)

    private val billed = Student(
        id = "s1",
        monthlyFee = 100.0,
        registrationDate = "01/07/2026",
        billingStart = "01/07/2026",
        lastBilledPeriod = "2026-08"
    )

    @Test
    fun `an archived student gets no new monthly dues`() {
        // غير مؤرشف: سبتمبر وأكتوبر ونوفمبر مستحقة
        assertEquals(listOf("2026-09", "2026-10", "2026-11"), billed.periodsDue(today).map { it.month.key })
        val archived = billed.copy(archived = true)
        assertTrue(archived.periodsDue(today).isEmpty())
        assertNull(archived.nextMonthlyDue(today))
    }

    @Test
    fun `restoring resumes monthly dues from today, skipping archived months`() {
        val restored = billed.copy(billingStart = billingStartOnUnarchive(billed.copy(archived = true), today))
        assertEquals("03/11/2026", restored.billingStart)
        // سبتمبر وأكتوبر ونوفمبر (موعده 1/11) كانت أثناء الأرشفة فلا تُحتسب؛ التالي ديسمبر
        assertTrue(restored.periodsDue(today).isEmpty())
        assertEquals(SimpleDate(2026, 12, 1), restored.nextMonthlyDue(today))
    }

    @Test
    fun `restoring on the due day still charges that day`() {
        val dueToday = SimpleDate(2026, 11, 1)
        val restored = billed.copy(billingStart = billingStartOnUnarchive(billed, dueToday))
        assertEquals(listOf("2026-11"), restored.periodsDue(dueToday).map { it.month.key })
    }

    @Test
    fun `restoring a student without a monthly fee keeps their billing start`() {
        val noFee = Student(id = "s2", billingStart = "01/01/2026")
        assertEquals("01/01/2026", billingStartOnUnarchive(noFee, today))
    }

    @Test
    fun `archive screen lists only archived students, most recently archived first`() {
        fun item(id: String, archived: Boolean, at: Long = 0) =
            StudentWithPayments(Student(id = id, archived = archived, archivedAt = at), StudentLedger.build(emptyList(), emptyList()))
        val list = listOf(item("active", false), item("old", true, 100), item("recent", true, 200))
        assertEquals(listOf("recent", "old"), archivedStudents(list).map { it.student.id })
    }
}
