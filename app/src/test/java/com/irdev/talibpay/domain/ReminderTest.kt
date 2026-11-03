package com.irdev.talibpay.domain

import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.model.Charge
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.model.Student
import com.irdev.talibpay.model.WalletEntry
import com.irdev.talibpay.viewmodel.overdueStudents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTest {

    private val today = SimpleDate(2026, 11, 3)

    private fun charge(id: String, amount: Double, dueDate: String = "", createdAt: Long = 0) =
        Charge(id = id, title = id, amount = amount, dueDate = dueDate, createdAt = createdAt)

    private fun payment(id: String, chargeId: String, cash: Double) =
        Payment(id = id, chargeId = chargeId, cash = cash, total = cash)

    @Test
    fun `local numbers get the country code instead of the leading zero`() {
        assertEquals("970591234567", PhoneNumbers.toInternational("0591234567", "970"))
        assertEquals("972541234567", PhoneNumbers.toInternational("054-123 4567", "972"))
    }

    @Test
    fun `international numbers are kept as they are`() {
        assertEquals("970591234567", PhoneNumbers.toInternational("+970 59 123 4567", "972"))
        assertEquals("970591234567", PhoneNumbers.toInternational("00970591234567", "972"))
        assertEquals("970591234567", PhoneNumbers.toInternational("970591234567", "972"))
    }

    @Test
    fun `invalid numbers are rejected`() {
        assertNull(PhoneNumbers.toInternational("", "970"))
        assertNull(PhoneNumbers.toInternational("12345", "970"))
        assertNull(PhoneNumbers.toInternational("1234567890123456", "970"))
    }

    @Test
    fun `country code must be 1 to 3 digits without a leading zero`() {
        assertTrue(PhoneNumbers.isValidCountryCode("970"))
        assertTrue(PhoneNumbers.isValidCountryCode("1"))
        assertFalse(PhoneNumbers.isValidCountryCode(""))
        assertFalse(PhoneNumbers.isValidCountryCode("0970"))
        assertFalse(PhoneNumbers.isValidCountryCode("097"))
        assertFalse(PhoneNumbers.isValidCountryCode("9a"))
    }

    @Test
    fun `only unpaid dues that are due today or earlier are overdue`() {
        val ledger = StudentLedger.build(
            listOf(
                charge("past", 100.0, "01/10/2026", createdAt = 1),
                charge("today", 100.0, "03/11/2026", createdAt = 2),
                charge("future", 100.0, "01/12/2026", createdAt = 3),
                charge("noDate", 50.0, createdAt = 4),
                charge("paid", 80.0, "01/09/2026", createdAt = 5)
            ),
            listOf(payment("p1", "past", 40.0), payment("p2", "paid", 80.0))
        )
        val overdue = ledger.overdueDues(today)
        assertEquals(listOf("past", "today", "noDate"), overdue.map { it.charge.id })
        // 60 متبقٍ من الأول + 100 + 50
        assertEquals(210.0, overdue.overdueTotal(), 0.001)
    }

    @Test
    fun `due covered by the wallet is not overdue`() {
        val ledger = StudentLedger.build(
            listOf(charge("c1", 100.0, "01/10/2026")),
            emptyList(),
            listOf(WalletEntry(id = "use-c1", type = WalletEntryType.USE.name, amount = 100.0, chargeId = "c1"))
        )
        assertTrue(ledger.overdueDues(today).isEmpty())
    }

    @Test
    fun `overdue students are sorted by the largest overdue amount`() {
        fun student(id: String, vararg charges: Charge) = StudentWithPayments(
            Student(id = id, name = id),
            StudentLedger.build(charges.toList(), emptyList())
        )
        val result = overdueStudents(
            listOf(
                student("small", charge("a", 50.0, "01/10/2026")),
                student("none", charge("b", 70.0, "01/12/2026")),
                student("big", charge("c", 200.0, "01/10/2026"))
            ),
            today
        )
        assertEquals(listOf("big", "small"), result.map { it.data.student.id })
        assertEquals(200.0, result.first().total, 0.001)
    }
}
