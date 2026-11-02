package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Charge
import com.irdev.talibpay.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentLedgerTest {

    private val september = Charge(id = "c1", title = "September", amount = 100.0, createdAt = 1)
    private val october = Charge(id = "c2", title = "October", amount = 100.0, createdAt = 2)

    @Test
    fun `partial payment then a second payment later settles the same due`() {
        // دفع 50 من 100، وبعد أسبوع دفع 50 أخرى لنفس المستحق
        val first = StudentLedger.build(
            listOf(september),
            listOf(Payment(id = "p1", chargeId = "c1", cash = 50.0))
        )
        val due1 = first.due("c1")!!
        assertEquals(DueStatus.PARTIAL, due1.status)
        assertEquals(50.0, due1.remaining, 0.0)

        val later = StudentLedger.build(
            listOf(september),
            listOf(
                Payment(id = "p1", chargeId = "c1", cash = 50.0),
                Payment(id = "p2", chargeId = "c1", bank = 50.0)
            )
        )
        val due2 = later.due("c1")!!
        assertEquals(DueStatus.PAID, due2.status)
        assertEquals(0.0, due2.remaining, 0.0)
        // كل دفعة تعرض ما بقي من المستحق بعدها
        assertEquals(listOf(50.0, 0.0), due2.payments.map { it.remaining })
    }

    @Test
    fun `payments only count toward their own due`() {
        val ledger = StudentLedger.build(
            listOf(september, october),
            listOf(
                Payment(id = "p1", chargeId = "c1", cash = 100.0),
                Payment(id = "p2", chargeId = "c2", cash = 30.0)
            )
        )
        assertEquals(DueStatus.PAID, ledger.due("c1")!!.status)
        assertEquals(70.0, ledger.due("c2")!!.remaining, 0.0)
        assertEquals(listOf("c2"), ledger.openDues.map { it.charge.id })
    }

    @Test
    fun `student totals add up all dues and payments`() {
        val totals = StudentLedger.build(
            listOf(september, october),
            listOf(
                Payment(id = "p1", chargeId = "c1", cash = 60.0, bank = 40.0),
                Payment(id = "p2", chargeId = "c2", cash = 30.0)
            )
        ).totals
        assertEquals(90.0, totals.paidCash, 0.0)
        assertEquals(40.0, totals.paidBank, 0.0)
        assertEquals(130.0, totals.paid, 0.0)
        assertEquals(200.0, totals.total, 0.0)
        assertEquals(70.0, totals.remaining, 0.0)
    }

    @Test
    fun `due with no payments is unpaid`() {
        val due = StudentLedger.build(listOf(october), emptyList()).due("c2")!!
        assertEquals(DueStatus.UNPAID, due.status)
        assertEquals(100.0, due.remaining, 0.0)
    }

    @Test
    fun `old payments without a due are grouped into a legacy fees due`() {
        val ledger = StudentLedger.build(
            emptyList(),
            listOf(
                Payment(id = "p1", cash = 300.0, total = 1000.0),
                Payment(id = "p2", bank = 300.0, total = 1000.0)
            )
        )
        val legacy = ledger.dues.single()
        assertTrue(legacy.isLegacy)
        assertEquals(1000.0, legacy.charge.amount, 0.0)
        assertEquals(400.0, legacy.remaining, 0.0)
        assertEquals(listOf(700.0, 400.0), legacy.payments.map { it.remaining })
    }

    @Test
    fun `payment method is derived from the amounts`() {
        assertEquals(PaymentMethod.CASH, methodFor(50.0, 0.0))
        assertEquals(PaymentMethod.BANK, methodFor(0.0, 50.0))
        assertEquals(PaymentMethod.MIXED, methodFor(20.0, 30.0))
    }

    @Test
    fun `payment cannot exceed what is left on the due`() {
        // المستحق 100 دُفع منه 50: أقصى دفعة 50
        assertEquals(
            PaymentValidation.ExceedsDue(50.0),
            validatePayment("60", "0", dueAmount = 100.0, paidByOtherPayments = 50.0)
        )
        assertEquals(
            PaymentValidation.Valid(30.0, 20.0),
            validatePayment("30", "20", dueAmount = 100.0, paidByOtherPayments = 50.0)
        )
    }

    @Test
    fun `payment needs a positive amount and valid numbers`() {
        assertEquals(PaymentValidation.EmptyPayment, validatePayment("0", "0", 100.0, 0.0))
        assertEquals(PaymentValidation.InvalidAmount, validatePayment("abc", "0", 100.0, 0.0))
        assertEquals(PaymentValidation.InvalidAmount, validatePayment("10", "-5", 100.0, 0.0))
    }

    @Test
    fun `due needs a title, a positive amount, and cannot drop below what was paid`() {
        assertEquals(ChargeValidation.MissingTitle, validateCharge(" ", "100"))
        assertEquals(ChargeValidation.InvalidAmount, validateCharge("September", "0"))
        assertEquals(ChargeValidation.BelowPaid(80.0), validateCharge("September", "50", alreadyPaid = 80.0))
        assertEquals(ChargeValidation.Valid("September", 120.0), validateCharge(" September ", "120", 80.0))
    }
}
