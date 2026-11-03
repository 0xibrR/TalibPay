package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Charge
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.model.Student
import com.irdev.talibpay.model.WalletEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MonthlyReportTest {

    private val november = YearMonth(2026, 11)

    private fun monthly(id: String, month: String, amount: Double, createdAt: Long = 0) =
        Charge(id = id, amount = amount, period = month, dueDate = "01/${month.takeLast(2)}/${month.take(4)}", createdAt = createdAt)

    private fun payment(id: String, chargeId: String, date: String, cash: Double = 0.0, bank: Double = 0.0) =
        Payment(id = id, chargeId = chargeId, cash = cash, bank = bank, paymentDate = date)

    private fun millis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, 12, 0) }.timeInMillis

    private fun entry(name: String, charges: List<Charge>, payments: List<Payment> = emptyList(), wallet: List<WalletEntry> = emptyList()) =
        Student(id = name, name = name) to StudentLedger.build(charges, payments, wallet)

    @Test
    fun `collected counts money received in the month, by payment date`() {
        val report = MonthlyReport.build(
            listOf(
                entry(
                    "a",
                    listOf(monthly("oct", "2026-10", 100.0, 1), monthly("nov", "2026-11", 100.0, 2)),
                    listOf(
                        payment("p1", "oct", "15/10/2026", cash = 100.0), // أكتوبر: لا يُحسب
                        payment("p2", "nov", "02/11/2026", cash = 60.0),
                        payment("p3", "nov", "20/11/2026", bank = 40.0)
                    )
                )
            ),
            november
        )
        assertEquals(60.0, report.collectedCash, 0.001)
        assertEquals(40.0, report.collectedBank, 0.001)
        assertEquals(100.0, report.collected, 0.001)
        assertEquals(2, report.paymentCount)
    }

    @Test
    fun `a late payment for an old due counts in the month it was received`() {
        // دُفع في نوفمبر عن رسوم أكتوبر: محصّل نوفمبر، ولا يمس مستحقات نوفمبر
        val report = MonthlyReport.build(
            listOf(
                entry(
                    "a",
                    listOf(monthly("oct", "2026-10", 100.0, 1), monthly("nov", "2026-11", 100.0, 2)),
                    listOf(payment("p1", "oct", "05/11/2026", cash = 100.0))
                )
            ),
            november
        )
        assertEquals(100.0, report.collected, 0.001)
        assertEquals(100.0, report.billed, 0.001)
        assertEquals(0.0, report.billedPaid, 0.001)
        assertEquals(100.0, report.billedRemaining, 0.001)
    }

    @Test
    fun `month dues show what was paid and who still owes, largest first`() {
        val report = MonthlyReport.build(
            listOf(
                entry("paid", listOf(monthly("c1", "2026-11", 100.0)), listOf(payment("p1", "c1", "01/11/2026", cash = 100.0))),
                entry("partial", listOf(monthly("c2", "2026-11", 100.0)), listOf(payment("p2", "c2", "01/11/2026", cash = 30.0))),
                entry("unpaid", listOf(monthly("c3", "2026-11", 100.0))),
                entry("otherMonth", listOf(monthly("c4", "2026-12", 100.0)))
            ),
            november
        )
        assertEquals(300.0, report.billed, 0.001)
        assertEquals(130.0, report.billedPaid, 0.001)
        assertEquals(170.0, report.billedRemaining, 0.001)
        assertEquals(listOf("unpaid", "partial"), report.unpaid.map { it.student.name })
        assertEquals(70.0, report.unpaid[1].remaining, 0.001)
    }

    @Test
    fun `manual dues belong to the month of their due date, undated ones to none`() {
        val report = MonthlyReport.build(
            listOf(
                entry(
                    "a",
                    listOf(
                        Charge(id = "books", amount = 50.0, dueDate = "10/11/2026", createdAt = 1),
                        Charge(id = "undated", amount = 80.0, createdAt = 2)
                    )
                )
            ),
            november
        )
        assertEquals(50.0, report.billed, 0.001)
        assertEquals(listOf("books"), report.unpaid.single().dues.map { it.charge.id })
    }

    @Test
    fun `wallet use counts as paid, and refunds reduce what was collected`() {
        val report = MonthlyReport.build(
            listOf(
                entry(
                    "a",
                    listOf(monthly("nov", "2026-11", 100.0)),
                    wallet = listOf(
                        WalletEntry(id = "dep", type = WalletEntryType.DEPOSIT.name, amount = 150.0, createdAt = millis(2026, 10, 1)),
                        WalletEntry(id = "use-nov", type = WalletEntryType.USE.name, amount = 100.0, chargeId = "nov", createdAt = millis(2026, 11, 1)),
                        WalletEntry(id = "ref", type = WalletEntryType.REFUND.name, amount = 20.0, createdAt = millis(2026, 11, 15))
                    )
                )
            ),
            november
        )
        assertEquals(100.0, report.billedPaid, 0.001)
        assertTrue(report.unpaid.isEmpty())
        assertEquals(20.0, report.refunded, 0.001)
        assertEquals(-20.0, report.collected, 0.001)
    }

    @Test
    fun `empty month has zeros`() {
        val report = MonthlyReport.build(emptyList(), november)
        assertEquals(0.0, report.collected, 0.001)
        assertEquals(0.0, report.billed, 0.001)
        assertEquals(0, report.paymentCount)
        assertTrue(report.unpaid.isEmpty())
    }
}
