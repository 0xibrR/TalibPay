package com.irdev.talibpay.viewmodel

import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.domain.SimpleDate
import com.irdev.talibpay.domain.StudentLedger
import com.irdev.talibpay.model.Charge
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.model.Student
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StudentListOptionsTest {

    private val today = SimpleDate(2026, 11, 3)

    /** طالب بمستحق واحد، ودفعة اختيارية عليه؛ المجاميع كما يحسبها الدفتر. */
    private fun student(
        name: String,
        amount: Double = 0.0,
        paid: Double = 0.0,
        dueDate: String = "01/10/2026"
    ): StudentWithPayments {
        val charges = if (amount > 0) listOf(Charge(id = "c-$name", amount = amount, dueDate = dueDate)) else emptyList()
        val payments = if (paid > 0) listOf(Payment(id = "p-$name", chargeId = "c-$name", cash = paid)) else emptyList()
        val ledger = StudentLedger.build(charges, payments)
        val totals = ledger.totals
        return StudentWithPayments(
            Student(id = name, name = name, paid = totals.paid, total = totals.total, remaining = totals.remaining),
            ledger
        )
    }

    private val unpaid = student("unpaid", amount = 100.0)
    private val partial = student("partial", amount = 100.0, paid = 40.0)
    private val paid = student("paid", amount = 100.0, paid = 100.0)
    private val futureDue = student("future", amount = 100.0, dueDate = "01/12/2026")
    private val noFees = student("none")
    private val all = listOf(unpaid, partial, paid, futureDue, noFees)

    private fun names(filter: StudentFilter) = all.filter { filter.matches(it, today) }.map { it.student.name }

    @Test
    fun `filters by payment status`() {
        assertEquals(listOf("unpaid", "partial", "paid", "future", "none"), names(StudentFilter.ALL))
        // المستحق المستقبلي ليس متأخرًا بعد
        assertEquals(listOf("unpaid", "partial"), names(StudentFilter.OVERDUE))
        assertEquals(listOf("partial"), names(StudentFilter.PARTIAL))
        // طالب بلا رسوم ليس "مدفوعًا بالكامل"
        assertEquals(listOf("paid"), names(StudentFilter.PAID))
    }

    private fun sorted(sort: StudentSort, list: List<StudentWithPayments>) =
        sort.apply(list, today, Locale.ENGLISH).map { it.student.name }

    @Test
    fun `default order is the order students were added`() {
        assertEquals(all.map { it.student.name }, sorted(StudentSort.ADDED_OLDEST, all))
        assertEquals(all.map { it.student.name }.reversed(), sorted(StudentSort.ADDED_NEWEST, all))
    }

    @Test
    fun `sorts by name ignoring case`() {
        val list = listOf(student("bob"), student("Alice"), student("carl"))
        assertEquals(listOf("Alice", "bob", "carl"), sorted(StudentSort.NAME, list))
    }

    @Test
    fun `sorts Arabic names alphabetically`() {
        val list = listOf(student("سامي"), student("أحمد"), student("بلال"))
        assertEquals(listOf("أحمد", "بلال", "سامي"), StudentSort.NAME.apply(list, today, Locale("ar")).map { it.student.name })
    }

    @Test
    fun `sorts by highest remaining, keeping the added order for ties`() {
        val list = listOf(student("a", 50.0), student("b", 200.0), student("c", 50.0), noFees)
        assertEquals(listOf("b", "a", "c", "none"), sorted(StudentSort.REMAINING_HIGHEST, list))
    }

    @Test
    fun `sorts by oldest overdue, students without overdue last`() {
        val list = listOf(
            student("recent", 100.0, dueDate = "01/11/2026"),
            futureDue,
            student("oldest", 100.0, dueDate = "01/08/2026"),
            student("undated", 100.0, dueDate = ""),
            paid
        )
        assertEquals(listOf("undated", "oldest", "recent", "future", "paid"), sorted(StudentSort.OVERDUE_OLDEST, list))
    }

    @Test
    fun `unknown saved sort falls back to the default`() {
        assertEquals(StudentSort.NAME, StudentSort.fromName("NAME"))
        assertEquals(StudentSort.DEFAULT, StudentSort.fromName("OLD_VALUE"))
        assertEquals(StudentSort.DEFAULT, StudentSort.fromName(null))
    }
}
