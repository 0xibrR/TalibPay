package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentTotalsTest {

    @Test
    fun `no payments gives zero totals`() {
        assertEquals(StudentTotals(0.0, 0.0, 0.0, 0.0, 0.0), StudentTotals.from(emptyList()))
    }

    @Test
    fun `totals are summed from all payments`() {
        val totals = StudentTotals.from(
            listOf(
                Payment(cash = 100.0, bank = 0.0, total = 500.0),
                Payment(cash = 50.0, bank = 150.0, total = 500.0)
            )
        )
        assertEquals(150.0, totals.paidCash, 0.0)
        assertEquals(150.0, totals.paidBank, 0.0)
        assertEquals(300.0, totals.paid, 0.0)
        assertEquals(500.0, totals.total, 0.0)
        assertEquals(200.0, totals.remaining, 0.0)
    }

    @Test
    fun `fees come from the latest payment so a discount can lower them`() {
        val totals = StudentTotals.from(
            listOf(Payment(cash = 100.0, total = 600.0), Payment(cash = 100.0, total = 400.0))
        )
        assertEquals(400.0, totals.total, 0.0)
        assertEquals(200.0, totals.remaining, 0.0)
    }

    @Test
    fun `remaining never goes negative`() {
        val totals = StudentTotals.from(
            listOf(Payment(cash = 300.0, total = 200.0), Payment(bank = 100.0, total = 200.0))
        )
        assertEquals(0.0, totals.remaining, 0.0)
    }

    @Test
    fun `each payment shows the balance left after it, not fees minus that payment alone`() {
        // رسوم 1000: دفعتان من 300 → المتبقي 700 ثم 400 (الطريقة القديمة كانت تعرض 700 مرتين)
        val payments = StudentTotals.withRunningBalance(
            listOf(
                Payment(id = "a", cash = 300.0, total = 1000.0),
                Payment(id = "b", bank = 300.0, total = 1000.0),
                Payment(id = "c", cash = 150.0, bank = 250.0, total = 1000.0)
            )
        )
        assertEquals(listOf(700.0, 400.0, 0.0), payments.map { it.remaining })
        assertEquals(listOf(300.0, 300.0, 400.0), payments.map { it.paid })
    }

    @Test
    fun `validatePayment accepts valid input`() {
        val result = validatePayment("100", "50", "200", PaymentMethod.MIXED)
        assertEquals(PaymentValidation.Valid(100.0, 50.0, 200.0), result)
    }

    @Test
    fun `validatePayment is cumulative across the student's other payments`() {
        // دُفع 700 من 1000 سابقًا: دفعة 400 ترفض، والحد الأقصى 300
        assertEquals(
            PaymentValidation.PaidExceedsTotal(300.0),
            validatePayment("400", "0", "1000", PaymentMethod.CASH, paidByOtherPayments = 700.0)
        )
        assertTrue(
            validatePayment("300", "0", "1000", PaymentMethod.CASH, paidByOtherPayments = 700.0)
                    is PaymentValidation.Valid
        )
    }

    @Test
    fun `validatePayment rejects a single payment above the fees`() {
        assertEquals(
            PaymentValidation.PaidExceedsTotal(200.0),
            validatePayment("150", "100", "200", PaymentMethod.MIXED)
        )
    }

    @Test
    fun `validatePayment requires fees above zero`() {
        assertEquals(PaymentValidation.MissingFees, validatePayment("0", "0", "0", PaymentMethod.UNPAID))
    }

    @Test
    fun `payment method must match the amounts`() {
        val mismatch = PaymentValidation.MethodMismatch
        assertEquals(mismatch, validatePayment("100", "50", "500", PaymentMethod.CASH))
        assertEquals(mismatch, validatePayment("100", "0", "500", PaymentMethod.BANK))
        assertEquals(mismatch, validatePayment("100", "0", "500", PaymentMethod.MIXED))
        assertEquals(mismatch, validatePayment("100", "0", "500", PaymentMethod.UNPAID))
        assertEquals(mismatch, validatePayment("0", "0", "500", PaymentMethod.CASH))
        assertTrue(validatePayment("0", "0", "500", PaymentMethod.UNPAID) is PaymentValidation.Valid)
        assertTrue(validatePayment("0", "80", "500", PaymentMethod.BANK) is PaymentValidation.Valid)
    }

    @Test
    fun `validatePayment rejects invalid amounts`() {
        assertTrue(validatePayment("abc", "0", "100", PaymentMethod.CASH) is PaymentValidation.InvalidAmount)
        assertTrue(validatePayment("10", "-1", "100", PaymentMethod.CASH) is PaymentValidation.InvalidAmount)
        assertTrue(validatePayment("10", "0", "", PaymentMethod.CASH) is PaymentValidation.InvalidAmount)
    }
}
